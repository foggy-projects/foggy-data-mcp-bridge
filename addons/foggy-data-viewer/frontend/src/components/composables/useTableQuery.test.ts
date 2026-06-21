import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useTableQuery } from './useTableQuery'
import { globalQueryHooks } from './globalQueryHooks'
import type { FetchDataParams, FetchDataResult, QueryHookContext } from '@/types'

function createMockFetch(result?: Partial<FetchDataResult>) {
  return vi.fn<[FetchDataParams], Promise<FetchDataResult>>().mockResolvedValue({
    items: [{ id: 1, name: 'test' }],
    total: 1,
    ...result
  })
}

describe('useTableQuery', () => {
  beforeEach(() => {
    globalQueryHooks.clear()
  })

  describe('initial state', () => {
    it('should initialize with default values', () => {
      const query = useTableQuery(createMockFetch())

      expect(query.data.value).toEqual([])
      expect(query.total.value).toBe(0)
      expect(query.loading.value).toBe(false)
      expect(query.activeTrigger.value).toBeNull()
      expect(query.lastError.value).toBeNull()
      expect(query.serverSummary.value).toBeNull()
      expect(query.currentPage.value).toBe(1)
      expect(query.currentPageSize.value).toBe(50)
      expect(query.currentOrderBy.value).toEqual([])
      expect(query.currentSlice.value).toEqual([])
    })

    it('should use custom pageSize from options', () => {
      const query = useTableQuery(createMockFetch(), { pageSize: 20 })
      expect(query.currentPageSize.value).toBe(20)
    })
  })

  describe('loadData', () => {
    it('should call fetchData with current state', async () => {
      const mockFetch = createMockFetch()
      const query = useTableQuery(mockFetch)

      await query.loadData()

      expect(mockFetch).toHaveBeenCalledWith({
        page: 1,
        pageSize: 50,
        slice: [],
        orderBy: []
      })
    })

    it('should update data and total after fetch', async () => {
      const query = useTableQuery(createMockFetch({
        items: [{ id: 1 }, { id: 2 }],
        total: 100
      }))

      await query.loadData()

      expect(query.data.value).toEqual([{ id: 1 }, { id: 2 }])
      expect(query.total.value).toBe(100)
    })

    it('should update serverSummary from totalData', async () => {
      const query = useTableQuery(createMockFetch({
        totalData: { _count: 100, amount: 5000 }
      }))

      await query.loadData()

      expect(query.serverSummary.value).toEqual({ _count: 100, amount: 5000 })
    })

    it('should manage loading state correctly', async () => {
      let resolvePromise: (value: FetchDataResult) => void
      const mockFetch = vi.fn<[FetchDataParams], Promise<FetchDataResult>>().mockImplementation(
        () => new Promise(resolve => { resolvePromise = resolve })
      )

      const query = useTableQuery(mockFetch)

      // Verify loading becomes true during fetch by checking inside the fetch
      let loadingDuringFetch = false
      mockFetch.mockImplementation(() => {
        loadingDuringFetch = query.loading.value
        return new Promise(resolve => { resolvePromise = resolve })
      })

      const loadPromise = query.loadData()
      // Allow microtasks to run so loadData enters the fetch
      await new Promise(r => setTimeout(r, 0))

      expect(loadingDuringFetch).toBe(true)

      resolvePromise!({ items: [], total: 0 })
      await loadPromise

      expect(query.loading.value).toBe(false)
      expect(query.activeTrigger.value).toBeNull()
    })

    it('should set loading to false even on error', async () => {
      const mockFetch = vi.fn().mockRejectedValue(new Error('fail'))
      const query = useTableQuery(mockFetch)

      await expect(query.loadData()).rejects.toThrow('fail')
      expect(query.loading.value).toBe(false)
      expect(query.activeTrigger.value).toBeNull()
      expect(query.lastError.value?.message).toBe('fail')
    })

    it('should expose active trigger during fetch and clear it afterwards', async () => {
      let resolvePromise: (value: FetchDataResult) => void
      const mockFetch = vi.fn<[FetchDataParams], Promise<FetchDataResult>>().mockImplementation(
        () => new Promise(resolve => { resolvePromise = resolve })
      )
      const query = useTableQuery(mockFetch)

      const loadPromise = query.loadData('page')
      await new Promise(r => setTimeout(r, 0))

      expect(query.activeTrigger.value).toBe('page')

      resolvePromise!({ items: [], total: 0 })
      await loadPromise

      expect(query.activeTrigger.value).toBeNull()
    })

    it('should clear last error when a new fetch starts', async () => {
      let resolveSecond: (value: FetchDataResult) => void
      const mockFetch = vi.fn<[FetchDataParams], Promise<FetchDataResult>>()
        .mockRejectedValueOnce(new Error('first fail'))
        .mockImplementationOnce(() => new Promise(resolve => { resolveSecond = resolve }))
      const query = useTableQuery(mockFetch)

      await expect(query.loadData()).rejects.toThrow('first fail')
      expect(query.lastError.value?.message).toBe('first fail')

      const loadPromise = query.loadData('refresh')
      await new Promise(r => setTimeout(r, 0))

      expect(query.lastError.value).toBeNull()
      resolveSecond!({ items: [], total: 0 })
      await loadPromise
    })

    it('should pass trigger to hook context', async () => {
      const hookFn = vi.fn()
      const query = useTableQuery(createMockFetch(), {
        hooks: { onBeforeQuery: hookFn }
      })

      await query.loadData('sort')

      expect(hookFn).toHaveBeenCalledWith(
        expect.objectContaining({ trigger: 'sort' })
      )
    })

    it('should include current slice and orderBy in params', async () => {
      const mockFetch = createMockFetch()
      const query = useTableQuery(mockFetch)

      query.setSlice([{ field: 'name', op: 'like', value: 'test' }])
      query.setSort([{ field: 'id', order: 'desc' }])
      query.setPage(3, 25)

      await query.loadData()

      expect(mockFetch).toHaveBeenCalledWith({
        page: 3,
        pageSize: 25,
        slice: [{ field: 'name', op: 'like', value: 'test' }],
        orderBy: [{ field: 'id', order: 'desc' }]
      })
    })
  })

  describe('refresh / reload', () => {
    it('refresh should call loadData with trigger=refresh', async () => {
      const hookFn = vi.fn()
      const query = useTableQuery(createMockFetch(), {
        hooks: { onBeforeQuery: hookFn }
      })

      await query.refresh()

      expect(hookFn).toHaveBeenCalledWith(
        expect.objectContaining({ trigger: 'refresh' })
      )
    })

    it('reload should reset page to 1 and call loadData', async () => {
      const mockFetch = createMockFetch()
      const query = useTableQuery(mockFetch)

      query.setPage(5)
      await query.reload()

      expect(query.currentPage.value).toBe(1)
      expect(mockFetch).toHaveBeenCalledWith(
        expect.objectContaining({ page: 1 })
      )
    })
  })

  describe('setPage / setSort / setSlice', () => {
    it('setPage should update page and optionally pageSize', () => {
      const query = useTableQuery(createMockFetch())

      query.setPage(3)
      expect(query.currentPage.value).toBe(3)
      expect(query.currentPageSize.value).toBe(50)

      query.setPage(1, 100)
      expect(query.currentPage.value).toBe(1)
      expect(query.currentPageSize.value).toBe(100)
    })

    it('setSort should update orderBy', () => {
      const query = useTableQuery(createMockFetch())

      query.setSort([{ field: 'name', order: 'asc' }])
      expect(query.currentOrderBy.value).toEqual([{ field: 'name', order: 'asc' }])
    })

    it('setSlice should update slice', () => {
      const query = useTableQuery(createMockFetch())

      query.setSlice([{ field: 'status', op: '=', value: 'active' }])
      expect(query.currentSlice.value).toEqual([{ field: 'status', op: '=', value: 'active' }])
    })
  })

  describe('props hooks (via options)', () => {
    it('onBeforeQuery should be called before fetch', async () => {
      const order: string[] = []
      const mockFetch = vi.fn().mockImplementation(async () => {
        order.push('fetch')
        return { items: [], total: 0 }
      })

      const query = useTableQuery(mockFetch, {
        hooks: {
          onBeforeQuery: async () => { order.push('before') }
        }
      })

      await query.loadData()

      expect(order).toEqual(['before', 'fetch'])
    })

    it('onBeforeQuery returning false should cancel query', async () => {
      const mockFetch = createMockFetch()
      const query = useTableQuery(mockFetch, {
        hooks: {
          onBeforeQuery: async () => false
        }
      })

      await query.loadData()

      expect(mockFetch).not.toHaveBeenCalled()
      expect(query.loading.value).toBe(false)
    })

    it('onBeforeQuery returning params should replace them', async () => {
      const mockFetch = createMockFetch()
      const query = useTableQuery(mockFetch, {
        hooks: {
          onBeforeQuery: async () => ({
            page: 99,
            pageSize: 10,
            slice: [{ field: 'injected', op: '=', value: true }],
            orderBy: []
          })
        }
      })

      await query.loadData()

      expect(mockFetch).toHaveBeenCalledWith(
        expect.objectContaining({
          page: 99,
          pageSize: 10,
          slice: [{ field: 'injected', op: '=', value: true }]
        })
      )
    })

    it('onBeforeQuery can mutate params directly', async () => {
      const mockFetch = createMockFetch()
      const query = useTableQuery(mockFetch, {
        hooks: {
          onBeforeQuery: async (ctx) => {
            ctx.params.slice.push({ field: 'tenantId', op: '=', value: 't1' })
          }
        }
      })

      await query.loadData()

      expect(mockFetch).toHaveBeenCalledWith(
        expect.objectContaining({
          slice: [{ field: 'tenantId', op: '=', value: 't1' }]
        })
      )
    })

    it('onAfterQuery should transform result', async () => {
      const query = useTableQuery(
        createMockFetch({ items: [{ id: 1 }], total: 1 }),
        {
          hooks: {
            onAfterQuery: async (_ctx, result) => ({
              items: result.items.map(i => ({ ...i, transformed: true })),
              total: result.total
            })
          }
        }
      )

      await query.loadData()

      expect(query.data.value[0]).toEqual({ id: 1, transformed: true })
    })

    it('onQueryError returning true should suppress error', async () => {
      const errorHandler = vi.fn().mockResolvedValue(true)
      const query = useTableQuery(
        vi.fn().mockRejectedValue(new Error('fail')),
        { hooks: { onQueryError: errorHandler } }
      )

      // Should NOT throw
      await query.loadData()

      expect(errorHandler).toHaveBeenCalled()
      expect(query.loading.value).toBe(false)
    })
  })

  describe('instance hooks (runtime API)', () => {
    it('addHook should register and return dispose', async () => {
      const mockFetch = createMockFetch()
      const query = useTableQuery(mockFetch)
      const hook = vi.fn()

      const dispose = query.addHook('onBeforeQuery', hook)
      await query.loadData()
      expect(hook).toHaveBeenCalledTimes(1)

      dispose()
      await query.loadData()
      expect(hook).toHaveBeenCalledTimes(1) // not called again
    })

    it('removeHook should remove by reference', async () => {
      const query = useTableQuery(createMockFetch())
      const hook = vi.fn()

      query.addHook('onBeforeQuery', hook)
      await query.loadData()
      expect(hook).toHaveBeenCalledTimes(1)

      query.removeHook('onBeforeQuery', hook)
      await query.loadData()
      expect(hook).toHaveBeenCalledTimes(1)
    })

    it('instance onBeforeQuery can cancel query', async () => {
      const mockFetch = createMockFetch()
      const query = useTableQuery(mockFetch)

      query.addHook('onBeforeQuery', async () => false)
      await query.loadData()

      expect(mockFetch).not.toHaveBeenCalled()
    })

    it('instance onAfterQuery can transform result', async () => {
      const query = useTableQuery(
        createMockFetch({ items: [{ id: 1 }], total: 1 })
      )

      query.addHook('onAfterQuery', async (_ctx, result) => ({
        items: result.items.map(i => ({ ...i, added: true })),
        total: result.total
      }))

      await query.loadData()

      expect(query.data.value[0]).toEqual({ id: 1, added: true })
    })
  })

  describe('global hooks', () => {
    it('global onBeforeQuery should be called', async () => {
      const hook = vi.fn()
      globalQueryHooks.add('onBeforeQuery', hook)

      const query = useTableQuery(createMockFetch())
      await query.loadData()

      expect(hook).toHaveBeenCalled()
    })

    it('global onBeforeQuery returning false should cancel query', async () => {
      globalQueryHooks.add('onBeforeQuery', async () => false)
      const mockFetch = createMockFetch()

      const query = useTableQuery(mockFetch)
      await query.loadData()

      expect(mockFetch).not.toHaveBeenCalled()
    })

    it('global onAfterQuery should transform result', async () => {
      globalQueryHooks.add('onAfterQuery', async (_ctx, result) => ({
        items: result.items.map(i => ({ ...i, globalTag: true })),
        total: result.total
      }))

      const query = useTableQuery(
        createMockFetch({ items: [{ id: 1 }], total: 1 })
      )
      await query.loadData()

      expect(query.data.value[0]).toEqual({ id: 1, globalTag: true })
    })

    it('global onQueryError can handle errors', async () => {
      globalQueryHooks.add('onQueryError', async () => true)
      const query = useTableQuery(vi.fn().mockRejectedValue(new Error('fail')))

      // Should NOT throw
      await query.loadData()
      expect(query.loading.value).toBe(false)
    })
  })

  describe('hook execution order (onion model)', () => {
    it('before: global → props → instance', async () => {
      const order: string[] = []

      globalQueryHooks.add('onBeforeQuery', async () => { order.push('global') })

      const query = useTableQuery(createMockFetch(), {
        hooks: {
          onBeforeQuery: async () => { order.push('props') }
        }
      })

      query.addHook('onBeforeQuery', async () => { order.push('instance') })

      await query.loadData()

      expect(order).toEqual(['global', 'props', 'instance'])
    })

    it('after: instance → props → global', async () => {
      const order: string[] = []

      globalQueryHooks.add('onAfterQuery', async () => { order.push('global') })

      const query = useTableQuery(createMockFetch(), {
        hooks: {
          onAfterQuery: async () => { order.push('props') }
        }
      })

      query.addHook('onAfterQuery', async () => { order.push('instance') })

      await query.loadData()

      expect(order).toEqual(['instance', 'props', 'global'])
    })

    it('error: instance → props → global', async () => {
      const order: string[] = []

      globalQueryHooks.add('onQueryError', async () => { order.push('global'); return true })

      const query = useTableQuery(vi.fn().mockRejectedValue(new Error('fail')), {
        hooks: {
          onQueryError: async () => { order.push('props') }
        }
      })

      query.addHook('onQueryError', async () => { order.push('instance') })

      await query.loadData()

      expect(order).toEqual(['instance', 'props', 'global'])
    })

    it('global cancel should prevent props and instance before hooks', async () => {
      const propsHook = vi.fn()
      const instanceHook = vi.fn()

      globalQueryHooks.add('onBeforeQuery', async () => false)

      const query = useTableQuery(createMockFetch(), {
        hooks: { onBeforeQuery: propsHook }
      })
      query.addHook('onBeforeQuery', instanceHook)

      await query.loadData()

      expect(propsHook).not.toHaveBeenCalled()
      expect(instanceHook).not.toHaveBeenCalled()
    })

    it('props cancel should prevent instance before hooks', async () => {
      const instanceHook = vi.fn()

      const query = useTableQuery(createMockFetch(), {
        hooks: { onBeforeQuery: async () => false }
      })
      query.addHook('onBeforeQuery', instanceHook)

      await query.loadData()

      expect(instanceHook).not.toHaveBeenCalled()
    })

    it('after hooks should chain result transformations across layers', async () => {
      globalQueryHooks.add('onAfterQuery', async (_ctx, result) => ({
        items: result.items.map(i => ({ ...i, global: true })),
        total: result.total
      }))

      const query = useTableQuery(
        createMockFetch({ items: [{ id: 1 }], total: 1 }),
        {
          hooks: {
            onAfterQuery: async (_ctx, result) => ({
              items: result.items.map(i => ({ ...i, props: true })),
              total: result.total
            })
          }
        }
      )

      query.addHook('onAfterQuery', async (_ctx, result) => ({
        items: result.items.map(i => ({ ...i, instance: true })),
        total: result.total
      }))

      await query.loadData()

      // instance → props → global, each builds on the previous
      expect(query.data.value[0]).toEqual({
        id: 1,
        instance: true,
        props: true,
        global: true
      })
    })
  })

  describe('error handling', () => {
    it('should throw when no error hook handles it', async () => {
      const query = useTableQuery(vi.fn().mockRejectedValue(new Error('unhandled')))

      await expect(query.loadData()).rejects.toThrow('unhandled')
    })

    it('should not throw when any layer handles the error', async () => {
      // instance handles it
      const query = useTableQuery(vi.fn().mockRejectedValue(new Error('fail')))
      query.addHook('onQueryError', async () => true)

      await query.loadData() // no throw
    })

    it('should convert non-Error throws to Error', async () => {
      const errorHook = vi.fn().mockResolvedValue(true)
      const query = useTableQuery(
        vi.fn().mockRejectedValue('string error'),
        { hooks: { onQueryError: errorHook } }
      )

      await query.loadData()

      expect(errorHook).toHaveBeenCalledWith(
        expect.anything(),
        expect.objectContaining({ message: 'string error' })
      )
    })
  })
})
