/**
 * 列定义类型
 */
export interface ColumnSchema {
  name: string
  type: string
  label?: string
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
}

/**
 * 数据查询请求
 */
export interface ViewerQueryRequest {
  start?: number
  limit?: number
  filters?: Record<string, FilterValue>
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}

/**
 * 过滤值类型
 */
export type FilterValue = string | number | boolean | string[] | number[]

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
