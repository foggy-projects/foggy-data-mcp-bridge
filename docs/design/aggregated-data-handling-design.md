# Aggregated Data Handling Design

## Overview

This document describes the design for handling large dataset queries in the MCP Bridge. When users query aggregated data (small volume), results are returned directly to the AI. For detailed data queries (large volume), we provide a link to a browser-based data viewer instead of wasting tokens.

## Problem Statement

| Query Type | Example | Data Volume | Recommended Handling |
|------------|---------|-------------|---------------------|
| Aggregated | "Top 10 customers by sales in 2025" | Small (≤100 rows) | Return to AI directly |
| Detailed | "All orders in 2025" | Large (1000+ rows) | Provide viewer link |
| Mixed | "Monthly sales breakdown" | Medium | Configurable threshold |

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              AI Client (Claude/Cursor)                       │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                    ┌───────────────────┴───────────────────┐
                    ▼                                       ▼
         ┌──────────────────────┐              ┌──────────────────────────┐
         │ dataset.query_model  │              │ dataset.open_in_viewer   │
         │   (aggregated data)  │              │   (large detailed data)  │
         └──────────────────────┘              └──────────────────────────┘
                    │                                       │
                    ▼                                       ▼
         Return data directly              Generate viewer link + cache query
                                                            │
                                                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         foggy-data-viewer (New Addon)                        │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                         Spring Boot Backend                             ││
│  │  ┌───────────────────┐  ┌──────────────────┐  ┌─────────────────────┐  ││
│  │  │ ViewerController  │  │ QueryCacheService│  │ MongoDB Repository  │  ││
│  │  │ (REST API)        │  │ (TTL managed)    │  │ (query storage)     │  ││
│  │  └───────────────────┘  └──────────────────┘  └─────────────────────┘  ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │          Vue.js + vxe-table Frontend (in resources/static)             ││
│  │  ┌───────────┐ ┌──────────────┐ ┌────────────┐ ┌──────────────────┐   ││
│  │  │ Filtering │ │ Aggregation  │ │ Pagination │ │   Sorting        │   ││
│  │  └───────────┘ └──────────────┘ └────────────┘ └──────────────────┘   ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
                    ┌───────────────────────────────────────┐
                    │  Existing Query Infrastructure        │
                    │  (QueryModelDataStoreController,      │
                    │   QueryFacade, SemanticService)       │
                    └───────────────────────────────────────┘
```

## Module Dependency Architecture

To avoid circular dependencies between `foggy-dataset-mcp` and addon modules like `foggy-data-viewer`, we extract common interfaces into an SPI (Service Provider Interface) module.

### Module Dependency Diagram

```
                    ┌─────────────────────────────────────┐
                    │         foggy-mcp-spi               │
                    │  (Interfaces only, ~5 files)        │
                    │  ┌───────────────────────────────┐  │
                    │  │ McpTool.java                  │  │
                    │  │ ToolCategory.java             │  │
                    │  │ DatasetAccessor.java          │  │
                    │  │ McpToolDescriptor.java        │  │
                    │  └───────────────────────────────┘  │
                    └─────────────────────────────────────┘
                           ▲                    ▲
                           │                    │
            ┌──────────────┘                    └──────────────┐
            │                                                  │
┌───────────────────────────────┐          ┌───────────────────────────────┐
│     foggy-dataset-mcp         │          │     foggy-data-viewer         │
│  (MCP Server implementation)  │          │  (Data viewer addon)          │
│                               │          │                               │
│  - McpToolDispatcher          │          │  - OpenInViewerTool           │
│  - AnalystMcpController       │          │  - ViewerApiController        │
│  - Built-in tools             │          │  - QueryCacheService          │
│                               │          │                               │
│  depends on:                  │          │  depends on:                  │
│  - foggy-mcp-spi              │          │  - foggy-mcp-spi              │
│  - foggy-dataset-model        │          │  - foggy-dataset-model        │
└───────────────────────────────┘          └───────────────────────────────┘
            │                                          │
            │                                          │
            └──────────────┬───────────────────────────┘
                           │
                           ▼
              ┌─────────────────────────────────────┐
              │       foggy-mcp-starter             │
              │  (Optional integration module)      │
              │                                     │
              │  depends on:                        │
              │  - foggy-dataset-mcp                │
              │  - foggy-data-viewer                │
              │  - (other future addons)            │
              │                                     │
              │  Single dependency for users who    │
              │  want all MCP features              │
              └─────────────────────────────────────┘
```

### New Module: foggy-mcp-spi

Location: `foggy-mcp-spi/`

```
foggy-mcp-spi/
├── pom.xml
└── src/main/java/com/foggyframework/mcp/spi/
    ├── McpTool.java              # Tool interface
    ├── ToolCategory.java         # Tool category enum
    ├── DatasetAccessor.java      # Data access abstraction
    ├── McpToolDescriptor.java    # Tool metadata descriptor
    └── ToolExecutionContext.java # Execution context (traceId, auth, etc.)
