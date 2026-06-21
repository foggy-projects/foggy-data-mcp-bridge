# Foggy Data Viewer Release Notes

## Next beta

### Added

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

### Compatibility

- Frontend-only change. No Java engine or backend API update is required.
- Existing `column-*` / `filter-*` slots on `DataTableWithSearch` remain compatible.
- Existing `render({ row, value })` implementations continue to work if they ignore the new `column` field.
