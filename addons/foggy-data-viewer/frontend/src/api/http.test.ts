import axios, { AxiosError } from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { configureDataViewerHttp } from './http'
import type { DataViewerHttpRequestOptions } from './http'

let viewer: typeof import('./viewer')
let presets: typeof import('./listPreset')
let defaults: typeof import('./tableDefaultQueryConfig')
const requests: InternalAxiosRequestConfig[] = []
let status = 200
let originalError: AxiosError
const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
  requests.push(config)
  const response = { data: { code: 200, data: { fields: {} } }, status, statusText: '', headers: {}, config }
  if (status !== 200) {
    originalError = new AxiosError('Request failed', undefined, config, undefined, response)
    throw originalError
  }
  return response
})
const previousAdapter = axios.defaults.adapter
beforeAll(async () => {
  axios.defaults.adapter = adapter
  // All clients are created BEFORE any host configuration.
  viewer = await import('./viewer')
  presets = await import('./listPreset')
  defaults = await import('./tableDefaultQueryConfig')
  axios.defaults.adapter = previousAdapter
})
beforeEach(() => { configureDataViewerHttp({}); status = 200; requests.length = 0 })
afterEach(() => configureDataViewerHttp({}))

const scope = { userId: 'u', model: 'Orders' }
const query = { columns: ['id'], start: 0, limit: 10 }
function calls(options?: DataViewerHttpRequestOptions) {
  return [
    () => viewer.createQuery({ model: 'Orders', payload: { columns: ['id'], slice: [] } }, options),
    () => viewer.fetchQueryMeta('Orders', 'q', options),
    () => viewer.fetchQueryData('Orders', 'q', query, options),
    () => viewer.fetchQueryDataDirect('Orders', query, options),
    () => viewer.fetchFilterOptions('Orders', 'q', 'id', options),
    () => viewer.fetchQmSchema('Orders', options),
    () => viewer.fetchFrontendMeta('Orders', options),
    () => viewer.fetchMemberOptions({} as Parameters<typeof viewer.fetchMemberOptions>[0], options),
    () => presets.listPresets(scope, options),
    () => presets.getDefaultListPreset(scope, options),
    () => presets.createListPreset(scope, { title: 'x', columns: ['id'] }, options),
    () => presets.getListPreset('u', 'p', options),
    () => presets.updateListPreset('u', 'p', { title: 'y' }, options),
    () => presets.deleteListPreset('u', 'p', options),
    () => presets.setDefaultListPreset('u', 'p', options),
    () => presets.clearDefaultListPreset(scope, options),
    () => defaults.getTableDefaultQueryConfig({ queryModel: 'Orders' }, options)
  ]
}

describe('SDK HTTP configuration', () => {
  it('applies current token to all existing clients and retains request namespace', async () => {
    let token: string | undefined = 'A'
    configureDataViewerHttp({ getHeaders: () => token ? { Authorization: `Bearer ${token}` } : {} })
    for (const next of ['A', 'B', undefined]) {
      token = next
      for (const call of calls({ headers: { 'X-NS': 'tms-biz' } })) await call()
      for (const request of requests.splice(0)) {
        expect(request.headers.get('Authorization')).toBe(next ? `Bearer ${next}` : undefined)
        expect(request.headers.get('X-NS')).toBe('tms-biz')
        expect(request.headers.get('Content-Type')).toBe('application/json')
      }
    }
  })
  it('merges case-insensitively with request precedence and explicit deletion', async () => {
    configureDataViewerHttp({ getHeaders: async () => ({ authorization: 'global', 'X-NS': 'global' }) })
    await viewer.fetchFrontendMeta('Orders', { headers: { Authorization: 'request', 'x-ns': 'local' } })
    expect(requests[0].headers.get('Authorization')).toBe('request')
    expect(requests[0].headers.get('X-NS')).toBe('local')
    await viewer.fetchFrontendMeta('Orders', { headers: { Authorization: null } })
    expect(requests[1].headers.has('Authorization')).toBe(false)
  })
  it('preserves unconfigured behavior and resets configuration', async () => {
    await viewer.fetchFrontendMeta('Orders')
    expect(requests[0].headers.has('Authorization')).toBe(false)
    configureDataViewerHttp({ getHeaders: () => ({ Authorization: 'A' }) })
    configureDataViewerHttp({})
    await viewer.fetchFrontendMeta('Orders')
    expect(requests[1].headers.has('Authorization')).toBe(false)
  })
  it('notifies once per 401 across every API and preserves the original rejection', async () => {
    status = 401
    const oldCallback = vi.fn()
    const callback = vi.fn()
    configureDataViewerHttp({ onUnauthorized: oldCallback })
    configureDataViewerHttp({ getHeaders: () => ({ Authorization: 'secret' }), onUnauthorized: callback })
    for (const call of calls()) {
      const error = await call().catch(e => e)
      expect(error).toBe(originalError)
    }
    expect(oldCallback).not.toHaveBeenCalled()
    expect(callback).toHaveBeenCalledTimes(calls().length)
    expect(callback.mock.calls[0][0]).toEqual({ status: 401, method: 'POST', path: '/data-viewer/api/query/create' })
    expect(JSON.stringify(callback.mock.calls)).not.toContain('secret')
  })
  it.each(['sync', 'async'])('isolates %s callback failures', async kind => {
    status = 401
    configureDataViewerHttp({ onUnauthorized: () => {
      if (kind === 'sync') throw new Error('callback')
      return Promise.reject(new Error('callback'))
    } })
    expect(await viewer.fetchFrontendMeta('Orders').catch(e => e)).toBe(originalError)
  })
  it('rejects header provider failure before sending a request', async () => {
    const error = new Error('header provider')
    configureDataViewerHttp({ getHeaders: () => { throw error } })
    await expect(viewer.fetchFrontendMeta('Orders')).rejects.toBe(error)
    expect(requests).toHaveLength(0)
  })
  it('keeps existing 404/410 mappings and does not notify for them', async () => {
    const callback = vi.fn()
    configureDataViewerHttp({ onUnauthorized: callback })
    status = 410
    await expect(viewer.fetchFrontendMeta('Orders')).rejects.toThrow('查询链接已过期')
    status = 404
    await expect(presets.getListPreset('u', 'p')).rejects.toThrow('自定义列表不存在')
    expect(callback).not.toHaveBeenCalled()
  })
})
