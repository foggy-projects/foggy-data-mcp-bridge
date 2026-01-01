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
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           MCP Server (foggy-dataset-mcp)                     │
│  ┌─────────────────────┐  ┌──────────────────┐  ┌────────────────────────┐  │
│  │ dataset.query_model │  │ dataset.preview  │  │ dataset.open_in_viewer │  │
│  │   (aggregated)      │  │   (first N rows) │  │   (returns link)       │  │
│  └─────────────────────┘  └──────────────────┘  └────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Query Cache Layer (Redis/Memory)                     │
│           Cache query parameters + results with TTL for viewer access        │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      Data Viewer Service (New Addon)                         │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                    Vue.js + vxe-table Frontend                          ││
│  │  ┌───────────┐ ┌──────────────┐ ┌────────────┐ ┌───────────────────┐   ││
│  │  │ Filtering │ │ Aggregation  │ │ Pagination │ │ AI Secondary Query│   ││
│  │  └───────────┘ └──────────────┘ └────────────┘ └───────────────────┘   ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                    Backend API (Express/Koa)                            ││
│  │       Provides cached query execution + live filtering/aggregation      ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
```

## Component Design

### 1. New MCP Tools

#### 1.1 `dataset.open_in_viewer` Tool

A new tool that converts query parameters into a viewable link.

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
      "description": "Filter conditions"
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
    },
    "description": {
      "type": "string",
      "description": "Optional description of what this data represents"
    }
  }
}
```

**Output:**
```json
{
  "viewerUrl": "https://data-viewer.example.com/view/abc123",
  "expiresAt": "2025-01-02T10:30:00Z",
  "queryId": "abc123",
  "estimatedRowCount": 15000,
  "message": "This query returns approximately 15,000 rows. Open the link to view and interact with the data."
}
```

#### 1.2 `dataset.preview_data` Tool

Preview first N rows to help AI decide if full data is needed.

**Input Schema:**
```json
{
  "type": "object",
  "required": ["model", "columns"],
  "properties": {
    "model": { "type": "string" },
    "columns": { "type": "array" },
    "slice": { "type": "array" },
    "previewLimit": {
      "type": "integer",
      "default": 5,
      "description": "Number of sample rows to return"
    },
    "includeStats": {
      "type": "boolean",
      "default": true,
      "description": "Include row count and column statistics"
    }
  }
}
```

**Output:**
```json
{
  "preview": [
    {"orderId": "ORD001", "customerName": "Acme Corp", "amount": 1500.00},
    {"orderId": "ORD002", "customerName": "TechCo", "amount": 2300.00}
  ],
  "stats": {
    "totalRows": 15000,
    "columns": {
      "amount": { "min": 10.00, "max": 50000.00, "avg": 1250.50 }
    }
  },
  "recommendation": "large_dataset",
  "message": "This query returns 15,000 rows. Consider using dataset.open_in_viewer for better user experience."
}
```

### 2. Query Cache Layer

#### 2.1 Cache Strategy

```java
public interface QueryCacheService {

    /**
     * Store query parameters and generate a unique query ID
     * @param request The query request with model, columns, filters, etc.
     * @param authorization User authorization context
     * @param ttlMinutes Cache TTL (default: 60 minutes)
     * @return Unique query ID for later retrieval
     */
    String cacheQuery(SemanticQueryRequest request, String authorization, int ttlMinutes);

    /**
     * Retrieve cached query parameters
     * @param queryId The unique query ID
     * @return Cached query request or null if expired
     */
    CachedQueryContext getQuery(String queryId);

    /**
     * Execute cached query with optional overrides (pagination, filters)
     * @param queryId The unique query ID
     * @param overrides Runtime overrides for pagination/filtering
     * @return Query results
     */
    SemanticQueryResponse executeQuery(String queryId, QueryOverrides overrides);
}
```

#### 2.2 Cache Data Structure

```java
public class CachedQueryContext {
    private String queryId;
    private String model;
    private SemanticQueryRequest originalRequest;
    private String authorization;
    private String title;
    private String description;
    private Instant createdAt;
    private Instant expiresAt;
    private Long estimatedRowCount;
    private SchemaInfo schema;  // Column metadata for frontend
}
```

### 3. Data Viewer Service (New Addon)

