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
  ErrorQueryHookFn,
  QueryExecutionOptions
} from '@/types'
import { HookRegistry } from './hookRegistry'
import { globalQueryHooks } from './globalQueryHooks'
import { cloneSliceTree } from '@/utils/listPreset'
import { cloneFetchDataParams } from '@/utils/queryParams'

/** 钩子名称到函数类型的映射 */
type HookFnMap = {
  onBeforeQuery: BeforeQueryHookFn
  onAfterQuery: AfterQueryHookFn
  onQueryError: ErrorQueryHookFn
}

function normalizeOrderBy(orderBy: OrderRequestDef[]): OrderRequestDef[] {
  return orderBy.flatMap(item => {
    const rawDir = item.dir ?? item.order
    const dir = typeof rawDir === 'string' ? rawDir.toLowerCase() : rawDir

    if (!item.field || (dir !== 'asc' && dir !== 'desc')) {
      return []
    }

    const normalized: OrderRequestDef = {
      field: item.field,
      dir
    }

    if (item.nullFirst) {
      normalized.nullFirst = true
    }
    if (item.nullLast) {
      normalized.nullLast = true
    }

    return [normalized]
  })
}

function normalizeColumns(columns: string[]): string[] {
  return columns.flatMap(column => {
    const trimmed = column.trim()
    return trimmed ? [trimmed] : []
  })
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
  lastOutcome: ReturnType<typeof ref<'success' | 'cancelled' | 'error' | null>>
  serverSummary: ReturnType<typeof ref<Record<string, unknown> | null>>
  currentPage: ReturnType<typeof ref<number>>
  currentPageSize: ReturnType<typeof ref<number>>
  currentOrderBy: ReturnType<typeof ref<OrderRequestDef[]>>
  currentSlice: ReturnType<typeof ref<SliceRequestDef[]>>
  currentColumns: ReturnType<typeof ref<string[]>>
  currentTableInstanceId: ReturnType<typeof ref<string | undefined>>

  // 查询方法
  loadData: (trigger?: QueryTrigger) => Promise<void>
  /** Execute an independent query without changing any table state. */
  executeQuery: (params: FetchDataParams, options?: QueryExecutionOptions) => Promise<FetchDataResult | undefined>
  refresh: () => Promise<void>
  reload: () => Promise<void>
  setPage: (page: number, pageSize?: number) => void
  setSort: (orderBy: OrderRequestDef[]) => void
  setSlice: (slice: SliceRequestDef[]) => void
  setColumns: (columns: string[]) => void
  setTableInstanceId: (tableInstanceId?: string) => void

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
 * executeQuery uses the same query hook order with an isolated params copy.
 * It never updates loading, activeTrigger, data, totals, errors, or outcome;
 * cancellation and handled errors resolve to undefined, while unhandled
 * errors reject.
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
  const lastOutcome = ref<'success' | 'cancelled' | 'error' | null>(null)
  const serverSummary = ref<Record<string, unknown> | null>(null)
  const currentPage = ref(1)
  const currentPageSize = ref(options.pageSize ?? 50)
  const currentOrderBy = ref<OrderRequestDef[]>([])
  const currentSlice = ref<SliceRequestDef[]>([])
  const currentColumns = ref<string[]>([])
  const currentTableInstanceId = ref<string | undefined>(undefined)

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
  async function runQuery(
    params: FetchDataParams,
    trigger: QueryTrigger,
    updateTableState: boolean
  ): Promise<FetchDataResult | undefined> {
    const executionParams = cloneFetchDataParams(params)
    const ctx: QueryHookContext = {
      params: executionParams,
      trigger
    }

    const propsRegistry = buildPropsRegistry()

    if (updateTableState) {
      lastOutcome.value = null
      lastError.value = null
    }

    try {
      // ---- Before hooks: global → props → instance ----
      const globalBefore = await globalRegistry.runBefore(ctx)
      if (globalBefore === false) {
        if (updateTableState) lastOutcome.value = 'cancelled'
        return undefined
      }

      const propsBefore = await propsRegistry.runBefore(ctx)
      if (propsBefore === false) {
        if (updateTableState) lastOutcome.value = 'cancelled'
        return undefined
      }

      const instanceBefore = await instanceRegistry.runBefore(ctx)
      if (instanceBefore === false) {
        if (updateTableState) lastOutcome.value = 'cancelled'
        return undefined
      }

      // Hook replacement objects are part of the public hook contract. Detach
      // their mutable query branches before crossing into the fetch function.
      ctx.params = cloneFetchDataParams(ctx.params)

      if (updateTableState) {
        loading.value = true
        activeTrigger.value = trigger
      }
      let result = await fetchData(ctx.params)

      // ---- After hooks: instance → props → global ----
      result = await instanceRegistry.runAfter(ctx, result)
      result = await propsRegistry.runAfter(ctx, result)
      result = await globalRegistry.runAfter(ctx, result)

      if (updateTableState) {
        data.value = result.items
        total.value = result.total
        serverSummary.value = result.totalData ?? null
        lastOutcome.value = 'success'
      }
      return result
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err))

      if (updateTableState) {
        lastError.value = error
        lastOutcome.value = 'error'
      }

      // ---- Error hooks: instance → props → global ----
      const instanceHandled = await instanceRegistry.runError(ctx, error)
      const propsHandled = await propsRegistry.runError(ctx, error)
      const globalHandled = await globalRegistry.runError(ctx, error)

      if (!instanceHandled && !propsHandled && !globalHandled) {
        if (updateTableState) console.error('数据加载失败:', error)
        throw error
      }
      return undefined
    } finally {
      if (updateTableState) {
        loading.value = false
        activeTrigger.value = null
      }
    }
  }

  async function loadData(trigger: QueryTrigger = 'refresh'): Promise<void> {
    const params: FetchDataParams = {
      page: currentPage.value,
      pageSize: currentPageSize.value,
      columns: [...currentColumns.value],
      slice: cloneSliceTree(currentSlice.value),
      orderBy: [...currentOrderBy.value]
    }
    if (currentTableInstanceId.value) {
      params.tableInstanceId = currentTableInstanceId.value
    }

    await runQuery(params, trigger, true)
  }

  async function executeQuery(
    params: FetchDataParams,
    options: QueryExecutionOptions = {}
  ): Promise<FetchDataResult | undefined> {
    return runQuery(params, options.trigger ?? 'export', false)
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
    currentOrderBy.value = normalizeOrderBy(orderBy)
  }

  function setSlice(slice: SliceRequestDef[]): void {
    currentSlice.value = slice
  }

  function setColumns(columns: string[]): void {
    currentColumns.value = normalizeColumns(columns)
  }

  function setTableInstanceId(tableInstanceId?: string): void {
    currentTableInstanceId.value = tableInstanceId || undefined
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
    lastOutcome,
    serverSummary,
    currentPage,
    currentPageSize,
    currentOrderBy,
    currentSlice,
    currentColumns,
    currentTableInstanceId,
    loadData,
    executeQuery,
    refresh,
    reload,
    setPage,
    setSort,
    setSlice,
    setColumns,
    setTableInstanceId,
    addHook,
    removeHook
  }
}