```

**pom.xml:**
```xml
<project>
    <artifactId>foggy-mcp-spi</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <!-- Minimal dependencies - interfaces only -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

**McpTool.java:**
```java
package com.foggyframework.mcp.spi;

import java.util.Map;
import java.util.Set;

/**
 * Interface for MCP tools that can be discovered and executed by the MCP server.
 * Implement this interface in addon modules to provide custom tools.
 */
public interface McpTool {

    /**
     * Unique tool name, e.g., "dataset.open_in_viewer"
     */
    String getName();

    /**
     * Categories this tool belongs to, for role-based filtering
     */
    Set<ToolCategory> getCategories();

    /**
     * Execute the tool with given arguments
     *
     * @param arguments Tool arguments from AI
     * @param context Execution context (traceId, authorization, etc.)
     * @return Tool result (will be serialized to JSON)
     */
    Object execute(Map<String, Object> arguments, ToolExecutionContext context);

    /**
     * Tool description (can be loaded from external file)
     */
    default String getDescription() {
        return "";
    }

    /**
     * JSON Schema for input validation
     */
    default Map<String, Object> getInputSchema() {
        return Map.of();
    }

    /**
     * Whether this tool supports streaming responses
     */
    default boolean supportsStreaming() {
        return false;
    }
}
```

### Optional Module: foggy-mcp-starter

Location: `foggy-mcp-starter/`

For users who want a single dependency to include all MCP features:

```
foggy-mcp-starter/
├── pom.xml
└── src/main/resources/
    └── META-INF/
        └── spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

**pom.xml:**
```xml
<project>
    <artifactId>foggy-mcp-starter</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <!-- Core MCP server -->
        <dependency>
            <groupId>com.foggyframework</groupId>
            <artifactId>foggy-dataset-mcp</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Data viewer addon -->
        <dependency>
            <groupId>com.foggyframework</groupId>
            <artifactId>foggy-data-viewer</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- Future addons can be added here -->
    </dependencies>
</project>
```

### Usage Patterns

**Pattern 1: Include specific modules**
```xml
<!-- User's pom.xml -->
<dependencies>
    <dependency>
        <groupId>com.foggyframework</groupId>
        <artifactId>foggy-dataset-mcp</artifactId>
    </dependency>
    <dependency>
        <groupId>com.foggyframework</groupId>
        <artifactId>foggy-data-viewer</artifactId>
    </dependency>
</dependencies>
```

**Pattern 2: Use starter for all features**
```xml
<!-- User's pom.xml -->
<dependencies>
    <dependency>
        <groupId>com.foggyframework</groupId>
        <artifactId>foggy-mcp-starter</artifactId>
    </dependency>
</dependencies>
```

## Component Design

### 1. New MCP Tool: `dataset.open_in_viewer`

A single new tool that converts query parameters into a viewable link.

> **Note**: The existing `dataset.query_model_v2` tool already supports `returnTotal`, `limit`, and `columns` parameters, which provides sufficient preview/estimation functionality. No separate preview tool is needed.

**Input Schema:**
```json
{
  "type": "object",
  "required": ["model", "columns", "slice"],
  "properties": {
    "model": {
      "type": "string",
      "description": "The query model name (e.g., FactOrderQueryModel)"
    },
    "columns": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Columns to display"
    },
    "slice": {
      "type": "array",
      "minItems": 1,
      "description": "Filter conditions (REQUIRED). At least one filter must be provided to limit query scope (e.g., date range, customer, status). This prevents unbounded queries on large tables."
    },
    "groupBy": {
      "type": "array",
      "description": "Grouping/aggregation fields"
    },
    "orderBy": {
      "type": "array",
      "description": "Sort order"
    },
    "title": {
      "type": "string",
      "description": "Optional title for the data view"
    }
  }
}
```

**Output:**
```json
{
  "viewerUrl": "http://localhost:8080/data-viewer/view/abc123",
  "expiresAt": "2025-01-02T10:30:00Z",
  "queryId": "abc123",
  "estimatedRowCount": 15000,
  "message": "Data viewer link created. Users can browse, filter, and export the data."
}
```

**AI Guidance in Tool Description:**
```markdown
Generate a shareable link to view large datasets in an interactive browser.

**When to use:**
- Detailed data queries expecting many rows (500+)
- When user asks for "all", "list", "export" type queries
- When data exploration (filtering, sorting, pagination) would be valuable