Location: `addons/foggy-data-viewer/`

#### 3.1 Project Structure

```
addons/foggy-data-viewer/
├── package.json
├── Dockerfile
├── docker-compose.yml
├── vite.config.ts
├── tsconfig.json
│
├── src/
│   ├── main.ts                    # Vue app entry
│   ├── App.vue                    # Root component
│   │
│   ├── components/
│   │   ├── DataTable.vue          # Main vxe-table wrapper
│   │   ├── ColumnFilter.vue       # Per-column filter UI
│   │   ├── AggregationPanel.vue   # Column aggregation controls
│   │   ├── PaginationBar.vue      # Pagination controls
│   │   └── AiQueryPanel.vue       # Secondary AI query input
│   │
│   ├── views/
│   │   ├── DataViewer.vue         # Main viewer page
│   │   └── NotFound.vue           # 404 / expired link page
│   │
│   ├── services/
│   │   ├── api.ts                 # Backend API client
│   │   └── queryBuilder.ts        # DSL query builder helpers
│   │
│   ├── stores/
│   │   └── dataStore.ts           # Pinia store for data state
│   │
│   └── types/
│       └── index.ts               # TypeScript interfaces
│
├── server/
│   ├── index.ts                   # Express/Koa server
│   ├── routes/
│   │   ├── view.ts                # /view/:queryId - serve viewer page
│   │   ├── api.ts                 # /api/query/:queryId - fetch data
│   │   └── ai.ts                  # /api/ai/query - secondary AI queries
│   │
│   └── services/
│       ├── mcpClient.ts           # Client to call MCP server
│       └── cacheClient.ts         # Redis/memory cache client
│
└── public/
    └── index.html
```

#### 3.2 Frontend Features (vxe-table)

##### Column Filtering

Leverage the DSL's `slice` capability for server-side filtering:

```typescript
interface ColumnFilter {
  field: string;
  operator: 'eq' | 'ne' | 'gt' | 'lt' | 'gte' | 'lte' | 'like' | 'in' | 'between';
  value: any;
}

// Convert vxe-table filter to DSL slice
function toSliceItem(filter: ColumnFilter): SliceItem {
  const opMap = {
    'eq': '=',
    'ne': '!=',
    'gt': '>',
    'lt': '<',
    'gte': '>=',
    'lte': '<=',
    'like': 'like',
    'in': 'in',
    'between': '[]'
  };
  return {
    field: filter.field,
    op: opMap[filter.operator],
    value: filter.value
  };
}
```

##### Column Aggregation

Allow users to add aggregations dynamically:

```typescript
interface ColumnAggregation {
  field: string;
  type: 'sum' | 'avg' | 'min' | 'max' | 'count' | 'distinct';
}

// Generate groupBy DSL from user selections
function toGroupByItems(aggregations: ColumnAggregation[], groupFields: string[]): GroupByItem[] {
  return [
    ...groupFields.map(f => ({ name: f, type: 'PK' })),
    ...aggregations.map(a => ({
      name: a.field,
      type: a.type.toUpperCase()
    }))
  ];
}
```

##### Pagination

Server-side pagination using DSL's `start` and `limit`:

```typescript
interface PaginationState {
  currentPage: number;
  pageSize: number;
  totalRows: number;
}

function toPaginationParams(state: PaginationState) {
  return {
    start: (state.currentPage - 1) * state.pageSize,
    limit: state.pageSize,
    returnTotal: true
  };
}
```

##### Secondary AI Query

Allow users to ask follow-up questions based on current data:

```vue
<template>
  <div class="ai-query-panel">
    <textarea
      v-model="aiQuery"
      placeholder="Ask about this data, e.g., 'Which customer has the highest growth rate?'"
    />
    <button @click="submitAiQuery">Ask AI</button>
    <div v-if="aiResponse" class="ai-response">
      {{ aiResponse }}
    </div>
  </div>
</template>

<script setup lang="ts">
const submitAiQuery = async () => {
  const response = await api.askAI({
    queryId: props.queryId,
    question: aiQuery.value,
    currentFilters: currentFilters.value,
    visibleData: getCurrentPageData()
  });
  aiResponse.value = response.answer;
};
</script>
```

#### 3.3 Backend API Endpoints

