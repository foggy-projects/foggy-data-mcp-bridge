/**
 * 字典项
 */
export interface DictItem {
  value: string | number
  label: string
}

/**
 * 列定义类型
 */
export interface ColumnSchema {
  name: string
  type: string
  title?: string
  /** 字段说明，来自 TM/QM description，用于表头帮助提示 */
  description?: string
  width?: number
  filterable?: boolean
  aggregatable?: boolean
  /** 是否允许悬浮复制该列普通单元格内容 */
  copyable?: boolean

  // 过滤器元数据
  filterType?: 'text' | 'number' | 'date' | 'datetime' | 'dict' | 'dimension' | 'bool' | 'custom'
  dictId?: string
  dictItems?: DictItem[]
  dimensionRef?: string
  memberLookup?: MemberLookupMeta
  format?: string
  measure?: boolean
  uiConfig?: Record<string, unknown>
}

/**
 * DSL 过滤条件 (SliceRequestDef)
 * 直接对应后端 DSL 格式
 */
export interface SliceRequestDef {
  field: string
  op: string  // =, !=, >, >=, <, <=, in, like, right_like, [], [), is null, is not null 等
  value?: unknown
  link?: 1 | 2  // 1=AND, 2=OR
  children?: SliceRequestDef[]
}

/**
 * DSL 排序条件 (OrderRequestDef)
 */
export type SortDirection = 'asc' | 'desc'

export interface OrderRequestDef {
  field: string
  /** 后端 DSL 识别的排序方向字段 */
  dir?: SortDirection
  /** 兼容旧版前端状态，发送请求前会被规范化为 dir */
  order?: SortDirection
  nullLast?: boolean
  nullFirst?: boolean
}

/**
 * 查询元数据响应（重构后的版本）
 */
export interface QueryMetaResponse {
  title: string
  tableConfig: TableConfig  // 改为 tableConfig
  estimatedRowCount: number | null
  expiresAt: string
  /** 初始过滤条件（来自缓存） */
  initialSlice?: SliceRequestDef[]
}

/**
 * 数据查询请求 (使用 DSL 格式)
 */
export interface ViewerQueryRequest {
  start?: number
  limit?: number
  /** 运行时扩展参数，仅透传到后端 DbQueryRequestDef.extData */
  extData?: Record<string, unknown>
  /** 过滤条件 (DSL slice 格式) */
  slice?: SliceRequestDef[]
  /** 排序条件 (DSL orderBy 格式) */
  orderBy?: OrderRequestDef[]
}

/**
 * 过滤选项（用于下拉）
 */
export interface FilterOption {
  value: string | number
  label: string
}

/**
 * 过滤选项响应
 */
export interface FilterOptionsResponse {
  options: FilterOption[]
  total: number
  error?: string
}

/**
 * 数据响应
 */
export interface ViewerDataResponse {
  success: boolean
  items: Record<string, unknown>[]
  total: number
  start: number
  limit: number
  errorMessage?: string
  expired?: boolean
  /** 全量数据汇总（包含总记录数和度量合计） */
  totalData?: Record<string, unknown>
}

/**
 * 分页状态
 */
export interface PaginationState {
  currentPage: number
  pageSize: number
  total: number
}

/**
 * 排序状态
 */
export interface SortState {
  field: string | null
  order: SortDirection | null
}

/**
 * 列定制配置
 */
export interface ColumnCustomization {
  name: string
  width?: number
  minWidth?: number
  fixed?: 'left' | 'right'
  render?: (params: { row: Record<string, unknown>; value: unknown }) => unknown
  filterComponent?: unknown
  formatter?: (value: unknown) => string
}

/**
 * 增强的列配置（合并 QM schema 和前端定制）
 */
export interface EnhancedColumnSchema extends ColumnSchema {
  width?: number
  minWidth?: number
  fixed?: 'left' | 'right'
  customRender?: (params: { row: Record<string, unknown>; value: unknown }) => unknown
  customFilterComponent?: unknown
  customFormatter?: (value: unknown) => string
}

/**
 * 单元格复制配置
 */
export interface CellCopyConfig {
  /** 是否启用普通单元格悬浮复制，默认 true */
  enabled?: boolean
}

/**
 * DataTableWithSearch 内置查询入口模式
 *
 * - panel: 只显示组件面板查询，不显示列头筛选
 * - column: 只显示列头筛选，不显示组件面板查询
 * - combined: 组件面板查询和列头筛选同时显示
 * - none: 不显示组件内置查询入口，供业务页完全自定义
 */
export type QueryMode = 'panel' | 'column' | 'combined' | 'none'

