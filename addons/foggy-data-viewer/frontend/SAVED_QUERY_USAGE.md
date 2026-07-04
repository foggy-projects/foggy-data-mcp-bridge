# Query Plan Feature - Usage Guide

## Overview

The query plan feature is implemented by `ListPresetManager` and integrated into
`DataTableWithSearch`. It lets users save, load, and reuse table view state:

- visible columns and column settings
- filter conditions
- sort rules
- page size
- visibility levels: `PRIVATE`, `DEPARTMENT`, `TENANT`
- default plan per user/model/business key

The old standalone saved-query components and API are no longer exported. Use
`DataTableWithSearch` with `enableSavedQuery` and `listPreset` instead.

## Basic Usage

```vue
<template>
  <DataTableWithSearch
    :schema="tableSchema"
    :fetch-data="fetchData"
    qm-model="FactSalesDemoAuthQueryModel"
    table-instance-id="sales-report-2024"
    enable-saved-query
    :default-query-config-scope="{ userId: 'user-123' }"
  />
</template>
```

When `enableSavedQuery` is enabled, or when `listPreset` can resolve
`model + userId`, `DataTableWithSearch` renders a toolbar-right dropdown named
`查询方案`. Explicit `listPreset.placement` can still override the placement.

The dropdown contains:

- `自定义查询`: open the field and condition configuration dialog.
- `加载查询`: open a focused query plan list and apply an existing plan.
- `保存查询`: save the current table state as a new plan.
- `可用查询`: list available user query plans in the dropdown; click one to apply it directly.
- `清空查询条件`: clear current filters while preserving columns and sort rules.

## Explicit List Preset Config

Use `listPreset` when the page needs a stable business namespace, custom
sharing behavior, or non-default placement. If `placement` is omitted it
defaults to `toolbar-right`.

```vue
<template>
  <DataTableWithSearch
    :schema="tableSchema"
    :fetch-data="fetchData"
    enable-saved-query
    :list-preset="{
      userId: 'user-123',
      model: 'FactSalesDemoAuthQueryModel',
      businessKey: 'sales-report-2024',
      allowShared: true,
      allowTenantShared: false,
      placement: 'toolbar-right'
    }"
  />
</template>
```

`businessKey` isolates plans for different pages or business contexts, for
example:

- `sales-report-2024`
- `inventory-dashboard`
- `customer-analysis`

## API Functions

Programmatic plan management uses the list-preset API:

```ts
import {
  createListPreset,
  listPresets,
  updateListPreset,
  deleteListPreset,
  getDefaultListPreset,
  setDefaultListPreset
} from 'foggy-data-viewer'
```

Most pages should not call these functions directly. Prefer the integrated
`DataTableWithSearch` toolbar unless you are building a custom management UI.

## Backend Requirements

The list-preset API persists user plans by `userId`, `model`, and `businessKey`.
Make sure the backend list-preset endpoints are enabled and the current page
passes a stable `userId`.

Sharing options depend on the backend identity and authorization integration:

- `PRIVATE`: only the owner can use the plan.
- `DEPARTMENT`: department-level sharing when enabled.
- `TENANT`: tenant-level sharing when enabled.

## Troubleshooting

### The Dropdown Does Not Render

Check that either:

- `enableSavedQuery` is true and `qmModel/defaultQueryConfigScope.userId` are
  available, or
- `listPreset` explicitly provides `model` and `userId`, or can resolve them
  from component context.

Also check that `listPreset` is not `false` and `enableSavedQuery` is not false.

### Saved Plans Do Not Appear

Verify that `userId`, `model`, and `businessKey` are stable across refreshes.
Changing any of them switches the plan namespace.

### Saved Columns Are Missing

`requiredRuntimeColumns` are intentionally not shown as normal columns.
`lockedColumns` are forced visible and cannot be removed by user plans.
