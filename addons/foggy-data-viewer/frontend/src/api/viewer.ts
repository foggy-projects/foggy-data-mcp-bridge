import axios from 'axios'
import type { QueryMetaResponse, ViewerQueryRequest, ViewerDataResponse } from '@/types'

const apiClient = axios.create({
  baseURL: '/data-viewer/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

/**
 * 获取查询元数据
 */
export async function fetchQueryMeta(queryId: string): Promise<QueryMetaResponse> {
  const response = await apiClient.get<QueryMetaResponse>(`/query/${queryId}/meta`)
  return response.data
}

/**
 * 查询数据
 */
export async function fetchQueryData(
  queryId: string,
  request: ViewerQueryRequest
): Promise<ViewerDataResponse> {
  const response = await apiClient.post<ViewerDataResponse>(`/query/${queryId}/data`, request)
  return response.data
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