```typescript
// GET /view/:queryId
// Serve the data viewer SPA with embedded query context

// GET /api/query/:queryId
// Fetch paginated data with optional filter/sort overrides
interface QueryRequest {
  queryId: string;
  start?: number;
  limit?: number;
  filters?: ColumnFilter[];
  sorts?: { field: string; order: 'asc' | 'desc' }[];
  groupBy?: string[];
  aggregations?: ColumnAggregation[];
}

interface QueryResponse {
  data: Record<string, any>[];
  schema: {
    columns: { name: string; type: string; title: string }[];
  };
  pagination: {
    total: number;
    start: number;
    limit: number;
    hasMore: boolean;
  };
  aggregationResults?: Record<string, number>;
}

// POST /api/ai/query
// Secondary AI query with data context
interface AiQueryRequest {
  queryId: string;
  question: string;
  currentFilters?: ColumnFilter[];
  dataContext?: {
    sampleRows?: Record<string, any>[];
    aggregations?: Record<string, number>;
  };
}

interface AiQueryResponse {
  answer: string;
  suggestedFilters?: ColumnFilter[];
  suggestedAggregations?: ColumnAggregation[];
}
```

### 4. MCP Tool Integration Flow

#### 4.1 Query Detection Logic

```java
public class QueryTypeDetector {

    private static final int AGGREGATED_THRESHOLD = 100;
    private static final int LARGE_DATASET_THRESHOLD = 500;

    public enum QueryType {
        AGGREGATED,    // Return directly to AI
        MEDIUM,        // AI decides (preview available)
        DETAILED       // Recommend viewer link
    }

    public QueryType detectQueryType(SemanticQueryRequest request) {
        // Check if query has groupBy (aggregation)
        boolean hasGroupBy = request.getGroupBy() != null && !request.getGroupBy().isEmpty();

        // Check limit
        Integer limit = request.getLimit();

        if (hasGroupBy && (limit == null || limit <= AGGREGATED_THRESHOLD)) {
            return QueryType.AGGREGATED;
        }

        if (limit != null && limit > LARGE_DATASET_THRESHOLD) {
            return QueryType.DETAILED;
        }

        // For no limit or medium range, estimate row count
        Long estimatedCount = estimateRowCount(request);

        if (estimatedCount != null) {
            if (estimatedCount <= AGGREGATED_THRESHOLD) {
                return QueryType.AGGREGATED;
            } else if (estimatedCount > LARGE_DATASET_THRESHOLD) {
                return QueryType.DETAILED;
            }
        }

        return QueryType.MEDIUM;
    }
}
```

#### 4.2 AI Guidance in Tool Descriptions

Update tool descriptions to guide AI on when to use which tool:

**`dataset.query_model_v2` description update:**
```markdown
Execute a structured data query against a semantic model.

**When to use:**
- Aggregated queries (with groupBy) returning summary statistics
- Small result sets (≤100 rows expected)
- When AI needs to analyze and interpret the data directly

**When NOT to use:**
- Detailed data queries expecting 500+ rows
- When user wants to browse/export raw data
- For queries like "show me all orders" or "list all customers"

For large detailed queries, use `dataset.open_in_viewer` instead to provide
a link where users can interactively browse, filter, and export the data.
```

**`dataset.open_in_viewer` description:**
```markdown
Generate a shareable link to view large datasets in an interactive browser.

**When to use:**
- Detailed data queries expecting many rows (500+)
- When user asks for "all", "list", "export" type queries
- When data exploration (filtering, sorting, pagination) would be valuable

**Returns:**
- A URL that opens an interactive data viewer
- The viewer supports: column filtering, aggregation, pagination, export
- Users can also ask follow-up AI questions about the displayed data

**Example scenarios:**
- "Show me all orders from 2025" → Use this tool
- "Export customer list" → Use this tool
- "Top 10 sales by region" → Use dataset.query_model_v2 instead (aggregated, small result)
```

### 5. Configuration

#### 5.1 Application Properties

