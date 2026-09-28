<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import DataTable from './DataTable.vue'
import { fetchQueryMeta, fetchQueryData, fetchFilterOptions, fetchQmSchema, fetchFrontendMeta, QueryContextUnavailableError } from '@/api/viewer'
import { buildTableColumns } from '@/utils/schemaHelper'
import { routeMeasureFilters } from '@/utils/filterRouting'
import { buildViewerCsv, collectViewerExportRows, MAX_VIEWER_EXPORT_ROWS } from '@/utils/viewerCsvExport'
import type { QueryMetaResponse, ViewerQueryRequest, SliceRequestDef, OrderRequestDef, FilterOption, EnhancedColumnSchema, ColumnSchema, FrontendMeta } from '@/types'

const props = defineProps<{
  queryId: string
  model: string
}>()

// 状态
const loading = ref(false)
const error = ref<string | null>(null)
const expired = ref(false)
const meta = ref<QueryMetaResponse | null>(null)
const qmSchema = ref<ColumnSchema[]>([])
const frontendMeta = ref<FrontendMeta | null>(null)
const columns = ref<EnhancedColumnSchema[]>([])
const data = ref<Record<string, unknown>[]>([])
const total = ref(0)
const serverSummary = ref<Record<string, unknown> | null>(null)
const lastQueryAt = ref<string | null>(null)
const lastExecutedRequest = ref<ViewerQueryRequest | null>(null)
const exporting = ref(false)
const exportError = ref<string | null>(null)
const dslCopyStatus = ref<'idle' | 'copied' | 'failed'>('idle')

// 查询参数 (DSL 格式)
const queryParams = ref<ViewerQueryRequest>({
  start: 0,
  limit: 50,
  slice: [],
  orderBy: []
})

const dataTableRef = ref<InstanceType<typeof DataTable>>()

// 计算属性
const title = computed(() => {
  const name = meta.value?.title?.trim()
  return name && !name.includes(props.model) && !/Harness preview/i.test(name) ? name : '查询结果'
})
const expiresAt = computed(() => {
  if (!meta.value?.expiresAt) return ''
  return new Date(meta.value.expiresAt).toLocaleString('zh-CN')
})
const queryEvidence = computed(() => {
  const fieldTitles = new Map(qmSchema.value.map(field => [field.name, field.title]))
  const clauses = [
    ...(meta.value?.initialSlice || []),
    ...(meta.value?.initialHaving || []),
    ...(lastExecutedRequest.value?.slice || []),
    ...(lastExecutedRequest.value?.having || [])
  ]
  return clauses.map(item => {
    if (!item.field) return '组合筛选'
    const value = Array.isArray(item.value) ? item.value.join(' 至 ') : String(item.value ?? '空值')
    return `${fieldTitles.get(item.field) || item.field} ${item.op} ${value}`
  })
})
const filterSummary = computed(() => queryEvidence.value.length
  ? `${queryEvidence.value.slice(0, 2).join('、')}${queryEvidence.value.length > 2 ? ` 等 ${queryEvidence.value.length} 项` : ''}`
  : '未设置筛选条件')
const effectiveDsl = computed(() => {
  if (!meta.value || !lastExecutedRequest.value) return ''
  const base = meta.value.initialDsl
  const current = lastExecutedRequest.value
  const query = {
    queryModel: base?.queryModel || props.model,
    columns: base?.columns || meta.value.tableConfig.visibleColumns,
    ...(base?.groupBy?.length ? { groupBy: base.groupBy } : {}),
    ...(base?.calculatedFields?.length ? { calculatedFields: base.calculatedFields } : {}),
    slice: [...(base?.slice || meta.value.initialSlice || []), ...(current.slice || [])],
    having: [...(base?.having || meta.value.initialHaving || []), ...(current.having || [])],
    orderBy: current.orderBy?.length ? current.orderBy : (base?.orderBy || []),
    start: current.start ?? 0,
    limit: current.limit ?? 50
  }
  return JSON.stringify(query, null, 2)
})