/** 表格视觉密度 */
export type TableDensity = 'default' | 'compact'

/**
 * 表格配置
 */
export interface TableConfig {
  /** QM 模型名称（可选，用于元数据） */
  qmModel?: string
  /** 显式指定显示的列及顺序（必填，除非 showAll=true） */
  visibleColumns?: string[]
  /** 显示所有列 */
  showAll?: boolean
  /** 列定制配置 */
  customizations?: ColumnCustomization[]
}

/**
 * 表格 Schema 配置（用于 DataTableWithSearch 的 schema 模式）
 */
export interface TableSchema {
  /** 列配置 */
  columns: EnhancedColumnSchema[]
  /** 单元格复制配置 */
  cellCopy?: CellCopyConfig
  /** 内置查询入口模式；设置后优先于 showFilters 等旧开关 */
  queryMode?: QueryMode
  /** 表格视觉密度 */
  density?: TableDensity
  /** 搜索工具栏显示的字段（不指定则从 columns 中筛选 uiConfig.showInToolbar=true 的列） */
  searchableFields?: string[]
  /** 每页大小 */
  pageSize?: number
  /** 是否显示表头过滤器 */
  showFilters?: boolean
  /** 是否显示分页栏（默认 true） */
  showPager?: boolean
  /** 搜索工具栏布局 */
  searchLayout?: 'horizontal' | 'vertical'
}

/**
 * 数据加载参数
 */
export interface FetchDataParams {
  page: number
  pageSize: number
  slice: SliceRequestDef[]
  orderBy: OrderRequestDef[]
}

/**
 * 数据加载结果
 */
export interface FetchDataResult<T = Record<string, unknown>> {
  items: T[]
  total: number
  /** 全量汇总数据（可选） */
  totalData?: Record<string, unknown>
}

/**
 * 数据加载函数类型
 */
export type FetchDataFn<T = Record<string, unknown>> = (
  params: FetchDataParams
) => Promise<FetchDataResult<T>>

// ========== Query Hooks ==========

/** Promise 或同步值 */
export type MaybePromise<T> = T | Promise<T>

/** 查询触发来源 */
export type QueryTrigger = 'mount' | 'filter' | 'sort' | 'page' | 'refresh' | 'reload'

/** 钩子名称 */
export type QueryHookName = 'onBeforeQuery' | 'onAfterQuery' | 'onQueryError'

/**
 * 查询钩子上下文
 */
export interface QueryHookContext {
  /** 查询参数（可在 onBeforeQuery 中修改） */
  params: FetchDataParams
  /** 触发来源 */
  trigger: QueryTrigger
}

/**
 * 查询前钩子函数
 * - 返回 false 取消本次查询
 * - 返回 FetchDataParams 替换查询参数
 * - 返回 void 继续执行
 */
export type BeforeQueryHookFn = (ctx: QueryHookContext) => MaybePromise<void | false | FetchDataParams>

export type {
  PivotAxisField,
  PivotAxisPage,
  PivotAxisPageScope,
  PivotAxisRole,
  PivotHeaderNode,
  PivotHeaderRole,
  PivotMetric,
  PivotRawAxisMember,
  PivotRawCell,
  PivotRawPayload,
  PivotShape,
  PivotViewMode,
  PivotViewModel
} from './pivot'

/**
 * 查询后钩子函数（成功时调用）
 * - 返回 FetchDataResult 替换查询结果
 * - 返回 void 使用原结果
 */
export type AfterQueryHookFn = (ctx: QueryHookContext, result: FetchDataResult) => MaybePromise<void | FetchDataResult>

/**
 * 查询错误钩子函数
 * - 返回 true 表示已处理错误（不再触发 load-error 事件）
 * - 返回 void 继续默认错误处理
 */
export type ErrorQueryHookFn = (ctx: QueryHookContext, error: Error) => MaybePromise<void | boolean>

/**
 * 查询钩子配置（用于 props 声明式传入）
 */
export interface QueryHooks {
  onBeforeQuery?: BeforeQueryHookFn
  onAfterQuery?: AfterQueryHookFn
  onQueryError?: ErrorQueryHookFn
}

// ========== List Preset ==========

/** 自定义列表可见范围 */
export type ListPresetVisibility = 'PRIVATE' | 'DEPARTMENT' | 'TENANT'

/** 自定义列表按钮挂载位置 */
export type ListPresetPlacement = 'toolbar-left' | 'toolbar-right' | 'external'

/**
 * 自定义列表配置
 *
 * v1 中 userId 由前端显式提供，用于后端隔离配置存储命名空间。
 * 它不是安全边界；真实数据权限仍由后端查询链路控制。
 */
