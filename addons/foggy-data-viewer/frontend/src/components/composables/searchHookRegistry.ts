import type {
  AfterSearchHookFn,
  BeforeSearchHookFn,
  ErrorSearchHookFn,
  FetchDataResult,
  SearchHookContext,
  SearchHookName,
  SearchHooks,
  SearchHookUpdate
} from '@/types'

type SearchHookFn = BeforeSearchHookFn | AfterSearchHookFn | ErrorSearchHookFn

type SearchHookFnMap = {
  beforeSearch: BeforeSearchHookFn
  afterSearch: AfterSearchHookFn
  searchError: ErrorSearchHookFn
}

function applySearchHookUpdate(ctx: SearchHookContext, update: SearchHookUpdate): void {
  if (update.params) {
    ctx.params = update.params
    ctx.columns = [...update.params.columns]
    ctx.slice = [...update.params.slice]
    ctx.orderBy = [...update.params.orderBy]
  }
  if (update.columns) {
    ctx.columns = [...update.columns]
  }
  if (update.slice) {
    ctx.slice = [...update.slice]
  }
  if (update.orderBy) {
    ctx.orderBy = [...update.orderBy]
  }
}

export class SearchHookRegistry {
  private hooks = new Map<SearchHookName, Set<SearchHookFn>>()

  add<N extends SearchHookName>(name: N, fn: SearchHookFnMap[N]): () => void {
    if (!this.hooks.has(name)) {
      this.hooks.set(name, new Set())
    }
    this.hooks.get(name)!.add(fn)
    return () => this.hooks.get(name)?.delete(fn)
  }

  remove<N extends SearchHookName>(name: N, fn: SearchHookFnMap[N]): void {
    this.hooks.get(name)?.delete(fn)
  }

  register(hooks: SearchHooks): () => void {
    const disposers: Array<() => void> = []
    if (hooks.beforeSearch) {
      disposers.push(this.add('beforeSearch', hooks.beforeSearch))
    }
    if (hooks.afterSearch) {
      disposers.push(this.add('afterSearch', hooks.afterSearch))
    }
    if (hooks.searchError) {
      disposers.push(this.add('searchError', hooks.searchError))
    }
    return () => disposers.forEach(dispose => dispose())
  }

  removeRegistered(hooks: SearchHooks): void {
    if (hooks.beforeSearch) {
      this.remove('beforeSearch', hooks.beforeSearch)
    }
    if (hooks.afterSearch) {
      this.remove('afterSearch', hooks.afterSearch)
    }
    if (hooks.searchError) {
      this.remove('searchError', hooks.searchError)
    }
  }

  size(name: SearchHookName): number {
    return this.hooks.get(name)?.size ?? 0
  }

  clear(): void {
    this.hooks.clear()
  }

  async runBefore(ctx: SearchHookContext): Promise<void | false> {
    const fns = this.hooks.get('beforeSearch')
    if (!fns) return

    for (const fn of fns) {
      const result = await (fn as BeforeSearchHookFn)(ctx)
      if (result === false) return false
      if (result && typeof result === 'object') {
        applySearchHookUpdate(ctx, result)
      }
    }
  }

  async runAfter(ctx: SearchHookContext, result: FetchDataResult): Promise<void> {
    const fns = this.hooks.get('afterSearch')
    if (!fns) return

    for (const fn of fns) {
      await (fn as AfterSearchHookFn)(ctx, result)
    }
  }

  async runError(ctx: SearchHookContext, error: Error): Promise<boolean> {
    const fns = this.hooks.get('searchError')
    if (!fns) return false

    let handled = false
    for (const fn of fns) {
      const result = await (fn as ErrorSearchHookFn)(ctx, error)
      if (result === true) {
        handled = true
      }
    }
    return handled
  }
}