// 加载元数据
async function loadMeta() {
  try {
    loading.value = true
    error.value = null

    // 1. 获取查询元数据（包含 tableConfig）
    meta.value = await fetchQueryMeta(props.model, props.queryId)

    // 2. 获取 QM Schema
    if (meta.value.tableConfig.qmModel) {
      const scopedOptions = meta.value.namespace ? { headers: { 'X-NS': meta.value.namespace } } : undefined
      qmSchema.value = await fetchQmSchema(meta.value.tableConfig.qmModel, scopedOptions)
      try {
        frontendMeta.value = await fetchFrontendMeta(meta.value.tableConfig.qmModel, scopedOptions)
      } catch {
        frontendMeta.value = null
      }

      // 3. 使用 buildTableColumns 构建列配置
      columns.value = buildTableColumns(qmSchema.value, meta.value.tableConfig)
    }
  } catch (e) {
    if (e instanceof QueryContextUnavailableError) {
      expired.value = true
      error.value = e.message
    } else {
      error.value = e instanceof Error ? e.message : '加载元数据失败'
    }
  } finally {
    loading.value = false
  }
}

// 加载数据
async function loadData() {
  if (!meta.value) return

  try {
    loading.value = true
    error.value = null

    const response = await fetchQueryData(props.model, props.queryId, queryParams.value)

    if (response.expired) {
      expired.value = true
      error.value = response.errorMessage || '查询已过期'
      return
    }

    if (!response.success) {
      error.value = response.errorMessage || '查询失败'
      return
    }

    data.value = response.items
    total.value = response.total
    // 提取汇总数据
    serverSummary.value = response.totalData ?? null
    lastQueryAt.value = new Date().toLocaleString('zh-CN')
    lastExecutedRequest.value = JSON.parse(JSON.stringify(queryParams.value))
    dslCopyStatus.value = 'idle'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载数据失败'
  } finally {
    loading.value = false
  }
}

// 事件处理
function handlePageChange(page: number, size: number) {
  queryParams.value.start = (page - 1) * size
  queryParams.value.limit = size
  loadData()
}

function handleSortChange(field: string | null, order: 'asc' | 'desc' | null) {
  if (field && order) {
    queryParams.value.orderBy = [{ field, dir: order }]
  } else {
    queryParams.value.orderBy = []
  }
  loadData()
}

function handleFilterChange(slices: SliceRequestDef[]) {
  const routed = routeMeasureFilters(slices, qmSchema.value)
  queryParams.value.slice = routed.slice
  queryParams.value.having = routed.having
  queryParams.value.start = 0
  dataTableRef.value?.resetPagination()
  loadData()
}

// 加载维度选项
async function loadFilterOptions(columnName: string): Promise<FilterOption[]> {
  try {
    const response = await fetchFilterOptions(props.model, props.queryId, columnName)
    return response.options || []
  } catch (e) {
    console.error('Failed to load filter options:', e)
    return []
  }
}

// 刷新数据
function refresh() {
  loadData()
}

async function copyDsl() {
  if (!effectiveDsl.value) return
  try {
    await navigator.clipboard.writeText(effectiveDsl.value)
    dslCopyStatus.value = 'copied'
  } catch {
    dslCopyStatus.value = 'failed'
  }
}

