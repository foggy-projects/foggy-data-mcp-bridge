import { HookRegistry } from './hookRegistry'
import type {
  QueryHookName,
  BeforeQueryHookFn,
  AfterQueryHookFn,
  ErrorQueryHookFn,
  QueryHooks
} from '@/types'

/** 全局钩子注册表（模块级单例） */
const registry = new HookRegistry()

/** 钩子名称到函数类型的映射 */
type HookFnMap = {
  onBeforeQuery: BeforeQueryHookFn
  onAfterQuery: AfterQueryHookFn
  onQueryError: ErrorQueryHookFn
}

/**
 * 全局查询钩子 API
 *
 * 注册的钩子对所有 DataTableWithSearch / useTableQuery 实例生效。
 *
 * @example
 * ```ts
 * import { globalQueryHooks } from 'foggy-data-viewer'
 *
 * // 注册全局前置钩子
 * const dispose = globalQueryHooks.add('onBeforeQuery', (ctx) => {
 *   ctx.params.slice.push({ field: 'tenantId', op: '=', value: getTenantId() })
 * })
 *
 * // 移除
 * dispose()
 *
 * // 批量注册
 * const disposeAll = globalQueryHooks.register({
 *   onBeforeQuery: (ctx) => { ... },
 *   onAfterQuery: (ctx, result) => { ... }
 * })
 * disposeAll()
 * ```
 */
export const globalQueryHooks = {
  /**
   * 注册单个全局钩子
   * @returns dispose 函数
   */
  add<N extends QueryHookName>(name: N, fn: HookFnMap[N]): () => void {
    return registry.add(name, fn)
  },

  /** 移除单个全局钩子 */
  remove<N extends QueryHookName>(name: N, fn: HookFnMap[N]): void {
    registry.remove(name, fn)
  },

  /**
   * 批量注册全局钩子
   * @returns dispose 函数（一次性移除所有注册的钩子）
   */
  register(hooks: QueryHooks): () => void {
    const disposers: Array<() => void> = []
    if (hooks.onBeforeQuery) {
      disposers.push(registry.add('onBeforeQuery', hooks.onBeforeQuery))
    }
    if (hooks.onAfterQuery) {
      disposers.push(registry.add('onAfterQuery', hooks.onAfterQuery))
    }
    if (hooks.onQueryError) {
      disposers.push(registry.add('onQueryError', hooks.onQueryError))
    }
    return () => disposers.forEach(d => d())
  },

  /** 清空所有全局钩子（主要用于测试） */
  clear(): void {
    registry.clear()
  },

  /** 获取底层 registry（内部使用） */
  _getRegistry(): HookRegistry {
    return registry
  }
}
