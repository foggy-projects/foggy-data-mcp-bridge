# Saved Query Feature - Usage Guide

## Overview

The saved query feature allows users to save, share, and reuse table query configurations including:
- Selected columns
- Filter conditions (with parameter configuration)
- Sort settings
- Visibility levels (PRIVATE, DEPARTMENT, TENANT)

## Installation

The saved query components are already included in the `foggy-data-viewer` package. You just need to install the dependencies:

```bash
npm install date-fns
```

## Components

### SavedQueryManager

The main entry point component that provides "Load Query" and "Save Query" buttons.

**Props:**
- `tableRef` - Reference to DataTableWithSearch instance (required)
- `model` - QM model name (required)
- `businessId` - Business ID for query isolation (optional)
- `currentUserId` - Current user ID for distinguishing own queries (optional)
- `position` - Button position: 'top' | 'bottom' (default: 'top')

### SaveQueryDialog

Three-step wizard dialog for saving queries:
1. **Step 1**: Select columns (with search, select-all, invert)
2. **Step 2**: Configure query conditions (parameter types, default values, options)
3. **Step 3**: Name and confirm (visibility selection, preview)

### QueryListDialog

Left-right split dialog for viewing saved queries:
- **Left**: My queries (with edit/delete actions)
- **Right**: Shared queries (view-only)

### OptionManagerDialog

Dialog for configuring single-select/multi-select options for query parameters.

## Basic Usage

### 1. Enable Saved Query Feature

Add `enableSavedQuery` prop to DataTableWithSearch:

```vue
<template>
  <div class="report-container">
    <!-- Query Manager (independent component) -->
    <SavedQueryManager
      :table-ref="tableRef"
      :model="modelName"
      :business-id="businessId"
      :current-user-id="userId"
      position="top"
    />

    <!-- Data Table -->
    <DataTableWithSearch
      ref="tableRef"
      :schema="tableSchema"
      :fetch-data="fetchData"
      enable-saved-query
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { DataTableWithSearch, SavedQueryManager } from 'foggy-data-viewer'

const tableRef = ref()
const modelName = 'FactSalesDemoAuthQueryModel'
const businessId = 'sales-report-2024'  // Optional: for multi-business isolation
const userId = 'user-123'  // Current user ID

// Your table schema and fetch data logic
const tableSchema = { /* ... */ }
const fetchData = async (params) => { /* ... */ }
</script>
```

### 2. Configure Authorization

The saved query API requires authentication. Add an Axios interceptor to inject the Authorization header:

```typescript
import axios from 'axios'

axios.interceptors.request.use(config => {
  const token = getAuthToken() // Your auth token logic
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
```

### 3. Backend Configuration

The backend needs a `SecurityIdentityResolver` implementation to parse user identity from the Authorization token. See the main implementation plan for details.

## Features

### Save Query

1. Click "Save Query" button
2. **Step 1**: Select columns to include in the saved query
3. **Step 2**: Configure filter conditions:
   - Select field, operator
   - Choose parameter type: Fixed value | Single-select | Multi-select
   - Set default values and options
4. **Step 3**: Name the query, add description, choose visibility
5. Click "Save Query"

### Load Query

1. Click "Load Query" button
2. Browse saved queries in left-right split view:
   - **My Queries**: Queries you created (can edit/delete)
   - **Shared Queries**: Queries shared by colleagues
3. Click "Apply" to load a saved query
4. The table will reload with the saved configuration

### Query Visibility Levels

- **PRIVATE**: Only you can see and use this query
- **DEPARTMENT**: All members in your department can see and use this query
- **TENANT**: All members in your organization can see and use this query

### Business ID Isolation

Use `businessId` to isolate queries for different business contexts. For example:
- `sales-report-2024` - Sales report queries
- `inventory-dashboard` - Inventory dashboard queries
- `customer-analysis` - Customer analysis queries

Queries with different businessIds won't show up in each other's lists.

## API Methods

The following methods are exposed via `tableRef`:

```typescript
// Get current query state (for saving)
tableRef.value.getQueryState()
// Returns: { columns: string[], slice: SliceRequestDef[], orderBy: OrderRequestDef[] }

// Apply saved query state (for loading)
tableRef.value.applyQueryState({
  columns: ['col1', 'col2'],
  slice: [/* filter conditions */],
  orderBy: [/* sort settings */]
})

// Reload data
tableRef.value.reload()

// Get column schema
tableRef.value.getSchema()
// Returns: ColumnSchema[]
```

## Styling

The saved query components use Element Plus default theme colors:
- Primary: `#409eff` (Blue)
- Success: `#67c23a` (Green)
- Warning: `#e6a23c` (Orange)
- Danger: `#f56c6c` (Red)
- Info: `#909399` (Gray)

You can customize the theme by overriding Element Plus CSS variables.

## Advanced Usage

### Programmatic Query Management

You can also use the saved query API functions directly:

```typescript
import {
  saveQuery,
  listSavedQueries,
  getSavedQuery,
  updateSavedQuery,
  deleteSavedQuery,
  applySavedQuery
} from 'foggy-data-viewer'

// Save a query
const savedQuery = await saveQuery({
  businessId: 'report-001',
  model: 'FactSalesDemoAuthQueryModel',
  title: 'Q1 Sales Report',
  description: 'Sales data for Q1 2024',
  columns: ['date', 'product', 'amount'],
  slice: [{ field: 'date', op: '>=', value: '2024-01-01' }],
  orderBy: [{ field: 'date', order: 'desc' }],
  visibility: 'DEPARTMENT'
})

// List queries
const queries = await listSavedQueries('FactSalesDemoAuthQueryModel', 'report-001')

// Apply a saved query
const result = await applySavedQuery(savedQuery.id)
// result.queryId - Use this to fetch data from the viewer API
```

## Troubleshooting

### Service Unavailable (503)

If you get a 503 error when trying to save queries, it means the `SecurityIdentityResolver` SPI is not configured on the backend. Check the backend configuration.

### Query Not Found (404)

If you get a 404 error when applying a query, it means:
- The query was deleted
- You don't have permission to access it
- The query has expired (if TTL is configured)

### Authorization Failed

Make sure your Axios interceptor is correctly injecting the Authorization header with a valid token.

## Examples

See the implementation plan document for complete UI examples and detailed component specifications.
