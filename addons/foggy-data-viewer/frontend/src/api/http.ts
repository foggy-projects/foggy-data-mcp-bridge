import axios, { AxiosHeaders } from 'axios'
import type { AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'

export type DataViewerHttpHeaders = Record<string, string | null | undefined>
export interface DataViewerHttpRequestOptions {
  headers?: DataViewerHttpHeaders
}
export interface DataViewerUnauthorizedContext {
  status: 401
  method: string
  /** Path only: excludes credentials, query, fragment, headers and body. */
  path: string
}
export interface DataViewerHttpConfig {
  getHeaders?: () => DataViewerHttpHeaders | Promise<DataViewerHttpHeaders>
  onUnauthorized?: (context: DataViewerUnauthorizedContext) => void | Promise<void>
}

let configuration: DataViewerHttpConfig = {}

/** Replace configuration for all SDK clients, including already-created clients. */
export function configureDataViewerHttp(config: DataViewerHttpConfig): void {
  configuration = { ...config }
}

type RequestConfig = InternalAxiosRequestConfig & {
  viewerRequestHeaders?: DataViewerHttpHeaders
}

export function httpOptions(options?: DataViewerHttpRequestOptions): AxiosRequestConfig {
  return { viewerRequestHeaders: options?.headers } as AxiosRequestConfig
}

function mergeHeaders(target: AxiosHeaders, source?: DataViewerHttpHeaders): void {
  for (const [name, value] of Object.entries(source ?? {})) {
    if (value === null) target.delete(name)
    else if (value !== undefined) target.set(name, value, true)
  }
}

function requestPath(config?: AxiosRequestConfig): string {
  try {
    const url = config?.url ?? ''
    const combined = /^https?:\/\//i.test(url) ? url : `${config?.baseURL ?? ''}/${url.replace(/^\//, '')}`
    return new URL(combined, 'http://sdk.invalid').pathname
  } catch {
    return ''
  }
}

/** Internal factory; response unwrapping remains owned by each existing API. */
export function createDataViewerHttpClient(baseURL: string) {
  const client = axios.create({
    baseURL,
    timeout: 30000,
    headers: { 'Content-Type': 'application/json' }
  })
  client.interceptors.request.use(async (request: RequestConfig) => {
    const headers = new AxiosHeaders(request.headers)
    mergeHeaders(headers, await configuration.getHeaders?.())
    mergeHeaders(headers, request.viewerRequestHeaders)
    request.headers = headers
    delete request.viewerRequestHeaders
    return request
  })
  client.interceptors.response.use(response => response, error => {
    if (error.response?.status === 401) {
      try {
        // Notifications never delay or replace rejection of the original request.
        Promise.resolve(configuration.onUnauthorized?.({
          status: 401,
          method: String(error.config?.method ?? 'GET').toUpperCase(),
          path: requestPath(error.config)
        })).catch(() => {})
      } catch {
        // Host callback failures must not mask the original HTTP failure.
      }
    }
    return Promise.reject(error)
  })
  return client
}
