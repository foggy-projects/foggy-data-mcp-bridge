import { describe, it, expect, vi } from 'vitest'
import { HookRegistry } from './hookRegistry'
import type { QueryHookContext, FetchDataResult } from '@/types'

function createCtx(overrides?: Partial<QueryHookContext>): QueryHookContext {
  return {
    params: { page: 1, pageSize: 50, slice: [], orderBy: [] },
    trigger: 'refresh',
    ...overrides
  }
}

describe('HookRegistry', () => {
  describe('add / remove / size / clear', () => {
    it('should register a hook and return dispose function', () => {
      const reg = new HookRegistry()
      const fn = vi.fn()

      const dispose = reg.add('onBeforeQuery', fn)

      expect(reg.size('onBeforeQuery')).toBe(1)
      dispose()
      expect(reg.size('onBeforeQuery')).toBe(0)
    })

    it('should remove a hook by reference', () => {
      const reg = new HookRegistry()
      const fn = vi.fn()

      reg.add('onBeforeQuery', fn)
      expect(reg.size('onBeforeQuery')).toBe(1)

      reg.remove('onBeforeQuery', fn)
      expect(reg.size('onBeforeQuery')).toBe(0)
    })

    it('should handle removing non-existent hook gracefully', () => {
      const reg = new HookRegistry()
      expect(() => reg.remove('onBeforeQuery', vi.fn())).not.toThrow()
    })

    it('should support multiple hooks on same name', () => {
      const reg = new HookRegistry()
      reg.add('onBeforeQuery', vi.fn())
      reg.add('onBeforeQuery', vi.fn())
      expect(reg.size('onBeforeQuery')).toBe(2)
    })

    it('should clear all hooks', () => {
      const reg = new HookRegistry()
      reg.add('onBeforeQuery', vi.fn())
      reg.add('onAfterQuery', vi.fn())
      reg.add('onQueryError', vi.fn())

      reg.clear()

      expect(reg.size('onBeforeQuery')).toBe(0)
      expect(reg.size('onAfterQuery')).toBe(0)
      expect(reg.size('onQueryError')).toBe(0)
    })

    it('should return 0 for unregistered hook names', () => {
      const reg = new HookRegistry()
      expect(reg.size('onBeforeQuery')).toBe(0)
    })
  })

  describe('runBefore', () => {
    it('should execute hooks in order', async () => {
      const reg = new HookRegistry()
      const order: number[] = []

      reg.add('onBeforeQuery', async () => { order.push(1) })
      reg.add('onBeforeQuery', async () => { order.push(2) })

      const ctx = createCtx()
      await reg.runBefore(ctx)

      expect(order).toEqual([1, 2])
    })

    it('should cancel when hook returns false', async () => {
      const reg = new HookRegistry()
      const secondHook = vi.fn()

      reg.add('onBeforeQuery', async () => false)
      reg.add('onBeforeQuery', secondHook)

      const result = await reg.runBefore(createCtx())

      expect(result).toBe(false)
      expect(secondHook).not.toHaveBeenCalled()
    })

    it('should replace params when hook returns FetchDataParams', async () => {
      const reg = new HookRegistry()
      const newParams = { page: 2, pageSize: 100, slice: [], orderBy: [] }

      reg.add('onBeforeQuery', async () => newParams)

      const ctx = createCtx()
      await reg.runBefore(ctx)

      expect(ctx.params).toEqual(newParams)
    })

    it('should allow hook to mutate ctx.params directly', async () => {
      const reg = new HookRegistry()

      reg.add('onBeforeQuery', async (ctx) => {
        ctx.params.slice.push({ field: 'tenantId', op: '=', value: 'abc' })
      })

      const ctx = createCtx()
      await reg.runBefore(ctx)

      expect(ctx.params.slice).toHaveLength(1)
      expect(ctx.params.slice[0].field).toBe('tenantId')
    })

    it('should return void when no hooks registered', async () => {
      const reg = new HookRegistry()
      const result = await reg.runBefore(createCtx())
      expect(result).toBeUndefined()
    })

    it('should support synchronous hooks', async () => {
      const reg = new HookRegistry()
      reg.add('onBeforeQuery', () => { /* sync noop */ })

      const result = await reg.runBefore(createCtx())
      expect(result).toBeUndefined()
    })
  })

  describe('runAfter', () => {
    const baseResult: FetchDataResult = {
      items: [{ id: 1 }],
      total: 1
    }

    it('should pass result through when hooks return void', async () => {
      const reg = new HookRegistry()
      reg.add('onAfterQuery', vi.fn())

      const result = await reg.runAfter(createCtx(), baseResult)
      expect(result).toEqual(baseResult)
    })

    it('should replace result when hook returns FetchDataResult', async () => {
      const reg = new HookRegistry()
      const newResult: FetchDataResult = { items: [{ id: 2 }], total: 2 }

      reg.add('onAfterQuery', async () => newResult)

      const result = await reg.runAfter(createCtx(), baseResult)
      expect(result).toEqual(newResult)
    })

    it('should chain replacements through multiple hooks', async () => {
      const reg = new HookRegistry()

      reg.add('onAfterQuery', async (_ctx, result) => ({
        items: result.items.map(i => ({ ...i, step1: true })),
        total: result.total
      }))
      reg.add('onAfterQuery', async (_ctx, result) => ({
        items: result.items.map(i => ({ ...i, step2: true })),
        total: result.total
      }))

      const result = await reg.runAfter(createCtx(), baseResult)
      expect(result.items[0]).toEqual({ id: 1, step1: true, step2: true })
    })

    it('should return original result when no hooks registered', async () => {
      const reg = new HookRegistry()
      const result = await reg.runAfter(createCtx(), baseResult)
      expect(result).toEqual(baseResult)
    })
  })

  describe('runError', () => {
    it('should return false when no hooks registered', async () => {
      const reg = new HookRegistry()
      const handled = await reg.runError(createCtx(), new Error('test'))
      expect(handled).toBe(false)
    })

    it('should return true when hook handles the error', async () => {
      const reg = new HookRegistry()
      reg.add('onQueryError', async () => true)

      const handled = await reg.runError(createCtx(), new Error('test'))
      expect(handled).toBe(true)
    })

    it('should return false when hook does not handle error', async () => {
      const reg = new HookRegistry()
      reg.add('onQueryError', vi.fn())

      const handled = await reg.runError(createCtx(), new Error('test'))
      expect(handled).toBe(false)
    })

    it('should execute all error hooks regardless of handling', async () => {
      const reg = new HookRegistry()
      const hook1 = vi.fn().mockResolvedValue(true)
      const hook2 = vi.fn()

      reg.add('onQueryError', hook1)
      reg.add('onQueryError', hook2)

      await reg.runError(createCtx(), new Error('test'))

      expect(hook1).toHaveBeenCalled()
      expect(hook2).toHaveBeenCalled()
    })

    it('should pass error and context to hooks', async () => {
      const reg = new HookRegistry()
      const hook = vi.fn()
      const error = new Error('test error')
      const ctx = createCtx({ trigger: 'filter' })

      reg.add('onQueryError', hook)
      await reg.runError(ctx, error)

      expect(hook).toHaveBeenCalledWith(ctx, error)
    })
  })
})
