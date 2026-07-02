import type {
  AfterSearchHookFn,
  BeforeSearchHookFn,
  ErrorSearchHookFn,
  SearchHookName,
  SearchHooks
} from '@/types'
import { SearchHookRegistry } from './searchHookRegistry'

const registry = new SearchHookRegistry()

type SearchHookFnMap = {
  beforeSearch: BeforeSearchHookFn
  afterSearch: AfterSearchHookFn
  searchError: ErrorSearchHookFn
}

/**
 * Global search lifecycle hooks.
 *
 * These hooks run at the DataTableWithSearch action layer, before the final
 * fetch/query lifecycle handled by globalQueryHooks.
 */
export const globalSearchHooks = {
  add<N extends SearchHookName>(name: N, fn: SearchHookFnMap[N]): () => void {
    return registry.add(name, fn)
  },

  remove<N extends SearchHookName>(name: N, fn: SearchHookFnMap[N]): void {
    registry.remove(name, fn)
  },

  register(hooks: SearchHooks): () => void {
    return registry.register(hooks)
  },

  clear(): void {
    registry.clear()
  },

  _getRegistry(): SearchHookRegistry {
    return registry
  }
}
