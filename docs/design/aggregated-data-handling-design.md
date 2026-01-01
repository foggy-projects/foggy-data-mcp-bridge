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
│  │  │ Filtering │ │ Aggregation  │ │ Pagination │ │ Export (CSV/XLS) │   ││
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

## Component Design

### 1. New MCP Tool: `dataset.open_in_viewer`

A single new tool that converts query parameters into a viewable link.

> **Note**: The existing `dataset.query_model_v2` tool already supports `returnTotal`, `limit`, and `columns` parameters, which provides sufficient preview/estimation functionality. No separate preview tool is needed.

**Input Schema:**
```json
{
  "type": "object",
  "required": ["model", "columns"],
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
      "description": "Filter conditions (same format as query_model)"
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
│   │   │   ├── AggregationPanel.vue           # Aggregation controls
│   │   │   └── ExportButton.vue               # Export functionality
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

    /**
     * Export data as CSV
     */
    @GetMapping("/query/{queryId}/export")
    public ResponseEntity<Resource> exportCsv(
            @PathVariable String queryId,
            @RequestParam(defaultValue = "csv") String format) {
        // Implementation for CSV/Excel export
    }
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
        <button @click="toggleAggregation">
          {{ aggregationMode ? 'Exit Aggregation' : 'Aggregate' }}
        </button>
        <ExportButton :queryId="queryId" />
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
```

#### 6.2 Auto Configuration

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

### 8. Integration with foggy-dataset-mcp

To enable the tool in the MCP server, add dependency and configuration:

**pom.xml (foggy-dataset-mcp):**
```xml
<dependency>
    <groupId>com.foggyframework</groupId>
    <artifactId>foggy-data-viewer</artifactId>
    <version>${project.version}</version>
    <optional>true</optional>
</dependency>
```

**application.yml:**
```yaml
mcp:
  tools:
    - name: dataset.open_in_viewer
      enabled: true
      descriptionFile: classpath:/schemas/descriptions/open_in_viewer.md
      schemaFile: classpath:/schemas/open_in_viewer_schema.json
```

### 9. Implementation Phases

#### Phase 1: Core Infrastructure
1. Create `addons/foggy-data-viewer` module structure
2. Implement MongoDB entity and repository
3. Implement `QueryCacheService`
4. Create `OpenInViewerTool` MCP tool
5. Add auto-configuration for embedding

#### Phase 2: Backend API
1. Implement `ViewerApiController` with data query endpoint
2. Leverage existing `QueryFacade` for query execution
3. Add filter/sort override support
4. Implement CSV export endpoint

#### Phase 3: Frontend
1. Set up Vue.js + vxe-table project in `frontend/`
2. Implement basic data table with pagination
3. Add column filtering (server-side)
4. Add column sorting
5. Build and output to `resources/static`

#### Phase 4: Advanced Features
1. Implement aggregation mode (groupBy + aggregations)
2. Add Excel export support
3. Handle expired link gracefully
4. Internationalization (i18n) support

#### Future Phases
- Secondary AI query capability (ask follow-up questions in viewer)
- Query history / bookmarks
- Share viewer link with others
- Real-time data refresh

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
- Aggregate by dimensions
- Export to CSV/Excel

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

#### Backend (pom.xml)
```xml
<dependencies>
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

    <!-- Export -->
    <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi-ooxml</artifactId>
        <version>5.2.5</version>
        <optional>true</optional>
    </dependency>
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
