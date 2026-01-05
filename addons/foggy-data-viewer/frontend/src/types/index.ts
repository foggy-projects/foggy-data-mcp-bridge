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
 * 查询元数据响应
 */
export interface QueryMetaResponse {
  title: string
  schema: ColumnSchema[]
  estimatedRowCount: number | null
  expiresAt: string
  model: string
  columns: string[]
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
