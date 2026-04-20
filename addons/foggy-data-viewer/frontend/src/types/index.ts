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
  width?: number
  filterable?: boolean
  aggregatable?: boolean

  // 过滤器元数据
  filterType?: 'text' | 'number' | 'date' | 'datetime' | 'dict' | 'dimension' | 'bool' | 'custom'
  dictId?: string
  dictItems?: DictItem[]
  dimensionRef?: string
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
export interface OrderRequestDef {
  field: string
  order: 'asc' | 'desc'
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
  order: 'asc' | 'desc' | null
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
  /** 搜索工具栏显示的字段（不指定则从 columns 中筛选 uiConfig.showInToolbar=true 的列） */
  searchableFields?: string[]
  /** 每页大小 */
  pageSize?: number
  /** 是否显示表头过滤器 */
  showFilters?: boolean
  /** 是否显示分页栏（默认 true） */
  showPager?: boolean
  /** 是否显示搜索工具栏 */
  showSearchToolbar?: boolean
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
  orderBy?: Array<{ field: string; order: 'asc' | 'desc' }>
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
