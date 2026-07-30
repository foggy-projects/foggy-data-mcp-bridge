import axios, {
  AxiosError,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig
} from 'axios'
import {
  clearConsoleSession,
  readDataAuthorization,
  readNamespace,
  readRuntimeToken
} from './storage'

export interface RuntimeErrorBody {
  code?: string
  phase?: string
  message?: string
  model?: string
  field?: string
  path?: string
  suggestedNextAction?: string
  safeToAutoRepair?: boolean
}

export interface RuntimeEnvelope<T> {
  success: boolean
  engine?: string
  runtimeApiVersion?: string
  data?: T
  error?: RuntimeErrorBody
  diagnostics?: Record<string, unknown>
}

export class RuntimeRequestError extends Error {
  readonly code: string
  readonly phase?: string
  readonly suggestedNextAction?: string
  readonly status?: number
  readonly diagnostics?: Record<string, unknown>

  constructor(
    message: string,
    options: {
      code?: string
      phase?: string
      suggestedNextAction?: string
      status?: number
      diagnostics?: Record<string, unknown>
    } = {}
  ) {
    super(message)
    this.name = 'RuntimeRequestError'
    this.code = options.code || 'RUNTIME_REQUEST_FAILED'
    this.phase = options.phase
    this.suggestedNextAction = options.suggestedNextAction
    this.status = options.status
    this.diagnostics = options.diagnostics
  }
}

export function runtimeApiBase(pathname = window.location.pathname): string {
  const marker = '/console'
  const markerIndex = pathname.indexOf(marker)
  const contextPath = markerIndex >= 0 ? pathname.slice(0, markerIndex) : ''
  return `${contextPath}/api/v1/`
}

const client = axios.create({
  baseURL: runtimeApiBase(),
  timeout: 30_000,
  headers: {
    Accept: 'application/json'
  }
})

function isSafeRelativeRequest(config: InternalAxiosRequestConfig): boolean {
  const requestUrl = config.url || ''
  if (/^[a-z][a-z\d+\-.]*:/i.test(requestUrl) || requestUrl.startsWith('//')) {
    return false
  }
  const resolved = new URL(requestUrl, new URL(config.baseURL || runtimeApiBase(), window.location.origin))
  return resolved.origin === window.location.origin
}

client.interceptors.request.use(config => {
  if (!isSafeRelativeRequest(config)) {
    throw new RuntimeRequestError('Console 拒绝向非同源地址发送 Runtime 请求。', {
      code: 'CROSS_ORIGIN_RUNTIME_REQUEST_BLOCKED'
    })
  }

  const token = readRuntimeToken()
  if (token && !config.headers['X-Foggy-Runtime-Code']) {
    config.headers['X-Foggy-Runtime-Code'] = token
  }

  const namespace = readNamespace()
  if (namespace && !config.headers['X-NS']) {
    config.headers['X-NS'] = namespace
  }

  const authorization = readDataAuthorization()
  if (authorization && !config.headers.Authorization) {
    config.headers.Authorization = authorization
  }
  return config
})

client.interceptors.response.use(
  response => response,
  (error: AxiosError<RuntimeEnvelope<unknown>>) => {
    const code = error.response?.data?.error?.code
    if (error.response?.status === 401 && code === 'RUNTIME_AUTH_REQUIRED') {
      clearConsoleSession()
      window.dispatchEvent(new CustomEvent('foggy:runtime-auth-required'))
    }
    return Promise.reject(error)
  }
)

function toRuntimeError(error: unknown): RuntimeRequestError {
  if (error instanceof RuntimeRequestError) {
    return error
  }
  if (axios.isAxiosError<RuntimeEnvelope<unknown>>(error)) {
    const body = error.response?.data
    return new RuntimeRequestError(
      body?.error?.message || (error.code === 'ECONNABORTED'
        ? 'Runtime 请求超时，请检查服务状态后重试。'
        : '无法完成 Runtime 请求。'),
      {
        code: body?.error?.code || error.code || 'RUNTIME_REQUEST_FAILED',
        phase: body?.error?.phase,
        suggestedNextAction: body?.error?.suggestedNextAction,
        status: error.response?.status,
        diagnostics: body?.diagnostics
      }
    )
  }
  return new RuntimeRequestError(error instanceof Error ? error.message : '未知 Runtime 请求错误。')
}

export async function runtimeRequest<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await client.request<RuntimeEnvelope<T>>(config)
    const envelope = response.data
    if (!envelope.success) {
      throw new RuntimeRequestError(envelope.error?.message || 'Runtime 返回失败状态。', {
        code: envelope.error?.code,
        phase: envelope.error?.phase,
        suggestedNextAction: envelope.error?.suggestedNextAction,
        status: response.status,
        diagnostics: envelope.diagnostics
      })
    }
    return envelope.data as T
  } catch (error) {
    throw toRuntimeError(error)
  }
}

export async function checkAccess(candidateToken: string): Promise<AccessCheck> {
  return runtimeRequest<AccessCheck>({
    method: 'GET',
    url: 'access/check',
    headers: {
      'X-Foggy-Runtime-Code': candidateToken
    }
  })
}

export interface AccessCheck {
  authenticated: boolean
  authScope: string
  runtimeApiVersion: string
}

export const runtimeApi = {
  get<T>(url: string, params?: Record<string, unknown>) {
    return runtimeRequest<T>({ method: 'GET', url, params })
  },
  post<T>(url: string, data?: unknown) {
    return runtimeRequest<T>({ method: 'POST', url, data })
  },
  put<T>(url: string, data?: unknown) {
    return runtimeRequest<T>({ method: 'PUT', url, data })
  },
  delete<T>(url: string) {
    return runtimeRequest<T>({ method: 'DELETE', url })
  }
}