**When NOT to use (use dataset.query_model_v2 instead):**
- Aggregated queries with groupBy (returns summary, small result set)
- Queries with explicit small limit (≤100 rows)
- When AI needs to analyze/interpret the data directly

**IMPORTANT - Filter Requirement:**
You MUST provide at least one filter condition in the `slice` parameter to limit the
query scope. This is mandatory to prevent unbounded queries on large tables.

Examples of valid scope-limiting filters:
- Date range: `{"field": "orderDate", "op": ">=", "value": "2025-01-01"}`
- Status: `{"field": "status", "op": "=", "value": "completed"}`
- Customer: `{"field": "customer$id", "op": "=", "value": "C001"}`

If the user's request doesn't specify a scope, ask them to clarify the time range
or other filtering criteria before creating the viewer link.

**Tip:** Use dataset.query_model_v2 with returnTotal=true and limit=1 to estimate
row count before deciding which tool to use.
```

### 2. Addon Module Structure

Location: `addons/foggy-data-viewer/`

```
addons/foggy-data-viewer/
├── pom.xml                                    # Maven configuration
├── package.json                               # Frontend build config
├── vite.config.ts                             # Vite bundler config
├── tsconfig.json
│
├── src/
│   └── main/
│       ├── java/com/foggyframework/dataviewer/
│       │   ├── DataViewerApplication.java     # Spring Boot entry (optional standalone)
│       │   ├── DataViewerAutoConfiguration.java  # Auto-config for embedding
│       │   │
│       │   ├── config/
│       │   │   ├── DataViewerProperties.java  # Configuration properties
│       │   │   └── MongoConfig.java           # MongoDB configuration
│       │   │
│       │   ├── controller/
│       │   │   ├── ViewerPageController.java  # Serve SPA page
│       │   │   └── ViewerApiController.java   # REST API for data
│       │   │
│       │   ├── service/
│       │   │   ├── QueryCacheService.java     # Cache query context
│       │   │   └── ViewerLinkService.java     # Generate viewer links
│       │   │
│       │   ├── repository/
│       │   │   └── CachedQueryRepository.java # MongoDB repository
│       │   │
│       │   ├── domain/
│       │   │   ├── CachedQueryContext.java    # Cached query entity
│       │   │   └── ViewerQueryRequest.java    # Frontend query request
│       │   │
│       │   └── mcp/
│       │       └── OpenInViewerTool.java      # MCP tool implementation
│       │
│       └── resources/
│           ├── application-viewer.yml         # Default configuration
│           ├── static/                         # ← Frontend build output
│           │   ├── index.html
│           │   └── assets/
│           │       ├── index-[hash].js
│           │       └── index-[hash].css
│           └── META-INF/
│               └── spring/
│                   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
├── frontend/                                   # Frontend source (Vue.js)
│   ├── src/
│   │   ├── main.ts
│   │   ├── App.vue
│   │   ├── components/
│   │   │   ├── DataTable.vue                  # vxe-table wrapper
│   │   │   ├── ColumnFilter.vue               # Per-column filter
│   │   │   └── AggregationPanel.vue           # Aggregation controls (Phase 4)
│   │   ├── views/
│   │   │   ├── DataViewer.vue                 # Main viewer page
│   │   │   └── ExpiredLink.vue                # Expired/invalid link page
│   │   ├── services/
│   │   │   └── api.ts                         # API client
│   │   ├── stores/
│   │   │   └── viewerStore.ts                 # Pinia state
│   │   └── types/
│   │       └── index.ts
│   └── index.html
│
└── README.md
```

### 3. MongoDB Cache Layer

#### 3.1 Entity Definition

```java
@Document(collection = "cached_queries")
public class CachedQueryContext {

    @Id
    private String queryId;

    private String model;
    private List<String> columns;
    private List<Map<String, Object>> slice;
    private List<Map<String, Object>> groupBy;
    private List<Map<String, Object>> orderBy;

    private String title;
    private String authorization;  // Original user's auth context

    @Indexed(expireAfter = "0s")  // TTL index, value from field
    private Instant expiresAt;

    private Instant createdAt;
    private Long estimatedRowCount;

    // Schema info for frontend column rendering
    private List<ColumnSchema> schema;

    @Data
    public static class ColumnSchema {
        private String name;
        private String type;      // TEXT, NUMBER, DAY, etc.
        private String title;     // Display title
        private boolean filterable;
        private boolean aggregatable;
    }
}
```

#### 3.2 Repository

```java
@Repository
public interface CachedQueryRepository extends MongoRepository<CachedQueryContext, String> {

    Optional<CachedQueryContext> findByQueryIdAndExpiresAtAfter(String queryId, Instant now);

    void deleteByExpiresAtBefore(Instant cutoff);
}
```

#### 3.3 Service

```java
@Service
public class QueryCacheService {

