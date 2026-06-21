import { ref } from 'vue'
import type {
  FetchDataParams,
  FetchDataResult,
  FetchDataFn,
  SliceRequestDef,
  OrderRequestDef,
  QueryHooks,
  QueryHookContext,
  QueryTrigger,
  QueryHookName,
  BeforeQueryHookFn,
  AfterQueryHookFn,
  ErrorQueryHookFn
} from '@/types'
import { HookRegistry } from './hookRegistry'
import { globalQueryHooks } from './globalQueryHooks'

/** 钩子名称到函数类型的映射 */
type HookFnMap = {
  onBeforeQuery: BeforeQueryHookFn
  onAfterQuery: AfterQueryHookFn
  onQueryError: ErrorQueryHookFn
}

export interface UseTableQueryOptions {
  /** 初始每页大小 */
  pageSize?: number
  /** 声明式钩子（来自 props） */
  hooks?: QueryHooks
}

export interface UseTableQueryReturn {
  // 状态
  data: ReturnType<typeof ref<Record<string, unknown>[]>>
  total: ReturnType<typeof ref<number>>
  loading: ReturnType<typeof ref<boolean>>
  activeTrigger: ReturnType<typeof ref<QueryTrigger | null>>
  lastError: ReturnType<typeof ref<Error | null>>
  serverSummary: ReturnType<typeof ref<Record<string, unknown> | null>>
  currentPage: ReturnType<typeof ref<number>>
  currentPageSize: ReturnType<typeof ref<number>>
  currentOrderBy: ReturnType<typeof ref<OrderRequestDef[]>>
  currentSlice: ReturnType<typeof ref<SliceRequestDef[]>>

  // 查询方法
  loadData: (trigger?: QueryTrigger) => Promise<void>
  refresh: () => Promise<void>
  reload: () => Promise<void>
  setPage: (page: number, pageSize?: number) => void
  setSort: (orderBy: OrderRequestDef[]) => void
  setSlice: (slice: SliceRequestDef[]) => void

  // 钩子管理
  addHook: <N extends QueryHookName>(name: N, fn: HookFnMap[N]) => () => void
  removeHook: <N extends QueryHookName>(name: N, fn: HookFnMap[N]) => void
}

/**
 * 表格查询 Composable
 *
 * 管理查询状态（分页、排序、筛选、加载、数据）和钩子执行链。
 *
 * 钩子执行顺序（洋葱模型）：
 *   global onBeforeQuery → props onBeforeQuery → instance onBeforeQuery
 *     → fetchData
 *   instance onAfterQuery → props onAfterQuery → global onAfterQuery
 *
 * @param fetchData 数据加载函数
 * @param options 配置项
 */
export function useTableQuery(
  fetchData: FetchDataFn,
  options: UseTableQueryOptions = {}
): UseTableQueryReturn {
  // ========== 状态 ==========
  const data = ref<Record<string, unknown>[]>([])
  const total = ref(0)
  const loading = ref(false)
  const activeTrigger = ref<QueryTrigger | null>(null)
  const lastError = ref<Error | null>(null)
  const serverSummary = ref<Record<string, unknown> | null>(null)
  const currentPage = ref(1)
  const currentPageSize = ref(options.pageSize ?? 50)
  const currentOrderBy = ref<OrderRequestDef[]>([])
  const currentSlice = ref<SliceRequestDef[]>([])

  // ========== 实例级钩子注册表 ==========
  const instanceRegistry = new HookRegistry()
  const globalRegistry = globalQueryHooks._getRegistry()

  // ========== Props 钩子作为中间层 registry ==========
  // 为 props 钩子创建一个临时 registry，每次 loadData 时从 options.hooks 读取
  function buildPropsRegistry(): HookRegistry {
    const reg = new HookRegistry()
    const hooks = options.hooks
    if (!hooks) return reg
    if (hooks.onBeforeQuery) reg.add('onBeforeQuery', hooks.onBeforeQuery)
    if (hooks.onAfterQuery) reg.add('onAfterQuery', hooks.onAfterQuery)
    if (hooks.onQueryError) reg.add('onQueryError', hooks.onQueryError)
    return reg
  }

  // ========== 核心加载逻辑 ==========
  async function loadData(trigger: QueryTrigger = 'refresh'): Promise<void> {
    const ctx: QueryHookContext = {
      params: {
        page: currentPage.value,
        pageSize: currentPageSize.value,
        slice: [...currentSlice.value],
        orderBy: [...currentOrderBy.value]
      },
      trigger
    }

    const propsRegistry = buildPropsRegistry()

    // ---- Before hooks: global → props → instance ----
    const globalBefore = await globalRegistry.runBefore(ctx)
    if (globalBefore === false) return

    const propsBefore = await propsRegistry.runBefore(ctx)
    if (propsBefore === false) return

    const instanceBefore = await instanceRegistry.runBefore(ctx)
    if (instanceBefore === false) return

    // ---- Fetch ----
    loading.value = true
    activeTrigger.value = trigger
    lastError.value = null
    try {
      let result = await fetchData(ctx.params)

      // ---- After hooks: instance → props → global ----
      result = await instanceRegistry.runAfter(ctx, result)
      result = await propsRegistry.runAfter(ctx, result)
      result = await globalRegistry.runAfter(ctx, result)

      data.value = result.items
      total.value = result.total
      serverSummary.value = result.totalData ?? null
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err))
      lastError.value = error

      // ---- Error hooks: instance → props → global ----
      const instanceHandled = await instanceRegistry.runError(ctx, error)
      const propsHandled = await propsRegistry.runError(ctx, error)
      const globalHandled = await globalRegistry.runError(ctx, error)

      if (!instanceHandled && !propsHandled && !globalHandled) {
        console.error('数据加载失败:', error)
        throw error
      }
    } finally {
      loading.value = false
      activeTrigger.value = null
    }
  }

  // ========== 便捷方法 ==========
  async function refresh(): Promise<void> {
    await loadData('refresh')
  }

  async function reload(): Promise<void> {
    currentPage.value = 1
    await loadData('reload')
  }

  function setPage(page: number, pageSize?: number): void {
    currentPage.value = page
    if (pageSize !== undefined) {
      currentPageSize.value = pageSize
    }
  }

  function setSort(orderBy: OrderRequestDef[]): void {
    currentOrderBy.value = orderBy
  }

  function setSlice(slice: SliceRequestDef[]): void {
    currentSlice.value = slice
  }

  // ========== 钩子管理 API ==========
  function addHook<N extends QueryHookName>(name: N, fn: HookFnMap[N]): () => void {
    return instanceRegistry.add(name, fn)
  }

  function removeHook<N extends QueryHookName>(name: N, fn: HookFnMap[N]): void {
    instanceRegistry.remove(name, fn)
  }

  return {
    data,
    total,
    loading,
    activeTrigger,
    lastError,
    serverSummary,
    currentPage,
    currentPageSize,
    currentOrderBy,
    currentSlice,
    loadData,
    refresh,
    reload,
    setPage,
    setSort,
    setSlice,
    addHook,
    removeHook
  }
}