async function exportCsv() {
  if (!meta.value || exporting.value || loading.value || expired.value) return
  exporting.value = true
  exportError.value = null
  try {
    // Vue wraps ref values in a Proxy, which structuredClone cannot copy.
    const request: ViewerQueryRequest = JSON.parse(JSON.stringify(queryParams.value))
    const rows = await collectViewerExportRows(request, page => fetchQueryData(props.model, props.queryId, page))
    const csv = buildViewerCsv(columns.value, rows)
    const blob = new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${props.model.replace(/[^\w.-]/g, '_')}-${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (e) {
    exportError.value = e instanceof Error ? e.message : '导出失败，请重试'
  } finally {
    exporting.value = false
  }
}

// 初始化
onMounted(async () => {
  await loadMeta()
  if (meta.value) {
    await loadData()
  }
})
</script>

<template>
  <div class="data-viewer">
    <header class="viewer-header">
      <h1 class="viewer-title">{{ title }}</h1>
      <div class="viewer-highlights" aria-live="polite">
        <span v-if="lastQueryAt" class="result-count">共 {{ total.toLocaleString('zh-CN') }} 条结果</span>
        <span v-else class="result-count">{{ loading ? '正在加载结果…' : '等待查询结果' }}</span>
        <span v-if="lastQueryAt" class="query-time">本次查询：{{ lastQueryAt }}</span>
      </div>
      <p class="filter-summary"><strong>筛选条件</strong>{{ filterSummary }}</p>
      <details v-if="meta" class="query-details">
        <summary>查询详情与 DSL</summary>
        <div class="query-details-body">
          <section class="query-detail-panel" aria-label="查询详情">
            <div class="detail-heading">查询详情</div>
            <dl class="query-metadata">
              <div><dt>查询模型</dt><dd>{{ meta.tableConfig.qmModel }}</dd></div>
              <div><dt>数据空间</dt><dd>{{ meta.namespace || '默认空间' }}</dd></div>
              <div v-if="expiresAt"><dt>链接有效至</dt><dd>{{ expiresAt }}</dd></div>
              <div v-if="frontendMeta?.description"><dt>模型说明</dt><dd>{{ frontendMeta.description }}</dd></div>
              <div><dt>已生效的筛选</dt><dd>{{ queryEvidence.length ? queryEvidence.join('；') : '无' }}</dd></div>
            </dl>
          </section>
          <section class="query-dsl-panel" aria-label="本次查询 DSL">
            <div class="dsl-header">
              <div class="dsl-heading">本次查询 DSL</div>
              <button class="dsl-copy-btn" type="button" :disabled="!effectiveDsl" @click="copyDsl">
                {{ dslCopyStatus === 'copied' ? '已复制' : '复制 DSL' }}
              </button>
            </div>
            <span class="sr-only" role="status">{{ dslCopyStatus === 'copied' ? 'DSL 已复制到剪贴板' : '' }}</span>
            <p v-if="dslCopyStatus === 'failed'" class="dsl-copy-error" role="alert">复制失败，请选中下方 DSL 手动复制。</p>
            <p class="dsl-note">根据最近一次成功查询展示；不包含可能敏感的运行时参数。</p>
            <pre v-if="effectiveDsl" class="dsl-code"><code>{{ effectiveDsl }}</code></pre>
            <p v-else class="dsl-note">查询成功后会显示 DSL。</p>
            <p v-if="meta.initialDsl?.hasRuntimeParameters" class="dsl-note">这次查询使用了运行时参数，参数值未在此展示。</p>
          </section>
        </div>
      </details>
    </header>

    <div v-if="expired" class="viewer-expired">
      <div class="expired-content">
        <h2>链接已失效</h2>
        <p>{{ error || '请从 Harness 重新打开报表' }}</p>
      </div>
    </div>

    <div v-else-if="error && !data.length" class="viewer-error">
      <div class="error-content">
        <h2>加载失败</h2>
        <p>{{ error }}</p>
        <button @click="loadData">重试</button>
      </div>
    </div>

    <main v-else class="viewer-main">
      <DataTable
        ref="dataTableRef"
        :columns="columns"
        :data="data"
        :total="total"
        :loading="loading"
        :filter-options-loader="loadFilterOptions"
        :server-summary="serverSummary"
        @page-change="handlePageChange"
        @sort-change="handleSortChange"
        @filter-change="handleFilterChange"
      >
        <template #toolbar>
          <div class="viewer-actions">
            <button class="refresh-btn" type="button" @click="refresh" :disabled="loading || !meta">
              {{ loading ? '刷新中…' : '刷新' }}
            </button>
            <button class="export-btn" type="button" @click="exportCsv" :disabled="loading || exporting || expired || !meta" :aria-busy="exporting" :title="`导出当前筛选和排序的结果，最多 ${MAX_VIEWER_EXPORT_ROWS.toLocaleString('zh-CN')} 行`">
              {{ exporting ? '导出中…' : '导出 CSV' }}
            </button>
          </div>
          <p v-if="exportError" class="export-error" role="alert">{{ exportError }}</p>
        </template>
      </DataTable>
    </main>

    <footer class="viewer-footer">
      <span>Foggy Data Viewer</span>
    </footer>
  </div>
</template>

<style scoped>
.data-viewer {
  --viewer-space-xs: 4px;
  --viewer-space-sm: 8px;
  --viewer-space-md: 16px;
  --viewer-space-lg: 24px;
  --viewer-text: #303133;
  --viewer-muted: #606266;
  --viewer-border: #e4e7ed;
  --viewer-accent: #1670c5;
  --viewer-bg: #f5f7fa;
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: var(--viewer-bg);
}

.viewer-header {
  padding: var(--viewer-space-md) var(--viewer-space-lg);
  background-color: #fff;
  border-bottom: 1px solid var(--viewer-border);
}

.viewer-title {
  margin: 0 0 var(--viewer-space-sm);
  font-size: 22px;
  font-weight: 600;
  color: var(--viewer-text);
}

.viewer-highlights {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--viewer-space-md);
  font-size: 14px;
  color: var(--viewer-muted);
}

.result-count {
  font-weight: 600;
  color: var(--viewer-text);
}

.filter-summary {
  margin: var(--viewer-space-sm) 0 0;
  color: var(--viewer-muted);
  font-size: 14px;
  line-height: 1.5;
}

.filter-summary strong {
  margin-right: var(--viewer-space-sm);
  color: var(--viewer-text);
}

.query-details {
  margin-top: var(--viewer-space-md);
  border-top: 1px solid var(--viewer-border);
  color: var(--viewer-muted);
  font-size: 13px;
}

.query-details summary {
  width: fit-content;
  padding-top: var(--viewer-space-sm);
  color: var(--viewer-accent);
  font-weight: 600;
  cursor: pointer;
}

.query-details summary:focus-visible,
.dsl-copy-btn:focus-visible,
.refresh-btn:focus-visible,
.export-btn:focus-visible {
  outline: 2px solid var(--viewer-accent);
  outline-offset: 2px;
}

.query-details-body {
  display: grid;
  grid-template-columns: minmax(260px, 2fr) minmax(0, 3fr);
  gap: var(--viewer-space-lg);
  padding-top: var(--viewer-space-md);
  max-height: 340px;
  overflow: auto;
}

.query-detail-panel,
.query-dsl-panel {
  min-width: 0;
}

.query-detail-panel {
  padding-right: var(--viewer-space-lg);
  border-right: 1px solid var(--viewer-border);
}

.query-metadata {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--viewer-space-sm);
  margin: var(--viewer-space-sm) 0 0;
  line-height: 1.5;
}