    @Autowired
    private CachedQueryRepository repository;

    @Autowired
    private DataViewerProperties properties;

    /**
     * Cache query and generate unique ID
     */
    public CachedQueryContext cacheQuery(OpenInViewerRequest request, String authorization) {
        String queryId = generateSecureId();

        CachedQueryContext ctx = new CachedQueryContext();
        ctx.setQueryId(queryId);
        ctx.setModel(request.getModel());
        ctx.setColumns(request.getColumns());
        ctx.setSlice(request.getSlice());
        ctx.setGroupBy(request.getGroupBy());
        ctx.setOrderBy(request.getOrderBy());
        ctx.setTitle(request.getTitle());
        ctx.setAuthorization(authorization);
        ctx.setCreatedAt(Instant.now());
        ctx.setExpiresAt(Instant.now().plus(properties.getCacheTtlMinutes(), ChronoUnit.MINUTES));

        // Fetch schema from model metadata
        ctx.setSchema(fetchSchemaForColumns(request.getModel(), request.getColumns()));

        // Estimate row count (optional, can be async)
        ctx.setEstimatedRowCount(estimateRowCount(request));

        return repository.save(ctx);
    }

    /**
     * Retrieve cached query (null if expired or not found)
     */
    public Optional<CachedQueryContext> getQuery(String queryId) {
        return repository.findByQueryIdAndExpiresAtAfter(queryId, Instant.now());
    }

    private String generateSecureId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
```

### 4. Backend API Endpoints

#### 4.1 ViewerApiController

```java
@RestController
@RequestMapping("/data-viewer/api")
public class ViewerApiController {

    @Autowired
    private QueryCacheService cacheService;

    @Autowired
    private QueryFacade queryFacade;  // Reuse existing query infrastructure

    /**
     * Get cached query metadata (for initial page load)
     */
    @GetMapping("/query/{queryId}/meta")
    public ResponseEntity<QueryMetaResponse> getQueryMeta(@PathVariable String queryId) {
        return cacheService.getQuery(queryId)
            .map(ctx -> ResponseEntity.ok(new QueryMetaResponse(
                ctx.getTitle(),
                ctx.getSchema(),
                ctx.getEstimatedRowCount(),
                ctx.getExpiresAt()
            )))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Execute query with optional overrides (pagination, additional filters)
     */
    @PostMapping("/query/{queryId}/data")
    public ResponseEntity<ViewerDataResponse> queryData(
            @PathVariable String queryId,
            @RequestBody ViewerQueryRequest request) {

        Optional<CachedQueryContext> ctxOpt = cacheService.getQuery(queryId);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(410).body(
                ViewerDataResponse.expired("Query link has expired")
            );
        }

        CachedQueryContext ctx = ctxOpt.get();

        // Build query request combining cached params with overrides
        DbQueryRequestDef queryDef = buildQueryDef(ctx, request);

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryDef);
        pagingRequest.setStart(request.getStart());
        pagingRequest.setLimit(request.getLimit());

        // Execute using existing QueryFacade
        PagingResultImpl result = queryFacade.queryModelData(pagingRequest);

        return ResponseEntity.ok(ViewerDataResponse.success(
            result.getItems(),
            result.getTotal(),
            request.getStart(),
            request.getLimit()
        ));
    }

    // Note: Export endpoint will be added in Future Phases
    // @GetMapping("/query/{queryId}/export") - streaming CSV/Excel export
}
```

#### 4.2 Request/Response DTOs

```java
@Data
public class ViewerQueryRequest {
    private Integer start = 0;
    private Integer limit = 50;

    // Additional filters applied by user in viewer
    private List<SliceItem> additionalFilters;

    // Override sort order
    private List<OrderItem> orderBy;

    // Dynamic grouping (for aggregation mode)
    private List<String> groupByFields;
    private List<AggregationItem> aggregations;
}

@Data
public class ViewerDataResponse {
    private boolean success;
    private String error;
    private List<Map<String, Object>> items;
    private Long total;
    private Integer start;
    private Integer limit;
    private boolean hasMore;

