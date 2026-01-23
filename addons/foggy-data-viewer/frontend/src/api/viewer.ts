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

  // 解析 SemanticMetadataResponse V3 格式
  // 返回格式：{ "version": "v3", "fields": { "fieldName": { "name": "显示名", "meta": "..." } }, "models": {...} }
  if (!data || !data.fields) {
    return []
  }

  // 遍历 fields 对象，转换为 ColumnSchema 数组
  const columns: ColumnSchema[] = []
  for (const [fieldName, fieldInfo] of Object.entries(data.fields)) {
    const field = fieldInfo as any

    // 解析 meta 字段获取类型信息
    // meta 格式: "维度ID | 数值/文本 | ..."、"度量 | 数值 | ..."、"属性 | 文本"
    const meta = field.meta || ''
    const metaParts = meta.split('|').map((s: string) => s.trim())

    let type = 'TEXT'
    let filterType: string | undefined
    let measure = false

    // 从 meta 第二部分提取类型
    if (metaParts.length >= 2) {
      const dataType = metaParts[1].toLowerCase()
      if (dataType.includes('金额')) {
        type = 'MONEY'
      } else if (dataType.includes('数值') || dataType.includes('数字')) {
        type = 'NUMBER'
      } else if (dataType.includes('日期')) {
        type = 'DATE'
      } else if (dataType.includes('时间')) {
        type = 'DATETIME'
      } else {
        type = 'TEXT'
      }
    }

    // 从 meta 第一部分判断字段类别
    if (metaParts.length >= 1) {
      const category = metaParts[0].toLowerCase()
      if (category.includes('度量')) {
        measure = true
        filterType = 'number'
      } else if (category.includes('维度id')) {
        filterType = 'dimension'
      } else if (category.includes('维度名称') || category.includes('维度属性')) {
        filterType = 'dimension'
      }
    }

    columns.push({
      name: fieldName,
      title: field.name || fieldName,
      type: type,
      filterable: true,
      aggregatable: measure,
      measure: measure,
      filterType: filterType as any
    })
  }

  return columns
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