export interface ListPresetConfig {
  enabled?: boolean
  model: string
  userId: string
  businessKey?: string
  autoLoadDefault?: boolean
  allowShared?: boolean
  allowTenantShared?: boolean
  buttonText?: string
  placement?: ListPresetPlacement
}

/** 列视图偏好 */
export interface ColumnViewSetting {
  name: string
  visible: boolean
  width?: number
  minWidth?: number
  fixed?: 'left' | 'right'
  order: number
}

/** 自定义列表中的查询条件配置 */
export interface QueryConditionPreset {
  slice: SliceRequestDef[]
  orderBy: OrderRequestDef[]
}

/** DataTableWithSearch 可保存和恢复的列表视图状态 */
export interface ListViewState {
  columns: string[]
  columnSettings?: ColumnViewSetting[]
  slice: SliceRequestDef[]
  orderBy: OrderRequestDef[]
  pageSize?: number
}

/** 自定义列表方案 */
export interface ListPresetDef {
  id: string
  model: string
  businessKey?: string
  title: string
  description?: string
  columns: string[]
  columnSettings?: ColumnViewSetting[]
  query: QueryConditionPreset
  pageSize?: number
  visibility: ListPresetVisibility
  ownerId: string
  ownerDeptId?: string
  ownerTenantId?: string
  isDefault?: boolean
  version: 1
  createdAt: string
  updatedAt: string
}

// ========== Frontend Meta v1 ==========

/** 前端元数据契约 (frontend-meta v1) */
export interface FrontendMeta {
  metaVersion: string
  model: string
  caption: string
  description?: string
  fields: FieldMeta[]
  defaults?: DefaultsMeta
  capabilities?: CapabilitiesMeta
  params?: ParamsMeta
}

/** 字段分类 */
export type FieldCategory =
  | 'dimension-id'
  | 'dimension-caption'
  | 'dimension-property'
  | 'attribute'
  | 'measure'
  | 'calculated'

/** 字段元数据 */
export interface FieldMeta {
  name: string
  title: string
  description?: string
  type: string
  category: FieldCategory
  filterType?: string
  filterable?: boolean
  sortable?: boolean
  measure?: boolean
  aggregatable?: boolean
  aggregation?: string
  sourceColumn?: string
  dictId?: string
  dictMode?: 'static' | 'remote'
  dictItems?: DictItem[]
  calculated?: boolean
  hierarchical?: boolean
  hierarchyOps?: string[]
  memberLookup?: MemberLookupMeta
  uiHints?: UiHintsMeta
}

/** 维度成员远程查询配置 */
export interface MemberLookupMeta {
  enabled: boolean
  selectionFieldName: string
  displayFieldName: string
  searchable?: boolean
  pageable?: boolean
  defaultLimit?: number
}

/** 前端 UI 提示 */
export interface UiHintsMeta {
  visible?: boolean
  required?: boolean
  nullable?: boolean
  format?: string
  width?: number
}

/** 默认配置 */
export interface DefaultsMeta {
  visibleColumns?: string[]
  searchFields?: string[]
  pageSize?: number
  /** 生成到 TableSchema.queryMode，优先级高于生成组件的 queryMode prop */
  queryMode?: QueryMode
  orderBy?: OrderRequestDef[]
}

/** 模型级能力声明 */
export interface CapabilitiesMeta {
  pageable?: boolean
  sortable?: boolean
  filterable?: boolean
  aggregatable?: boolean
}

/** 参数入口 */
export interface ParamsMeta {
  global?: Record<string, unknown>
  custom?: Record<string, unknown>
}

// ========== Member Query ==========

/** 维度成员查询请求 */
export interface MemberQueryRequest {
  qmModel: string
  fieldName: string
  keyword?: string
  start?: number
  limit?: number
  selectedValues?: Array<string | number>
  hierarchy?: {
    op: string
    value: string | number
  }
}

/** 维度成员选项 */
export interface MemberOption {
  value: string | number
  label: string
  parentValue?: string | number | null
  depth?: number
  hasChildren?: boolean
  pathValues?: Array<string | number>
  pathLabels?: string[]
  disabled?: boolean
}

/** 维度成员查询响应 */
export interface MemberQueryResponse {
  qmModel?: string
  fieldName?: string
  selectionFieldName: string
  displayFieldName: string
  hierarchical?: boolean
  hierarchyOps?: string[]
  items: MemberOption[]
  selectedItems?: MemberOption[]
  total: number
  hasMore?: boolean
}