    // Aggregation results (when groupBy is used)
    private Map<String, Object> aggregationSummary;
}
```

### 5. Frontend Implementation (vxe-table)

#### 5.1 Main DataViewer Component

```vue
<template>
  <div class="data-viewer">
    <!-- Header -->
    <div class="viewer-header">
      <h1>{{ queryMeta?.title || 'Data Viewer' }}</h1>
      <div class="header-actions">
        <!-- Aggregation toggle (Phase 4) -->
        <button v-if="aggregationEnabled" @click="toggleAggregation">
          {{ aggregationMode ? 'Exit Aggregation' : 'Aggregate' }}
        </button>
        <!-- Export button will be added in Future Phases -->
      </div>
    </div>

    <!-- Aggregation Panel (when enabled) -->
    <AggregationPanel
      v-if="aggregationMode"
      :columns="queryMeta?.schema"
      v-model:groupBy="groupByFields"
      v-model:aggregations="aggregations"
      @apply="fetchData"
    />

    <!-- Data Table -->
    <vxe-grid
      ref="gridRef"
      v-bind="gridOptions"
      :data="tableData"
      :loading="loading"
      @filter-change="handleFilterChange"
      @sort-change="handleSortChange"
    >
      <!-- Dynamic columns from schema -->
      <vxe-column
        v-for="col in displayColumns"
        :key="col.name"
        :field="col.name"
        :title="col.title"
        :sortable="true"
        :filters="col.filterable ? getFilterOptions(col) : undefined"
      />
    </vxe-grid>

    <!-- Pagination -->
    <vxe-pager
      :current-page="currentPage"
      :page-size="pageSize"
      :total="totalRows"
      :page-sizes="[20, 50, 100, 200]"
      @page-change="handlePageChange"
    />

    <!-- Expired State -->
    <ExpiredLink v-if="expired" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute } from 'vue-router';
import { api } from '@/services/api';
import type { QueryMeta, ColumnSchema } from '@/types';

const route = useRoute();
const queryId = computed(() => route.params.queryId as string);

const queryMeta = ref<QueryMeta | null>(null);
const tableData = ref<Record<string, any>[]>([]);
const loading = ref(false);
const expired = ref(false);

const currentPage = ref(1);
const pageSize = ref(50);
const totalRows = ref(0);

const aggregationMode = ref(false);
const groupByFields = ref<string[]>([]);
const aggregations = ref<{ field: string; type: string }[]>([]);

const currentFilters = ref<Record<string, any>>({});
const currentSort = ref<{ field: string; order: 'asc' | 'desc' } | null>(null);

onMounted(async () => {
  await loadQueryMeta();
  await fetchData();
});

async function loadQueryMeta() {
  try {
    queryMeta.value = await api.getQueryMeta(queryId.value);
  } catch (e: any) {
    if (e.status === 404 || e.status === 410) {
      expired.value = true;
    }
  }
}

async function fetchData() {
  loading.value = true;
  try {
    const response = await api.queryData(queryId.value, {
      start: (currentPage.value - 1) * pageSize.value,
      limit: pageSize.value,
      additionalFilters: Object.entries(currentFilters.value).map(([field, value]) => ({
        field,
        op: '=',
        value
      })),
      orderBy: currentSort.value ? [currentSort.value] : undefined,
      groupByFields: aggregationMode.value ? groupByFields.value : undefined,
      aggregations: aggregationMode.value ? aggregations.value : undefined
    });

    tableData.value = response.items;
    totalRows.value = response.total;
  } catch (e: any) {
    if (e.status === 410) {
      expired.value = true;
    }
  } finally {
    loading.value = false;
  }
}

function handleFilterChange({ column, values }: any) {
  if (values && values.length > 0) {
    currentFilters.value[column.field] = values;
  } else {
    delete currentFilters.value[column.field];
  }
  currentPage.value = 1;
  fetchData();
}

function handleSortChange({ field, order }: any) {
  currentSort.value = order ? { field, order } : null;
  fetchData();
}

function handlePageChange({ currentPage: page, pageSize: size }: any) {
  currentPage.value = page;
  pageSize.value = size;
  fetchData();
}
</script>
```

#### 5.2 AggregationPanel Component

```vue
<template>
  <div class="aggregation-panel">
    <div class="panel-section">
      <h3>Group By</h3>
      <div class="field-selector">
        <label v-for="col in textColumns" :key="col.name">
          <input
            type="checkbox"
            :value="col.name"
            v-model="localGroupBy"
            @change="emitUpdate"
          />
          {{ col.title }}
        </label>
      </div>
    </div>

    <div class="panel-section">
      <h3>Aggregations</h3>
      <div v-for="col in numericColumns" :key="col.name" class="agg-row">
        <span>{{ col.title }}</span>
        <select v-model="aggTypes[col.name]" @change="emitUpdate">
          <option value="">None</option>
          <option value="sum">Sum</option>
          <option value="avg">Average</option>
          <option value="min">Min</option>
          <option value="max">Max</option>
          <option value="count">Count</option>
        </select>
      </div>
    </div>

    <button @click="$emit('apply')" class="apply-btn">Apply</button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import type { ColumnSchema } from '@/types';

const props = defineProps<{
  columns: ColumnSchema[];
  groupBy: string[];
  aggregations: { field: string; type: string }[];
}>();

const emit = defineEmits(['update:groupBy', 'update:aggregations', 'apply']);

