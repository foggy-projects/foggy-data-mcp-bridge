// 统一引入所有依赖样式（用户只需引入一次）
import 'vxe-pc-ui/lib/style.css'
import 'vxe-table/lib/style.css'
import 'element-plus/dist/index.css'

// 导出组件
export { default as DataTable } from './components/DataTable.vue'
export { default as DataViewer } from './components/DataViewer.vue'
export { default as SearchToolbar } from './components/SearchToolbar.vue'
export { default as DataTableWithSearch } from './components/DataTableWithSearch.vue'
export { default as QueryPanel } from './components/QueryPanel.vue'
export { default as PivotGrid } from './components/PivotGrid.vue'
export { default as ListPresetManager } from './components/list-preset/ListPresetManager.vue'

export type {
  QueryFieldSchema,
  QueryPanelLayoutSchema,
  QueryPanelExpose,
  QuerySchema
} from './components/QueryPanel.vue'

// 导出过滤器组件
export * from './components/filters'

// 导出保存查询组件
export * from './components/saved-query'

// 导出 Composables
export * from './components/composables'

// 导出工具函数
export { buildTableColumns, calculateColumnWidth } from './utils/schemaHelper'
export {
  buildPivotGridColumns,
  flattenPivotLeafNodes,
  validatePivotHeaderTree
} from './utils/pivotHeaderTree'
export type {
  BuildPivotGridColumnsOptions,
  PivotGridColumn,
  PivotHeaderValidationResult
} from './utils/pivotHeaderTree'

// 导出 API 函数
export {
  createQuery,
  fetchQueryMeta,
  fetchQueryData,
  fetchFilterOptions,
  fetchQmSchema,
  fetchQueryDataDirect,
  fetchFrontendMeta,
  fetchMemberOptions
} from './api/viewer'

// 导出 API 类型
export type {
  QueryPayload,
  CreateQueryRequest,
  CreateQueryResponse
} from './api/viewer'

// 导出保存查询 API
export {
  saveQuery,
  listSavedQueries,
  getSavedQuery,
  updateSavedQuery,
  deleteSavedQuery,
  applySavedQuery
} from './api/savedQuery'

// 导出保存查询类型
export type {
  SavedQueryDef,
  SaveQueryRequest,
  QueryVisibility
} from './api/savedQuery'

// 导出自定义列表 API
export {
  listPresets,
  getDefaultListPreset,
  createListPreset,
  getListPreset,
  updateListPreset,
  deleteListPreset,
  setDefaultListPreset,
  clearDefaultListPreset
} from './api/listPreset'

// 导出自定义列表 API 类型
export type {
  ListPresetScope,
  SaveListPresetRequest,
  UpdateListPresetRequest
} from './api/listPreset'

// 导出类型定义
export type {
  ColumnSchema,
  EnhancedColumnSchema,
  TableConfig,
  TableSchema,
  CellCopyConfig,
  QueryMode,
  ColumnCustomization,
  QueryMetaResponse,
  ViewerQueryRequest,
  ViewerDataResponse,
  SliceRequestDef,
  OrderRequestDef,
  FilterOption,
  FetchDataParams,
  FetchDataResult,
  FetchDataFn,
  DictItem,
  PaginationState,
  SortState,
  QueryHooks,
  QueryHookContext,
  QueryTrigger,
  QueryHookName,
  BeforeQueryHookFn,
  AfterQueryHookFn,
  ErrorQueryHookFn,
  MaybePromise,
  PivotAxisField,
  PivotAxisPage,
  PivotAxisPageScope,
  PivotAxisRole,
  PivotHeaderNode,
  PivotHeaderRole,
  PivotMetric,
  PivotShape,
  PivotViewMode,
  PivotViewModel,
  ListPresetVisibility,
  ListPresetPlacement,
  ListPresetConfig,
  ColumnViewSetting,
  QueryConditionPreset,
  ListViewState,
  ListPresetDef,
  // Frontend Meta v1
  FrontendMeta,
  FieldMeta,
  FieldCategory,
  MemberLookupMeta,
  UiHintsMeta,
  DefaultsMeta,
  CapabilitiesMeta,
  ParamsMeta,
  // Member Query
  MemberQueryRequest,
  MemberOption,
  MemberQueryResponse
} from './types'
