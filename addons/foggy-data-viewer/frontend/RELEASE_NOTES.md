# Foggy Data Viewer Release Notes

## Next beta

### Changed

- `DisplayValueColumn.type` is now required by the public table/export projection contract, preventing export adapters from dropping type metadata.
- Clarified the TMS adapter contract for preserving column metadata and delegating Excel serialization and download to TMS.
- Direct query requests now require explicit non-empty `columns`.
- Generated query APIs send the current displayed business columns and exclude frontend-only `_actions`.
- `DataTableWithSearch` keeps direct-query columns aligned with active list-view / saved-query column state.
- `fetchQueryDataDirect` normalizes direct-query columns in the package, filtering blanks, `_actions`, and duplicates before HTTP.
- `foggy-gen` treats empty `defaults.visibleColumns` as omitted and falls back to visible business fields.
- Generated QueryTable wrappers keep `reload()` as a single `DataTableWithSearch.reload()` pass-through.

### Compatibility

- Upgrade direct-query callers to pass `columns`; blank or missing columns now fail fast before the backend query executes.
- Direct-query empty-column errors now include the QM model name.
- `queryId` / saved-query execution can still use the columns cached when the query was created.

### Added

- Added a query-field `endOfDay` option for datetime ranges. It defaults the selected end date to 23:59 and serializes that day as an exclusive next-day midnight bound.
- Existing inclusive datetime ranges with a midnight end now display and query through 23:59 when `endOfDay` is enabled.
- Custom end times use the next minute as the exclusive query bound (for example, 13:59 becomes `< 14:00:00`) so the selected minute is complete.
- Datetime table header and search-toolbar filters now select 23:59 by default for the end date, so same-day queries include the whole day.
- Added explicit shared table/export display-value coverage for MONEY viewer scaling.
- Added cell render context exports: `CellRenderContext` and `CellRenderFn`.
- `ColumnCustomization.render` and `EnhancedColumnSchema.customRender` now receive `{ row, value, column }`.
- `foggy-gen` generated QueryTable wrappers now forward `column-*` and `filter-*` dynamic slots to `DataTableWithSearch`.
- Upstream pages can render hyperlink-like cells with `#column-{field}` and handle click behavior in business code.

### Usage

Use `column-*` slots for interactive cells:

```vue
<OrderQueryTable>
  <template #column-orderNo="{ row, value }">
    <button type="button" class="link-cell" @click.stop="openOrder(row)">
      {{ value || '-' }}
    </button>
  </template>
</OrderQueryTable>
```

Use `render` for pure display changes:

```typescript
const columnOverrides = {
  status: {
    render: ({ value, column }) => `${column.title}: ${String(value ?? '-')}`
  }
}
```

### Existing Compatibility Notes

- The slot/render additions are frontend-only.
- Existing `column-*` / `filter-*` slots on `DataTableWithSearch` remain compatible.
- Existing `render({ row, value })` implementations continue to work if they ignore the new `column` field.