const textColumns = computed(() =>
  props.columns?.filter(c => c.type === 'TEXT' || c.type === 'STRING') || []
);

const numericColumns = computed(() =>
  props.columns?.filter(c => ['NUMBER', 'MONEY', 'INTEGER', 'BIGINT'].includes(c.type)) || []
);

const localGroupBy = ref<string[]>([...props.groupBy]);
const aggTypes = ref<Record<string, string>>({});

// Initialize agg types from props
props.aggregations.forEach(a => {
  aggTypes.value[a.field] = a.type;
});

function emitUpdate() {
  emit('update:groupBy', localGroupBy.value);
  emit('update:aggregations',
    Object.entries(aggTypes.value)
      .filter(([_, type]) => type)
      .map(([field, type]) => ({ field, type }))
  );
}
</script>
```

#### 5.3 Build Configuration (vite.config.ts)

```typescript
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { resolve } from 'path';

export default defineConfig({
  plugins: [vue()],

  root: 'frontend',

  build: {
    // Output to Spring Boot static resources
    outDir: '../src/main/resources/static',
    emptyOutDir: true,

    rollupOptions: {
      input: resolve(__dirname, 'frontend/index.html')
    }
  },

  resolve: {
    alias: {
      '@': resolve(__dirname, 'frontend/src')
    }
  },

  server: {
    proxy: {
      '/data-viewer/api': 'http://localhost:8080'
    }
  }
});
```

### 6. Configuration

#### 6.1 Application Properties

```yaml
foggy:
  data-viewer:
    enabled: true

    # Base URL for generating viewer links
    base-url: http://localhost:8080/data-viewer

    # MongoDB settings
    mongodb:
      uri: mongodb://localhost:27017/foggy_viewer
      database: foggy_viewer

    # Cache settings
    cache:
      ttl-minutes: 60           # Link expiration time
      cleanup-interval: 300000  # Cleanup expired entries every 5 min

    # Thresholds for AI guidance
    thresholds:
      large-dataset-min: 500    # Recommend viewer for 500+ rows

    # Security
    security:
      require-auth: false       # Whether viewer requires same auth as original query

    # Query scope constraints (secondary safeguard)
    # Even if AI provides filters, the system will enforce additional constraints
    scope-constraints:
      enabled: true
      default-max-duration-days: 31    # Default max query range if not specified per model

      # Per-model scope constraints
      models:
        FactOrderQueryModel:
          scope-field: orderDate       # Field used to limit query scope
          max-duration-days: 31        # Max allowed duration (e.g., 1 month)
        FactSalesQueryModel:
          scope-field: salesDate
          max-duration-days: 31
```

#### 6.2 Query Scope Constraint Service

The system enforces a **double safeguard** for query scope:

1. **AI-level**: Tool description instructs AI to always include scope-limiting filters
2. **Code-level**: `QueryScopeConstraintService` validates and enforces constraints

```java
@Service
public class QueryScopeConstraintService {

    @Autowired
    private DataViewerProperties properties;

    /**
     * Validate and enforce scope constraints on the query.
     * This is the secondary safeguard - even if AI provides filters,
     * we ensure the query is within allowed bounds.
     *
     * @param model The query model name
     * @param slice The filter conditions from AI
     * @return Validated/adjusted slice with enforced constraints
     * @throws IllegalArgumentException if no valid scope filter is provided
     */
    public List<Map<String, Object>> enforceConstraints(String model, List<Map<String, Object>> slice) {
        ModelScopeConstraint constraint = properties.getScopeConstraints().getModels().get(model);

        if (constraint == null) {
            // No specific constraint for this model, just validate slice is not empty
            if (slice == null || slice.isEmpty()) {
                throw new IllegalArgumentException(
                    "At least one filter condition is required to limit query scope"
                );
            }
            return slice;
        }

        // Find scope field filter in slice
        String scopeField = constraint.getScopeField();
        Optional<Map<String, Object>> scopeFilter = findScopeFilter(slice, scopeField);

        if (scopeFilter.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                "Query must include a filter on '%s' to limit scope. " +
                "For example: {\"field\": \"%s\", \"op\": \">=\", \"value\": \"2025-01-01\"}",
                scopeField, scopeField
            ));
        }

        // Validate duration is within allowed range
        int maxDays = constraint.getMaxDurationDays();
        validateDuration(slice, scopeField, maxDays);

        return slice;
    }

    /**
     * If only start date is provided, automatically add end date constraint.
     */
    private void validateDuration(List<Map<String, Object>> slice, String scopeField, int maxDays) {
        LocalDate startDate = extractStartDate(slice, scopeField);
        LocalDate endDate = extractEndDate(slice, scopeField);

        if (startDate != null && endDate == null) {
            // Add end date constraint: startDate + maxDays
            LocalDate autoEndDate = startDate.plusDays(maxDays);
            slice.add(Map.of(
                "field", scopeField,
                "op", "<",
                "value", autoEndDate.toString()
            ));
        } else if (startDate != null && endDate != null) {
            // Validate range doesn't exceed max
            long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
            if (daysBetween > maxDays) {
                throw new IllegalArgumentException(String.format(
                    "Query range exceeds maximum allowed duration of %d days. " +
                    "Please narrow your date range.", maxDays
                ));
            }
        }
    }
}
```

```java
@Data
@ConfigurationProperties(prefix = "foggy.data-viewer.scope-constraints")
public class ScopeConstraintProperties {