```yaml
mcp:
  data-viewer:
    # Viewer service URL (external accessible)
    base-url: https://data-viewer.example.com

    # Query cache settings
    cache:
      ttl-minutes: 60
      max-entries: 10000
      storage: redis  # or 'memory'
      redis-url: redis://localhost:6379

    # Thresholds for query type detection
    thresholds:
      aggregated-max: 100      # Max rows for direct AI response
      large-dataset-min: 500   # Min rows to recommend viewer
      preview-rows: 5          # Sample rows in preview

    # Secondary AI query
    ai-query:
      enabled: true
      max-context-rows: 50     # Max rows to include in AI context
```

#### 5.2 Tool Enable/Disable

```yaml
mcp:
  tools:
    - name: dataset.open_in_viewer
      enabled: true
      descriptionFile: classpath:/schemas/descriptions/open_in_viewer.md
      schemaFile: classpath:/schemas/open_in_viewer_schema.json

    - name: dataset.preview_data
      enabled: true
      descriptionFile: classpath:/schemas/descriptions/preview_data.md
      schemaFile: classpath:/schemas/preview_data_schema.json
```

### 6. Security Considerations

#### 6.1 Query Link Security

1. **Query ID**: Use cryptographically secure random IDs (UUID v4 or similar)
2. **Expiration**: Links expire after configurable TTL (default: 60 minutes)
3. **Authorization**: Cache original user's authorization context
4. **Rate Limiting**: Limit query executions per queryId to prevent abuse
5. **Read-Only**: Viewer can only read data, no modifications

#### 6.2 Data Access Control

```java
public class ViewerSecurityService {

    public void validateAccess(String queryId, HttpServletRequest request) {
        CachedQueryContext ctx = cacheService.getQuery(queryId);

        if (ctx == null) {
            throw new QueryExpiredException("Query link has expired");
        }

        // Optional: Verify viewer has same authorization as original requester
        // Or: Allow public access if configured
        if (config.isAuthRequired()) {
            validateAuthorization(request, ctx.getAuthorization());
        }
    }
}
```

### 7. Implementation Phases

#### Phase 1: Core Infrastructure
1. Implement `QueryCacheService` with Redis/Memory storage
2. Create `dataset.open_in_viewer` MCP tool
3. Add viewer URL generation logic
4. Update tool descriptions for AI guidance

#### Phase 2: Data Viewer Frontend
1. Set up Vue.js + vxe-table project
2. Implement basic data display with pagination
3. Add column filtering UI
4. Add column sorting

#### Phase 3: Advanced Features
1. Implement column aggregation controls
2. Add export functionality (CSV, Excel)
3. Create secondary AI query panel
4. Add query history/bookmarks

#### Phase 4: Polish & Production
1. Add error handling and loading states
2. Implement responsive design
3. Add internationalization (i18n)
4. Performance optimization (virtual scrolling)
5. Documentation and examples

### 8. Example User Flows

#### Flow 1: Large Dataset Query

```
User: "Show me all orders from 2025"
     ↓
AI: Recognizes this is a detailed query, uses dataset.open_in_viewer
     ↓
Tool Returns: {
  "viewerUrl": "https://viewer.example.com/view/abc123",
  "estimatedRowCount": 15000,
  "message": "This query returns ~15,000 orders..."
}
     ↓
AI Response: "I've prepared a data viewer for you with all 15,000 orders
from 2025. You can open this link to browse the data:

**[View Orders Data](https://viewer.example.com/view/abc123)**

The viewer lets you:
- Filter by any column
- Sort the data
- Export to CSV/Excel
- Ask me follow-up questions about specific findings

The link expires in 1 hour."
```

#### Flow 2: Aggregated Query

```
User: "What are the top 10 customers by sales in 2025?"
     ↓
AI: Recognizes this is aggregated (groupBy + limit 10), uses dataset.query_model_v2
     ↓
Tool Returns: {
  "items": [
    {"customer": "Acme Corp", "totalSales": 1500000},
    {"customer": "TechCo", "totalSales": 1200000},
    ...
  ],
  "pagination": {"total": 10, "returned": 10}
}
     ↓
AI Response: "Here are your top 10 customers by sales in 2025:

| Rank | Customer | Total Sales |
|------|----------|-------------|
| 1 | Acme Corp | $1,500,000 |
| 2 | TechCo | $1,200,000 |
...

Acme Corp leads with $1.5M in sales, 25% higher than..."
```

#### Flow 3: Secondary AI Query in Viewer

