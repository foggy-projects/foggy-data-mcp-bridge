import type {
  QueryHookName,
  BeforeQueryHookFn,
  AfterQueryHookFn,
  ErrorQueryHookFn,
  QueryHookContext,
  FetchDataResult
} from '@/types'

/** 钩子函数联合类型 */
type HookFn = BeforeQueryHookFn | AfterQueryHookFn | ErrorQueryHookFn

/**
 * 钩子注册表
 *
 * 管理查询钩子的注册、移除和执行。
 * 支持实例级和全局级两种使用场景。
 */
export class HookRegistry {
  private hooks = new Map<QueryHookName, Set<HookFn>>()

  /**
   * 注册钩子
   * @returns dispose 函数，调用后自动移除该钩子
   */
  add(name: QueryHookName, fn: HookFn): () => void {
    if (!this.hooks.has(name)) {
      this.hooks.set(name, new Set())
    }
    this.hooks.get(name)!.add(fn)
    return () => this.hooks.get(name)?.delete(fn)
  }

  /** 移除钩子 */
  remove(name: QueryHookName, fn: HookFn): void {
    this.hooks.get(name)?.delete(fn)
  }

  /** 获取指定钩子的数量 */
  size(name: QueryHookName): number {
    return this.hooks.get(name)?.size ?? 0
  }

  /** 清空所有钩子 */
  clear(): void {
    this.hooks.clear()
  }

  /**
   * 执行 onBeforeQuery 钩子链
   *
   * 顺序执行所有注册的 before 钩子：
   * - 任一钩子返回 false → 取消查询，返回 false
   * - 任一钩子返回 FetchDataParams → 替换 ctx.params 并继续
   * - 全部通过 → 返回 void
   */
  async runBefore(ctx: QueryHookContext): Promise<void | false> {
    const fns = this.hooks.get('onBeforeQuery')
    if (!fns) return
    for (const fn of fns) {
      const result = await (fn as BeforeQueryHookFn)(ctx)
      if (result === false) return false
      if (result && typeof result === 'object' && 'page' in result) {
        ctx.params = result
      }
    }
  }

  /**
   * 执行 onAfterQuery 钩子链
   *
   * 顺序执行所有注册的 after 钩子：
   * - 任一钩子返回 FetchDataResult → 替换结果并继续传递
   * - 全部通过 → 返回最终结果
   */
  async runAfter(ctx: QueryHookContext, result: FetchDataResult): Promise<FetchDataResult> {
    const fns = this.hooks.get('onAfterQuery')
    if (!fns) return result
    let current = result
    for (const fn of fns) {
      const r = await (fn as AfterQueryHookFn)(ctx, current)
      if (r && typeof r === 'object' && 'items' in r) {
        current = r
      }
    }
    return current
  }

  /**
   * 执行 onQueryError 钩子链
   *
   * 顺序执行所有注册的 error 钩子：
   * - 任一钩子返回 true → 标记错误已处理
   * - 返回是否有钩子处理了错误
   */
  async runError(ctx: QueryHookContext, error: Error): Promise<boolean> {
    const fns = this.hooks.get('onQueryError')
    if (!fns) return false
    let handled = false
    for (const fn of fns) {
      const r = await (fn as ErrorQueryHookFn)(ctx, error)
      if (r === true) handled = true
    }
    return handled
  }
}