    private boolean enabled = true;
    private int defaultMaxDurationDays = 31;
    private Map<String, ModelScopeConstraint> models = new HashMap<>();

    @Data
    public static class ModelScopeConstraint {
        private String scopeField;        // e.g., "orderDate"
        private int maxDurationDays = 31; // e.g., 31 days
    }
}
```

#### 6.3 Auto Configuration

```java
@AutoConfiguration
@ConditionalOnProperty(prefix = "foggy.data-viewer", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableMongoRepositories(basePackages = "com.foggyframework.dataviewer.repository")
@Import({ViewerApiController.class, ViewerPageController.class, QueryCacheService.class})
public class DataViewerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenInViewerTool openInViewerTool(QueryCacheService cacheService,
                                              DataViewerProperties properties) {
        return new OpenInViewerTool(cacheService, properties);
    }
}
```

### 7. MCP Tool Implementation

```java
@Component
public class OpenInViewerTool implements McpTool {

    private final QueryCacheService cacheService;
    private final DataViewerProperties properties;
    private final DatasetAccessor datasetAccessor;

    @Override
    public String getName() {
        return "dataset.open_in_viewer";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        return Set.of(ToolCategory.QUERY, ToolCategory.EXPORT);
    }

    @Override
    public Object execute(Map<String, Object> arguments, String traceId, String authorization) {
        OpenInViewerRequest request = convertToRequest(arguments);

        // Cache the query
        CachedQueryContext ctx = cacheService.cacheQuery(request, authorization);

        // Build response
        String viewerUrl = properties.getBaseUrl() + "/view/" + ctx.getQueryId();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewerUrl", viewerUrl);
        result.put("queryId", ctx.getQueryId());
        result.put("expiresAt", ctx.getExpiresAt().toString());

        if (ctx.getEstimatedRowCount() != null) {
            result.put("estimatedRowCount", ctx.getEstimatedRowCount());
        }

        result.put("message", String.format(
            "Data viewer link created. The link expires at %s. " +
            "Users can browse, filter, sort, and export the data interactively.",
            ctx.getExpiresAt()
        ));

        return result;
    }
}
```

### 8. Integration

With the SPI architecture, integration is straightforward:

1. **No circular dependency**: `foggy-dataset-mcp` does NOT depend on `foggy-data-viewer`
2. **Auto-discovery**: Spring Boot auto-configuration discovers `OpenInViewerTool` bean
3. **User includes both**: Either individually or via `foggy-mcp-starter`

**Tool Configuration (application.yml):**
```yaml
mcp:
  tools:
    - name: dataset.open_in_viewer
      enabled: true
      descriptionFile: classpath:/schemas/descriptions/open_in_viewer.md
      schemaFile: classpath:/schemas/open_in_viewer_schema.json
```

**How Tool Discovery Works:**

```java
// In foggy-dataset-mcp: McpToolDispatcher
@Component
public class McpToolDispatcher {

    // Spring auto-injects all McpTool beans from classpath
    // This includes tools from foggy-data-viewer if it's on classpath
    @Autowired
    private List<McpTool> tools;

    @PostConstruct
    public void init() {
        tools.forEach(tool -> register(tool));
    }
}
```

### 9. Implementation Phases

#### Phase 0: SPI Module (Prerequisite)
1. Create `foggy-mcp-spi` module with interfaces
2. Refactor `foggy-dataset-mcp` to depend on `foggy-mcp-spi`
3. Move `McpTool`, `ToolCategory` interfaces to SPI module
4. Create `foggy-mcp-starter` integration module

#### Phase 1: Core Infrastructure
1. Create `addons/foggy-data-viewer` module structure
2. Implement MongoDB entity and repository
3. Implement `QueryCacheService` with scope constraint validation
4. Create `OpenInViewerTool` MCP tool (implements `McpTool` from SPI)
5. Add auto-configuration for embedding

#### Phase 2: Backend API
1. Implement `ViewerApiController` with data query endpoint
2. Leverage existing `QueryFacade` for query execution
3. Add filter/sort override support
4. Implement `QueryScopeConstraintService` (double safeguard)

#### Phase 3: Frontend
1. Set up Vue.js + vxe-table project in `frontend/`
2. Implement basic data table with pagination
3. Add column filtering (server-side)
4. Add column sorting
5. Build and output to `resources/static`

> **Note**: Frontend build is manual (`npm run build` then Maven).
> Build automation will be added once the project stabilizes.

#### Phase 4: Advanced Features
1. Implement aggregation mode (groupBy + aggregations)
2. Handle expired link gracefully
3. Internationalization (i18n) support

#### Future Phases
- **Data Export**: CSV/Excel export with streaming support for large datasets
- Secondary AI query capability (ask follow-up questions in viewer)
- Query history / bookmarks
- Share viewer link with others
- Real-time data refresh
- Build automation (frontend-maven-plugin)

### 10. Example User Flows

#### Flow 1: Large Dataset Query

```
User: "Show me all orders from 2025"
     ↓