.detail-heading,
.query-metadata dt,
.dsl-heading {
  font-weight: 600;
  color: var(--viewer-text);
}

.dsl-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--viewer-space-sm);
}

.dsl-copy-btn {
  padding: 4px 10px;
  border: 1px solid var(--viewer-border);
  border-radius: 4px;
  background: #fff;
  color: var(--viewer-accent);
  cursor: pointer;
  font-size: 12px;
}

.dsl-copy-btn:hover:not(:disabled) {
  background: #ecf5ff;
}

.dsl-copy-btn:disabled {
  color: var(--viewer-muted);
  cursor: not-allowed;
}

.dsl-copy-error {
  margin: var(--viewer-space-xs) 0;
  color: #c62828;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.query-metadata dd {
  margin: var(--viewer-space-xs) 0 0;
  overflow-wrap: anywhere;
}

.dsl-note {
  margin: var(--viewer-space-xs) 0 var(--viewer-space-sm);
  line-height: 1.5;
}

.dsl-code {
  margin: 0 0 var(--viewer-space-sm);
  padding: var(--viewer-space-md);
  overflow: auto;
  border: 1px solid var(--viewer-border);
  border-radius: 6px;
  background: var(--viewer-bg);
  color: var(--viewer-text);
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 780px) {
  .query-details-body {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--viewer-space-md);
  }

  .query-detail-panel {
    padding-right: 0;
    padding-bottom: var(--viewer-space-md);
    border-right: 0;
    border-bottom: 1px solid var(--viewer-border);
  }
}

.viewer-actions {
  display: flex;
  gap: var(--viewer-space-sm);
}

.refresh-btn,
.export-btn {
  min-height: 34px;
  padding: 6px 14px;
  border: 1px solid var(--viewer-accent);
  border-radius: 4px;
  background: #fff;
  color: var(--viewer-accent);
  cursor: pointer;
  font-size: 14px;
  transition: background-color 150ms ease;
}

.refresh-btn:hover:not(:disabled),
.export-btn:hover:not(:disabled) {
  background: #ecf5ff;
}

.refresh-btn:disabled,
.export-btn:disabled {
  opacity: .55;
  cursor: not-allowed;
}

.export-error {
  margin: 0;
  color: #c02a32;
  font-size: 13px;
}

.viewer-main {
  flex: 1;
  min-height: 0;
  padding: var(--viewer-space-md) var(--viewer-space-lg);
  overflow: hidden;
}

.viewer-main :deep(.data-table-toolbar) {
  flex-wrap: wrap;
}

.viewer-expired,
.viewer-error {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.expired-content,
.error-content {
  text-align: center;
  padding: 32px;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.expired-content h2,
.error-content h2 {
  margin: 0 0 12px 0;
  color: #f56c6c;
}

.expired-content p,
.error-content p {
  margin: 0 0 16px 0;
  color: #909399;
}

.error-content button {
  padding: 8px 24px;
  background-color: #409eff;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.viewer-footer {
  padding: 8px 24px;
  text-align: center;
  font-size: 12px;
  color: #909399;
  background-color: #fff;
  border-top: 1px solid #e4e7ed;
}
</style>
