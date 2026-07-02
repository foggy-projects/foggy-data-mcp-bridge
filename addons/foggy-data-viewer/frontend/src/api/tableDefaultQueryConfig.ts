import axios from 'axios'
import type { TableDefaultQueryConfig, TableDefaultQueryConfigScope } from '@/types'

const apiClient = axios.create({
  baseURL: '/data-viewer/api/table-defaults',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

interface ApiResponse<T> {
  code: number
  msg?: string
  data: T
}

function assertApiResponse<T>(response: ApiResponse<T>, fallbackMessage: string): T {
  if (!response || response.code !== 200) {
    throw new Error(response?.msg || fallbackMessage)
  }
  return response.data
}

function buildParams(scope: TableDefaultQueryConfigScope): Record<string, string | boolean> {
  const params: Record<string, string | boolean> = {
    queryModel: scope.queryModel
  }

  if (scope.tableInstanceId) params.tableInstanceId = scope.tableInstanceId
  if (scope.userId) params.userId = scope.userId
  if (scope.tenantId) params.tenantId = scope.tenantId
  if (scope.roleIds && scope.roleIds.length > 0) params.roleIds = scope.roleIds.join(',')
  if (scope.includeFallback !== undefined) params.includeFallback = scope.includeFallback

  return params
}

export async function getTableDefaultQueryConfig(
  scope: TableDefaultQueryConfigScope
): Promise<TableDefaultQueryConfig | null> {
  const response = await apiClient.get<ApiResponse<TableDefaultQueryConfig | null>>('/default', {
    params: buildParams(scope)
  })
  return assertApiResponse(response.data, '获取表格默认查询配置失败')
}

