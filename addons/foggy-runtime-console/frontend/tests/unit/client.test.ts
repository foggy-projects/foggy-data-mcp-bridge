import axios, { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { runtimeApiBase, runtimeRequest } from '@/api/client'
import { readRuntimeToken, writeRuntimeToken } from '@/api/storage'

function rejectingAdapter(status: number, code: string) {
  return (config: InternalAxiosRequestConfig) => Promise.reject(new AxiosError(
    'request failed',
    AxiosError.ERR_BAD_REQUEST,
    config,
    undefined,
    {
      data: {
        success: false,
        error: { code, message: 'rejected' }
      },
      status,
      statusText: 'Rejected',
      headers: {},
      config: {
        ...config,
        headers: config.headers || new AxiosHeaders()
      }
    }
  ))
}

describe('Runtime API client boundary', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.restoreAllMocks()
  })

  it('derives the API base from the servlet context before /console', () => {
    expect(runtimeApiBase('/console/')).toBe('/api/v1/')
    expect(runtimeApiBase('/foggy/console/assets/app.js')).toBe('/foggy/api/v1/')
  })

  it('blocks absolute cross-origin requests before an adapter can run', async () => {
    const adapter = vi.fn()
    await expect(runtimeRequest({
      url: 'https://outside.invalid/api/v1/capabilities',
      adapter
    })).rejects.toMatchObject({ code: 'CROSS_ORIGIN_RUNTIME_REQUEST_BLOCKED' })
    expect(adapter).not.toHaveBeenCalled()
  })

  it('clears the session only for 401 RUNTIME_AUTH_REQUIRED', async () => {
    writeRuntimeToken('keep-unless-auth-required')
    await expect(runtimeRequest({
      url: 'capabilities',
      adapter: rejectingAdapter(403, 'MODEL_PERMISSION_DENIED')
    })).rejects.toMatchObject({ status: 403 })
    expect(readRuntimeToken()).toBe('keep-unless-auth-required')

    await expect(runtimeRequest({
      url: 'capabilities',
      adapter: rejectingAdapter(401, 'RUNTIME_AUTH_REQUIRED')
    })).rejects.toMatchObject({ status: 401 })
    expect(readRuntimeToken()).toBeNull()
  })
})
