import axios from 'axios'
import type {
  ColumnViewSetting,
  ListPresetDef,
  ListPresetVisibility,
  QueryConditionPreset
} from '@/types'

const apiClient = axios.create({
  baseURL: '/data-viewer/api/list-preset',
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

export interface ListPresetScope {
  userId: string
  model: string
  businessKey?: string
}

export interface SaveListPresetRequest {
  title: string
  description?: string
  columns: string[]
  columnSettings?: ColumnViewSetting[]
  query?: QueryConditionPreset
  pageSize?: number
  visibility?: ListPresetVisibility
  isDefault?: boolean
}

export type UpdateListPresetRequest = Partial<SaveListPresetRequest>

function assertApiResponse<T>(response: ApiResponse<T>, fallbackMessage: string): T {
  if (!response || response.code !== 200) {
    throw new Error(response?.msg || fallbackMessage)
  }
  return response.data
}

function buildScopeParams(scope: ListPresetScope): Record<string, string> {
  return scope.businessKey ? { businessKey: scope.businessKey } : {}
}

function userPath(userId: string): string {
  return `/users/${encodeURIComponent(userId)}`
}

function modelPath(scope: ListPresetScope): string {
  return `${userPath(scope.userId)}/models/${encodeURIComponent(scope.model)}`
}

function presetPath(userId: string, presetId: string): string {
  return `${userPath(userId)}/presets/${encodeURIComponent(presetId)}`
}

/**
 * 查询当前用户在指定 model/businessKey 下的自定义列表方案。
 */
export async function listPresets(scope: ListPresetScope): Promise<ListPresetDef[]> {
  const response = await apiClient.get<ApiResponse<ListPresetDef[]>>(modelPath(scope), {
    params: buildScopeParams(scope)
  })
  return assertApiResponse(response.data, '获取自定义列表失败')
}

/**
 * 查询当前用户在指定 model/businessKey 下的默认自定义列表。
 */
export async function getDefaultListPreset(scope: ListPresetScope): Promise<ListPresetDef | null> {
  const response = await apiClient.get<ApiResponse<ListPresetDef | null>>(`${modelPath(scope)}/default`, {
    params: buildScopeParams(scope)
  })
  return assertApiResponse(response.data, '获取默认自定义列表失败')
}

/**
 * 保存新的自定义列表方案。
 */
export async function createListPreset(
  scope: ListPresetScope,
  request: SaveListPresetRequest
): Promise<ListPresetDef> {
  const response = await apiClient.post<ApiResponse<ListPresetDef>>(modelPath(scope), request, {
    params: buildScopeParams(scope)
  })
  return assertApiResponse(response.data, '保存自定义列表失败')
}

/**
 * 获取单个自定义列表详情。
 */
export async function getListPreset(userId: string, presetId: string): Promise<ListPresetDef> {
  const response = await apiClient.get<ApiResponse<ListPresetDef>>(presetPath(userId, presetId))
  return assertApiResponse(response.data, '获取自定义列表详情失败')
}

/**
 * 更新自定义列表方案。
 */
export async function updateListPreset(
  userId: string,
  presetId: string,
  request: UpdateListPresetRequest
): Promise<ListPresetDef> {
  const response = await apiClient.put<ApiResponse<ListPresetDef>>(presetPath(userId, presetId), request)
  return assertApiResponse(response.data, '更新自定义列表失败')
}

/**
 * 删除自定义列表方案。
 */
export async function deleteListPreset(userId: string, presetId: string): Promise<void> {
  const response = await apiClient.delete<ApiResponse<void>>(presetPath(userId, presetId))
  assertApiResponse(response.data, '删除自定义列表失败')
}

/**
 * 将某个自定义列表设为默认方案。
 */
export async function setDefaultListPreset(userId: string, presetId: string): Promise<ListPresetDef> {
  const response = await apiClient.post<ApiResponse<ListPresetDef>>(`${presetPath(userId, presetId)}/default`)
  return assertApiResponse(response.data, '设置默认自定义列表失败')
}

/**
 * 清除当前 model/businessKey 下的默认方案。
 */
export async function clearDefaultListPreset(scope: ListPresetScope): Promise<void> {
  const response = await apiClient.delete<ApiResponse<void>>(`${modelPath(scope)}/default`, {
    params: buildScopeParams(scope)
  })
  assertApiResponse(response.data, '清除默认自定义列表失败')
}

apiClient.interceptors.response.use(
  response => response,
  error => {
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      return Promise.reject(new Error('自定义列表不存在'))
    }
    return Promise.reject(error)
  }
)