AI: Uses dataset.query_model_v2 with limit=1, returnTotal=true
     → Response: { total: 15000, items: [...] }
     ↓
AI: Recognizes 15000 rows is too large, uses dataset.open_in_viewer
     ↓
Tool Returns: {
  "viewerUrl": "http://localhost:8080/data-viewer/view/abc123",
  "estimatedRowCount": 15000,
  "expiresAt": "2025-01-02T10:30:00Z",
  "message": "Data viewer link created..."
}
     ↓
AI Response: "Your query returns approximately 15,000 orders from 2025.
I've created an interactive data viewer for you:

**[Open Data Viewer](http://localhost:8080/data-viewer/view/abc123)**

In the viewer you can:
- Filter by any column (customer, product, amount, etc.)
- Sort the data
- Navigate through pages

The link expires in 1 hour."
```

#### Flow 2: Aggregated Query (Direct Response)

```
User: "What are the top 10 customers by sales in 2025?"
     ↓
AI: Uses dataset.query_model_v2 with groupBy + limit=10
     → Response: { items: [...10 rows...], total: 10 }
     ↓
AI Response: "Here are your top 10 customers by sales in 2025:

| Rank | Customer | Total Sales |
|------|----------|-------------|
| 1 | Acme Corp | $1,500,000 |
| 2 | TechCo | $1,200,000 |
..."
```

### 11. Dependencies

#### Backend (foggy-data-viewer/pom.xml)
```xml
<dependencies>
    <!-- MCP SPI - for McpTool interface (NO circular dependency) -->
    <dependency>
        <groupId>com.foggyframework</groupId>
        <artifactId>foggy-mcp-spi</artifactId>
    </dependency>

    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>

    <!-- Foggy dependencies -->
    <dependency>
        <groupId>com.foggyframework</groupId>
        <artifactId>foggy-dataset-model</artifactId>
    </dependency>

    <!-- Export (Future Phase) -->
    <!-- <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi-ooxml</artifactId>
        <version>5.2.5</version>
        <optional>true</optional>
    </dependency> -->
</dependencies>
```

#### Frontend (package.json)
```json
{
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.2.0",
    "pinia": "^2.1.0",
    "vxe-table": "^4.6.0",
    "xe-utils": "^3.5.0",
    "axios": "^1.6.0"
  },
  "devDependencies": {
    "vite": "^5.0.0",
    "typescript": "^5.3.0",
    "@vitejs/plugin-vue": "^5.0.0",
    "@types/node": "^20.0.0"
  }
}
```

---

## Appendix: DSL Query Transformation

### Original Cached Query
```json
{
  "model": "FactOrderQueryModel",
  "columns": ["orderId", "orderDate", "customer$name", "amount"],
  "slice": [
    {"field": "orderDate", "op": ">=", "value": "2025-01-01"}
  ]
}
```

### With User Filters Applied in Viewer
```json
{
  "model": "FactOrderQueryModel",
  "columns": ["orderId", "orderDate", "customer$name", "amount"],
  "slice": [
    {"field": "orderDate", "op": ">=", "value": "2025-01-01"},
    {"field": "customer$name", "op": "like", "value": "%Acme%"},
    {"field": "amount", "op": ">", "value": 1000}
  ],
  "orderBy": [{"field": "amount", "order": "DESC"}],
  "start": 0,
  "limit": 50,
  "returnTotal": true
}
```

### With User Aggregation in Viewer
```json
{
  "model": "FactOrderQueryModel",
  "columns": ["customer$name", "sum(amount)", "count(orderId)"],
  "slice": [
    {"field": "orderDate", "op": ">=", "value": "2025-01-01"}
  ],
  "groupBy": [
    {"name": "customer$name", "type": "PK"}
  ],
  "orderBy": [{"field": "sum(amount)", "order": "DESC"}]
}
```