```
[User is viewing orders in the data viewer]
[User filters to show only "Pending" orders]
[User types in AI query panel]: "Why are there so many pending orders from Customer X?"
     ↓
Viewer sends to /api/ai/query:
{
  "queryId": "abc123",
  "question": "Why are there so many pending orders from Customer X?",
  "currentFilters": [{"field": "status", "op": "=", "value": "Pending"}],
  "dataContext": {
    "sampleRows": [...first 20 visible rows...],
    "aggregations": {"count": 156}
  }
}
     ↓
AI analyzes context and responds in the panel
```

### 9. Dependencies

#### Backend (Java)
- Spring Boot 3.x (existing)
- Spring Data Redis (for caching)
- Existing foggy-dataset-model for query execution

#### Frontend (New)
```json
{
  "dependencies": {
    "vue": "^3.4",
    "vxe-table": "^4.6",
    "xe-utils": "^3.5",
    "pinia": "^2.1",
    "axios": "^1.6",
    "vue-router": "^4.2"
  },
  "devDependencies": {
    "vite": "^5.0",
    "typescript": "^5.3",
    "@vitejs/plugin-vue": "^5.0"
  }
}
```

### 10. Success Metrics

1. **Token Efficiency**: Reduction in tokens used for large dataset queries
2. **User Experience**: Time to first meaningful interaction with data
3. **Query Completion**: % of large queries that users successfully view via link
4. **Secondary Queries**: Usage of AI follow-up questions in viewer
5. **Export Usage**: % of viewer sessions that result in data export

---

## Appendix A: vxe-table Configuration Example

```vue
<template>
  <vxe-grid
    ref="gridRef"
    v-bind="gridOptions"
    @filter-change="handleFilterChange"
    @sort-change="handleSortChange"
    @page-change="handlePageChange"
  >
    <template #toolbar>
      <vxe-button @click="toggleAggregation">Toggle Aggregation</vxe-button>
      <vxe-button @click="exportData">Export</vxe-button>
    </template>
  </vxe-grid>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import { VxeGridProps } from 'vxe-table';

const gridOptions = reactive<VxeGridProps>({
  border: true,
  resizable: true,
  showOverflow: true,
  height: 600,

  // Enable column filtering
  filterConfig: {
    remote: true  // Server-side filtering
  },

  // Enable sorting
  sortConfig: {
    remote: true,  // Server-side sorting
    multiple: true
  },

  // Pagination
  pagerConfig: {
    enabled: true,
    pageSize: 50,
    pageSizes: [20, 50, 100, 200]
  },

  // Columns generated from schema
  columns: [],

  // Data from API
  data: []
});
</script>
```

## Appendix B: DSL Query Examples

### Original Query (Detailed)
```json
{
  "model": "FactOrderQueryModel",
  "columns": ["orderId", "orderDate", "customer$name", "product$name", "quantity", "amount"],
  "slice": [
    {"field": "orderDate", "op": ">=", "value": "2025-01-01"},
    {"field": "orderDate", "op": "<", "value": "2026-01-01"}
  ],
  "orderBy": [{"field": "orderDate", "order": "DESC"}]
}
```

### With Viewer Filters Applied
```json
{
  "model": "FactOrderQueryModel",
  "columns": ["orderId", "orderDate", "customer$name", "product$name", "quantity", "amount"],
  "slice": [
    {"field": "orderDate", "op": ">=", "value": "2025-01-01"},
    {"field": "orderDate", "op": "<", "value": "2026-01-01"},
    {"field": "customer$name", "op": "like", "value": "%Acme%"},
    {"field": "amount", "op": ">", "value": 1000}
  ],
  "orderBy": [{"field": "amount", "order": "DESC"}],
  "start": 0,
  "limit": 50,
  "returnTotal": true
}
```

### With User-Added Aggregation
```json
{
  "model": "FactOrderQueryModel",
  "columns": ["customer$name", "sum(amount)", "count(orderId)", "avg(amount)"],
  "slice": [
    {"field": "orderDate", "op": ">=", "value": "2025-01-01"},
    {"field": "orderDate", "op": "<", "value": "2026-01-01"}
  ],
  "groupBy": [
    {"name": "customer$name", "type": "PK"}
  ],
  "orderBy": [{"field": "sum(amount)", "order": "DESC"}]
}
```
