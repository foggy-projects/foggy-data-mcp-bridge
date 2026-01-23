import axios from 'axios'
import type { QueryMetaResponse, ViewerQueryRequest, ViewerDataResponse, FilterOptionsResponse, ColumnSchema } from '@/types'

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
  slice: Array<{ field: string; op: string; value?: unknown }>
  groupBy?: Array<{ field: string; agg?: string }>
  orderBy?: Array<{ field: string; order: 'asc' | 'desc' }>
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
export async function fetchQueryMeta(queryId: string): Promise<QueryMetaResponse> {
  const response = await apiClient.get<any>(`/query/${queryId}/meta`)

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
  queryId: string,
  request: ViewerQueryRequest
): Promise<ViewerDataResponse> {
  const response = await apiClient.post<any>(`/query/${queryId}/data`, request)

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
  queryId: string,
  columnName: string
): Promise<FilterOptionsResponse> {
  const response = await apiClient.get<FilterOptionsResponse>(
    `/query/${queryId}/filter-options/${encodeURIComponent(columnName)}`
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

  // 解析 SemanticMetadataResponse 返回的 JSON 结构
  // 返回格式类似：{ "models": [{ "name": "XXX", "fields": [...] }] }
  if (data && data.models && data.models[0]) {
    const fields = data.models[0].fields || []
    // 将 field 转换为 ColumnSchema 格式
    return fields.map((field: any) => ({
      name: field.name,
      type: field.jdbcType || field.type || 'TEXT',
      title: field.caption || field.name
    }))
  }

  return []
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
