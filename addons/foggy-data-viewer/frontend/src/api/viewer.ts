import axios from 'axios'
import type {
  QueryMetaResponse,
  ViewerQueryRequest,
  ViewerDataResponse,
  FilterOptionsResponse,
  ColumnSchema,
  FrontendMeta,
  MemberQueryRequest,
  MemberQueryResponse,
  OrderRequestDef
} from '@/types'

const apiClient = axios.create({
  baseURL: '/data-viewer/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

/**
 * 查询 payload（与 dataset.query_model 格式一致）
 */
export interface QueryPayload {
  columns: string[]
  extData?: Record<string, unknown>
  slice: Array<{ field: string; op: string; value?: unknown }>
  groupBy?: Array<{ field: string; agg?: string }>
  orderBy?: OrderRequestDef[]
  calculatedFields?: Array<{ name: string; expression: string; agg?: string }>
}

/**
 * 创建查询请求类型
 */
export interface CreateQueryRequest {
  model: string
  payload: QueryPayload
  title?: string
}

/**
 * 创建查询响应类型
 */
export interface CreateQueryResponse {
  success: boolean
  queryId: string | null
  viewerUrl: string | null
  error: string | null
}

/**
 * 创建查询（从 DSL 输入）
 */
export async function createQuery(request: CreateQueryRequest): Promise<CreateQueryResponse> {
  const response = await apiClient.post<any>('/query/create', request)

  // Handle RX response format: { code: 200, msg: "", data: {} }
  if (!response.data || response.data.code !== 200) {
    // Return the error response if available in data
    if (response.data?.data) {
      return response.data.data
    }
    throw new Error(response.data?.msg || '创建查询失败')
  }

  return response.data.data
}

/**
 * 获取查询元数据
 */
export async function fetchQueryMeta(model: string, queryId: string): Promise<QueryMetaResponse> {
  const response = await apiClient.get<any>(`/query/${encodeURIComponent(model)}/${queryId}/meta`)

  // Handle RX response format: { code: 200, msg: "", data: {} }
  if (!response.data || response.data.code !== 200) {
    throw new Error(response.data?.msg || '获取查询元数据失败')
  }

  return response.data.data
}

/**
 * 查询数据
 */
export async function fetchQueryData(
  model: string,
  queryId: string,
  request: ViewerQueryRequest
): Promise<ViewerDataResponse> {
  const response = await apiClient.post<any>(`/query/${encodeURIComponent(model)}/${queryId}/data`, request)

  // Handle RX response format: { code: 200, msg: "", data: {} }
  if (!response.data || response.data.code !== 200) {
    // If it's an expired query (status 410), return the expired response
    if (response.data?.data?.expired) {
      return response.data.data
    }
    throw new Error(response.data?.msg || '查询数据失败')
  }

  return response.data.data
}

/**
 * 获取过滤选项（维度成员或字典项）
 */
export async function fetchFilterOptions(
  model: string,
  queryId: string,
  columnName: string
): Promise<FilterOptionsResponse> {
  const response = await apiClient.get<FilterOptionsResponse>(
    `/query/${encodeURIComponent(model)}/${queryId}/filter-options/${encodeURIComponent(columnName)}`
  )
  return response.data
}

/**
 * 获取 QM Schema（查询模型的字段元数据）
 */
export async function fetchQmSchema(qmModel: string): Promise<ColumnSchema[]> {
  const response = await apiClient.get<any>(`/schema/${encodeURIComponent(qmModel)}`)

  // Handle RX response format: { code: 200, msg: "", data: {} }
  if (!response.data || response.data.code !== 200) {
    throw new Error(response.data?.msg || '获取 QM Schema 失败')
  }

  const data = response.data.data

  // 解析 SemanticMetadataResponse V3 格式
  // 返回格式：{ "version": "v3", "fields": { "fieldName": { "name": "显示名", "type": "TEXT", ... } }, "models": {...} }
  if (!data || !data.fields) {
    return []
  }

  // 遍历 fields 对象，转换为 ColumnSchema 数组
  const columns: ColumnSchema[] = []
  for (const [fieldName, fieldInfo] of Object.entries(data.fields)) {
    const field = fieldInfo as any

    // 直接使用后端返回的字段（不再解析 meta）
    columns.push({
      name: fieldName,
      title: field.name || fieldName,
      type: field.type || 'TEXT',
      filterable: field.filterable !== false,
      aggregatable: field.aggregatable || false,
      measure: field.measure || false,
      filterType: field.filterType,
      dictId: field.dictId,
      dictItems: field.dictItems,
      format: field.format
    })
  }

  return columns
}

// ========== Direct Query (无需 queryId) ==========

function normalizeDirectQueryColumns(columns?: string[]): string[] {
  if (!columns) return []
  const seen = new Set<string>()
  return columns.flatMap(column => {
    const trimmed = column.trim()
    if (!trimmed || trimmed === '_actions' || seen.has(trimmed)) return []
    seen.add(trimmed)
    return [trimmed]
  })
}

/**
 * 直连查询数据（无需提前创建 queryId）
 *
 * 适用于生成组件的标准用法：只需 qmModel + 分页/筛选/排序/列集合即可查询。
 * 现有 fetchQueryData(model, queryId, request) 保留给 DataViewer / SavedQuery 场景。
 */
export async function fetchQueryDataDirect(
  qmModel: string,
  request: ViewerQueryRequest
): Promise<ViewerDataResponse> {
  const columns = normalizeDirectQueryColumns(request.columns)
  if (columns.length === 0) {
    throw new Error(`${qmModel} direct query requires non-empty business columns`)
  }

  const response = await apiClient.post<any>(
    `/query/direct/${encodeURIComponent(qmModel)}`,
    { ...request, columns }
  )

  if (!response.data || response.data.code !== 200) {
    throw new Error(response.data?.msg || '查询数据失败')
  }

  return response.data.data
}

// ========== Frontend Meta v1 ==========

/**
 * 获取前端元数据契约 (frontend-meta v1)
 *
 * 返回面向前端渲染的标准元数据：fields 为有序数组、包含 memberLookup/category/uiHints。
 */
export async function fetchFrontendMeta(qmModel: string): Promise<FrontendMeta> {
  const response = await apiClient.get<any>(`/frontend-meta/${encodeURIComponent(qmModel)}`)

  if (!response.data || response.data.code !== 200) {
    throw new Error(response.data?.msg || '获取前端元数据失败')
  }

  return response.data.data
}

// ========== Member Query ==========

/**
 * 查询维度成员（远程搜索、分页、回填）
 *
 * 用于 SelectFilter 的 dimension 远程模式，替代旧的 fetchFilterOptions。
 * DSL slice 应使用返回的 selectionFieldName，而非 displayFieldName。
 */
export async function fetchMemberOptions(
  request: MemberQueryRequest
): Promise<MemberQueryResponse> {
  const response = await apiClient.post<any>('/members/query', request)

  if (!response.data || response.data.code !== 200) {
    throw new Error(response.data?.msg || '查询维度成员失败')
  }

  return response.data.data
}

/**
 * 错误处理
 */
apiClient.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 410) {
      return Promise.reject(new Error('查询链接已过期，请重新获取'))
    }
    if (error.response?.status === 404) {
      return Promise.reject(new Error('查询不存在'))
    }
    return Promise.reject(error)
  }
)
