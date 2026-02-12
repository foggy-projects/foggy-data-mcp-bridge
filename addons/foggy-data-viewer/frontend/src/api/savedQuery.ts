import axios from 'axios'
import type { SliceRequestDef, OrderRequestDef } from '@/types'

const apiClient = axios.create({
  baseURL: '/data-viewer/api/saved-query',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

/**
 * 查询可见性
 */
export type QueryVisibility = 'PRIVATE' | 'DEPARTMENT' | 'TENANT'

/**
 * 保存的查询定义
 */
export interface SavedQueryDef {
  id: string
  businessId?: string  // 业务ID（可选，用于隔离不同业务的查询）
  model: string
  title: string
  description?: string
  columns: string[]
  slice: SliceRequestDef[]
  orderBy: OrderRequestDef[]
  groupBy?: Array<{ field: string; agg?: string }>
  calculatedFields?: Array<{ name: string; expression: string; agg?: string }>
  visibility: QueryVisibility
  ownerId: string
  ownerDeptId?: string
  ownerTenantId?: string
  createdAt: string
  updatedAt: string
}

/**
 * 保存查询请求
 */
export interface SaveQueryRequest {
  businessId?: string  // 业务ID（可选）
  model: string
  title: string
  description?: string
  columns: string[]
  slice?: SliceRequestDef[]
  orderBy?: OrderRequestDef[]
  groupBy?: Array<{ field: string; agg?: string }>
  calculatedFields?: Array<{ name: string; expression: string; agg?: string }>
  visibility?: QueryVisibility
}

/**
 * 保存查询
 */
export async function saveQuery(request: SaveQueryRequest): Promise<SavedQueryDef> {
  const response = await apiClient.post<any>('', request)
  if (!response.data || response.data.code !== 200) {
    throw new Error(response.data?.msg || '保存查询失败')
  }
  return response.data.data
}

/**
 * 列出可见查询
 */
export async function listSavedQueries(model: string, businessId?: string): Promise<SavedQueryDef[]> {
  const params: any = {}
  if (businessId) params.businessId = businessId

  const response = await apiClient.get<any>(`/list/${encodeURIComponent(model)}`, { params })
  if (!response.data || response.data.code !== 200) {
    throw new Error(response.data?.msg || '获取查询列表失败')
  }
  return response.data.data
}

/**
 * 获取单个查询详情
 */
export async function getSavedQuery(id: string): Promise<SavedQueryDef> {
  const response = await apiClient.get<any>(`/${id}`)
  if (!response.data || response.data.code !== 200) {
    throw new Error(response.data?.msg || '获取查询详情失败')
  }
  return response.data.data
}

/**
 * 更新查询（仅 owner）
 */
export async function updateSavedQuery(id: string, request: SaveQueryRequest): Promise<SavedQueryDef> {
  const response = await apiClient.put<any>(`/${id}`, request)
  if (!response.data || response.data.code !== 200) {
    throw new Error(response.data?.msg || '更新查询失败')
  }
  return response.data.data
}

/**
 * 删除查询（仅 owner）
 */
export async function deleteSavedQuery(id: string): Promise<void> {
  const response = await apiClient.delete<any>(`/${id}`)
  if (!response.data || response.data.code !== 200) {
    throw new Error(response.data?.msg || '删除查询失败')
  }
}

/**
 * 应用保存的查询（创建临时 cached_query 并返回 queryId）
 */
export async function applySavedQuery(id: string): Promise<{ queryId: string }> {
  const response = await apiClient.post<any>(`/${id}/apply`)
  if (!response.data || response.data.code !== 200) {
    throw new Error(response.data?.msg || '应用查询失败')
  }
  return response.data.data
}

/**
 * 错误处理
 */
apiClient.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 503) {
      return Promise.reject(new Error('保存查询功能未启用，请配置 SecurityIdentityResolver'))
    }
    if (error.response?.status === 404) {
      return Promise.reject(new Error('查询不存在'))
    }
    return Promise.reject(error)
  }
)
