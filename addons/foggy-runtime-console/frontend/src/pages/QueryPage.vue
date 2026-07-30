<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import RuntimeResultTable from '@/components/RuntimeResultTable.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { normalizeResultRows, parseJsonObject, prettyJson } from '@/utils/json'
import { useContextRail } from '@/stores/contextRail'
import { useNamespaceScope } from '@/composables/useNamespaceScope'
import {
  queryRowsToCsv,
  summarizeQueryPayload,
  type QueryPayloadSummary
} from '@/features/query/queryWorkbench'

interface ModelCatalog {
  data?: { models?: string[] }
}

interface QueryResponse {
  items?: Record<string, unknown>[]
  warnings?: string[]
  total?: number
  hasNext?: boolean
  pagination?: Record<string, unknown>
  execution?: Record<string, unknown>
  [key: string]: unknown
}

interface QueryDiagnostics {
  total?: number
  hasNext?: boolean
  pagination?: Record<string, unknown>
  execution?: Record<string, unknown>
  clientDurationMs: number
}

interface QueryHistoryEntry {
  id: number
  namespace: string
  model: string
  mode: 'validate' | 'execute'
  status: 'SUCCESS' | 'FAILED'
  rows: number
  durationMs: number
  time: string
  payload: string
  errorCode?: string
}

const examplePayload = {
  columns: ['orderStatus', 'sum(payAmount) as totalPay'],
  slice: [],
  groupBy: [{ field: 'orderStatus' }],
  orderBy: [{ field: 'totalPay', dir: 'desc' }],
  page: { start: 0, limit: 100 }
}

const models = ref<string[]>([])
const contextRail = useContextRail()
const namespaceScope = useNamespaceScope()
const model = ref('')
const mode = ref<'validate' | 'execute'>('execute')
const payload = ref(prettyJson(examplePayload))
const busy = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const queryDiagnostics = ref<QueryDiagnostics | null>(null)
const warnings = ref<string[]>([])
const runError = ref('')
const modelsLoading = ref(false)
const history = ref<QueryHistoryEntry[]>([])
let historyId = 1

const payloadState = computed<{
  valid: boolean
  value: Record<string, unknown> | null
  summary: QueryPayloadSummary
  error: string
}>(() => {
  try {
    const value = parseJsonObject(payload.value, '查询 DSL JSON')
    return {
      valid: true,
      value,
      summary: summarizeQueryPayload(value),
      error: ''
    }
  } catch (error) {
    return {
      valid: false,
      value: null,
      summary: summarizeQueryPayload({}),
      error: (error as Error).message
    }
  }
})
const currentHistory = computed(() =>
  history.value.filter(entry => entry.namespace === namespaceScope.namespace.value).slice(0, 6)
)
const executionDuration = computed(() => {
  const runtimeDuration = queryDiagnostics.value?.execution?.durationMs
  return typeof runtimeDuration === 'number'
    ? runtimeDuration
    : queryDiagnostics.value?.clientDurationMs
})

function errorText(error: unknown): string {
  return error instanceof RuntimeRequestError ? error.message : '语义查询失败。'
}

function syncContextRail(): void {
  contextRail.setContext({
    route: 'query',
    eyebrow: 'Semantic workbench',
    title: 'Query Models',
    description: '选择模型、恢复当前空间的历史请求，或编写受治理查询 DSL。',
    loading: modelsLoading.value,
    filterable: true,
    emptyText: '当前 namespace 没有可查询模型或历史。',
    sections: [
      {
        id: 'models',
        label: `${models.value.length} available`,
        items: models.value.map(item => ({
          id: `model:${item}`,
          label: item,
          meta: 'semantic query model',
          badge: 'QM',
          active: model.value === item,
          action: () => {
            model.value = item
            syncContextRail()
          }
        }))
      },
      {
        id: 'query-history',
        label: `${currentHistory.value.length} recent in ${namespaceScope.label.value}`,
        items: currentHistory.value.map(entry => ({
          id: `history:${entry.id}`,
          label: `${entry.mode.toUpperCase()} · ${entry.model}`,
          meta: `${entry.status} · ${entry.rows} rows · ${entry.durationMs} ms`,
          badge: entry.status,
          action: () => restoreHistory(entry)
        }))
      }
    ]
  })
}

async function loadModels(): Promise<void> {
  const requestScope = namespaceScope.snapshot()
  modelsLoading.value = true
  syncContextRail()
  try {
    const result = await runtimeApi.get<ModelCatalog>('models', { format: 'json', fieldLimit: 0 })
    if (!namespaceScope.isCurrent(requestScope)) return
    models.value = result.data?.models || []
    model.value = models.value.includes(model.value)
      ? model.value
      : models.value.includes('FactOrderQueryModel')
        ? 'FactOrderQueryModel'
        : models.value[0] || ''
  } catch (error) {
    if (!namespaceScope.isCurrent(requestScope)) return
    ElMessage.error(errorText(error))
  } finally {
    if (namespaceScope.isCurrent(requestScope)) {
      modelsLoading.value = false
      syncContextRail()
    }
  }
}

function clearResult(): void {
  rows.value = []
  queryDiagnostics.value = null
  warnings.value = []
  runError.value = ''
}

function resetNamespaceState(): void {
  models.value = []
  model.value = ''
  clearResult()
  busy.value = false
  syncContextRail()
}

function recordHistory(entry: Omit<QueryHistoryEntry, 'id' | 'time'>): void {
  history.value.unshift({
    ...entry,
    id: historyId++,
    time: new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    }).format(new Date())
  })
  history.value = history.value.slice(0, 18)
  syncContextRail()
}

function restoreHistory(entry: QueryHistoryEntry): void {
  model.value = entry.model
  mode.value = entry.mode
  payload.value = entry.payload
  clearResult()
  syncContextRail()
  ElMessage.success('已恢复历史请求，尚未自动执行。')
}

function formatPayload(): void {
  if (!payloadState.value.valid || !payloadState.value.value) {
    ElMessage.error(payloadState.value.error)
    return
  }
  payload.value = prettyJson(payloadState.value.value)
}

function restoreExample(): void {
  payload.value = prettyJson(examplePayload)
  clearResult()
  ElMessage.success('已恢复查询示例，尚未发送。')
}

function exportCsv(): void {
  const csv = queryRowsToCsv(rows.value)
  if (!csv) {
    ElMessage.warning('当前没有可导出的查询结果。')
    return
  }
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${model.value.trim() || 'query-result'}-${namespaceScope.namespace.value || 'empty'}.csv`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

async function run(nextMode = mode.value): Promise<void> {
  if (!model.value.trim()) {
    ElMessage.warning('请选择或输入 QM 模型名。')
    return
  }
  if (!payloadState.value.valid || !payloadState.value.value) {
    ElMessage.error(payloadState.value.error)
    return
  }

  mode.value = nextMode
  const requestScope = namespaceScope.snapshot()
  const requestModel = model.value.trim()
  const requestPayload = payload.value
  const startedAt = performance.now()
  busy.value = true
  clearResult()
  try {
    const result = await runtimeApi.post<QueryResponse>(
      `query/${encodeURIComponent(requestModel)}/${nextMode}`,
      payloadState.value.value
    )
    if (!namespaceScope.isCurrent(requestScope)) return
    const durationMs = Math.max(0, Math.round(performance.now() - startedAt))
    rows.value = normalizeResultRows(result.items || [])
    warnings.value = result.warnings || []
    queryDiagnostics.value = {
      total: result.total,
      hasNext: result.hasNext,
      pagination: result.pagination,
      execution: result.execution,
      clientDurationMs: durationMs
    }
    recordHistory({
      namespace: requestScope.namespace,
      model: requestModel,
      mode: nextMode,
      status: 'SUCCESS',
      rows: rows.value.length,
      durationMs,
      payload: requestPayload
    })
    ElMessage.success(nextMode === 'validate' ? '查询 DSL 校验通过。' : `查询完成，返回 ${rows.value.length} 行。`)
  } catch (error) {
    if (!namespaceScope.isCurrent(requestScope)) return
    const durationMs = Math.max(0, Math.round(performance.now() - startedAt))
    const runtimeError = error instanceof RuntimeRequestError ? error : null
    runError.value = `${runtimeError?.code || 'QUERY_FAILED'} · ${errorText(error)}`
    recordHistory({
      namespace: requestScope.namespace,
      model: requestModel,
      mode: nextMode,
      status: 'FAILED',
      rows: 0,
      durationMs,
      payload: requestPayload,
      errorCode: runtimeError?.code
    })
    ElMessage.error(errorText(error))
  } finally {
    if (namespaceScope.isCurrent(requestScope)) busy.value = false
  }
}

contextRail.setContext({
  route: 'query',
  eyebrow: 'Semantic workbench',
  title: 'Query Models',
  description: '选择模型、恢复当前空间的历史请求，或编写受治理查询 DSL。',
  loading: true,
  filterable: true,
  emptyText: '当前 namespace 没有可查询模型或历史。',
  sections: []
})
watch(namespaceScope.namespace, () => {
  resetNamespaceState()
  void loadModels()
})
watch(model, syncContextRail)
onMounted(() => {
  resetNamespaceState()
  void loadModels()
})
onBeforeUnmount(() => contextRail.clearContext('query'))
</script>

<template>
  <PageHeader
    eyebrow="Semantic workbench"
    title="查询 DSL"
    description="选择现有 QM，理解 payload 后分别执行 validate 或 execute；结果与历史只属于当前数据与模型空间。"
  />

  <section class="query-manifest" aria-label="查询命令上下文">
    <div :aria-label="`当前空间 ${namespaceScope.label.value}`">
      <span>NAMESPACE</span>
      <strong>{{ namespaceScope.label.value }}</strong>
    </div>
    <div>
      <span>QUERY MODEL</span>
      <strong>{{ model || '未选择' }}</strong>
    </div>
    <div>
      <span>MODE</span>
      <strong>{{ mode.toUpperCase() }}</strong>
    </div>
    <div :class="{ invalid: !payloadState.valid }">
      <span>PAYLOAD</span>
      <strong>{{ payloadState.valid ? 'JSON READY' : 'JSON INVALID' }}</strong>
    </div>
  </section>

  <div class="toolbar query-toolbar">
    <label class="console-field">
      <span class="console-label">QM 模型</span>
      <input v-model="model" class="console-input" list="query-models" placeholder="例如 OrderModel" autocomplete="off">
      <datalist id="query-models">
        <option v-for="item in models" :key="item" :value="item" />
      </datalist>
    </label>
    <div class="toolbar-spacer" />
    <button class="console-button compact" type="button" @click="restoreExample">恢复示例</button>
    <button class="console-button compact" type="button" :disabled="!payloadState.valid" @click="formatPayload">格式化 JSON</button>
  </div>

  <div class="workbench-grid query-workbench">
    <section class="workbench-editor">
      <div class="workbench-toolbar">
        <span class="console-panel-kicker">QUERY PAYLOAD · JSON</span>
        <button class="console-button compact" type="button" :disabled="busy || !payloadState.valid" @click="run('validate')">
          {{ busy && mode === 'validate' ? '校验中…' : '校验' }}
        </button>
        <button class="console-button compact primary" type="button" :disabled="busy || !payloadState.valid" @click="run('execute')">
          {{ busy && mode === 'execute' ? '运行中…' : '运行查询' }}
        </button>
      </div>
      <label>
        <span class="visually-hidden">查询 DSL JSON</span>
        <textarea
          v-model="payload"
          class="console-textarea query-editor"
          :aria-invalid="!payloadState.valid"
          spellcheck="false"
        />
      </label>
      <div v-if="!payloadState.valid" class="payload-error" role="alert">{{ payloadState.error }}</div>
      <div v-else class="payload-intelligence" aria-label="查询 Payload 摘要">
        <span><small>COLUMNS</small><strong>{{ payloadState.summary.columns }}</strong></span>
        <span><small>SLICE</small><strong>{{ payloadState.summary.slices }}</strong></span>
        <span><small>GROUP BY</small><strong>{{ payloadState.summary.groups }}</strong></span>
        <span><small>ORDER BY</small><strong>{{ payloadState.summary.ordering }}</strong></span>
        <span><small>PAGE START / LIMIT</small><strong>{{ payloadState.summary.page }}</strong></span>
      </div>
      <div class="workbench-hint">请求固定发送到当前页面同源的 /api/v1/query/{model}，不会接受可编辑 Runtime 地址。</div>
    </section>

    <section class="workbench-result">
      <div class="console-panel-head">
        <span class="console-panel-title">查询结果</span>
        <div class="result-head-actions">
          <span class="console-panel-kicker">{{ rows.length }} ROWS</span>
          <button class="console-button compact" type="button" :disabled="!rows.length || busy" @click="exportCsv">
            导出 CSV
          </button>
        </div>
      </div>
      <div v-if="runError" class="notice error-notice" role="alert">{{ runError }}</div>
      <div v-if="warnings.length" class="notice">{{ warnings.join(' · ') }}</div>
      <div v-if="queryDiagnostics" class="query-diagnostic-strip" aria-label="查询执行诊断">
        <div><span>TOTAL</span><strong>{{ queryDiagnostics.total ?? '—' }}</strong></div>
        <div><span>RETURNED</span><strong>{{ rows.length }}</strong></div>
        <div><span>HAS NEXT</span><strong>{{ queryDiagnostics.hasNext ? 'YES' : 'NO' }}</strong></div>
        <div><span>DURATION</span><strong>{{ executionDuration ?? '—' }} ms</strong></div>
      </div>
      <RuntimeResultTable :rows="rows" :loading="busy" />
      <details class="diagnostics-details">
        <summary>分页与 Runtime 执行诊断</summary>
        <pre class="raw-output">{{ queryDiagnostics ? prettyJson({
          pagination: queryDiagnostics.pagination,
          execution: queryDiagnostics.execution,
          clientDurationMs: queryDiagnostics.clientDurationMs
        }) : '暂无诊断。' }}</pre>
      </details>
    </section>
  </div>

  <section class="query-history" aria-labelledby="query-history-title">
    <div class="console-panel-head">
      <div>
        <span class="console-panel-kicker">SESSION ONLY / NAMESPACE SCOPED</span>
        <h2 id="query-history-title">当前空间查询历史</h2>
      </div>
      <span class="status-chip">{{ currentHistory.length }} RECENT</span>
    </div>
    <div v-if="currentHistory.length" class="query-history-list">
      <button v-for="entry in currentHistory" :key="entry.id" type="button" @click="restoreHistory(entry)">
        <span>{{ entry.time }}</span>
        <strong>{{ entry.model }}</strong>
        <code>{{ entry.mode.toUpperCase() }}</code>
        <small>{{ entry.status }} · {{ entry.rows }} rows · {{ entry.durationMs }} ms</small>
        <i aria-hidden="true">↺</i>
      </button>
    </div>
    <div v-else class="history-empty">当前 Namespace 尚无查询记录。执行校验或查询后会显示在这里。</div>
  </section>
</template>

<style scoped>
.query-manifest {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  margin-bottom: 12px;
  border: 1px solid var(--console-line-strong);
  background: var(--console-line);
}

.query-manifest > div {
  min-width: 0;
  padding: 13px;
  background: var(--console-panel);
}

.query-manifest > div.invalid {
  background:
    repeating-linear-gradient(135deg, transparent 0 9px, var(--console-hatch-line) 9px 10px),
    var(--console-panel);
}

.query-manifest span,
.query-manifest strong {
  display: block;
  overflow: hidden;
  font-family: var(--console-mono);
  text-overflow: ellipsis;
}

.query-manifest span {
  color: var(--console-dim);
  font-size: 9px;
}

.query-manifest strong {
  margin-top: 6px;
  font-size: 11px;
}

.query-toolbar {
  margin-bottom: 12px;
}

.query-workbench {
  align-items: stretch;
}

.query-editor {
  min-height: 360px;
}

.query-editor[aria-invalid="true"] {
  border-color: var(--console-paper);
  background:
    repeating-linear-gradient(135deg, transparent 0 12px, var(--console-hatch-line) 12px 13px),
    var(--console-panel);
}

.payload-error {
  padding: 12px 14px;
  border-top: 3px double var(--console-line-strong);
  font: 10px/1.5 var(--console-mono);
}

.payload-intelligence {
  display: grid;
  grid-template-columns: repeat(4, .7fr) 1.4fr;
  gap: 1px;
  border-top: 1px solid var(--console-line);
  background: var(--console-line);
}

.payload-intelligence span {
  min-width: 0;
  padding: 10px;
  background: var(--console-panel-2);
}

.payload-intelligence small,
.payload-intelligence strong {
  display: block;
  overflow: hidden;
  font-family: var(--console-mono);
  text-overflow: ellipsis;
}

.payload-intelligence small {
  color: var(--console-dim);
  font-size: 8px;
}

.payload-intelligence strong {
  margin-top: 5px;
  font-size: 10px;
}

.result-head-actions {
  display: flex;
  align-items: center;
  gap: 9px;
}

.query-diagnostic-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  border-bottom: 1px solid var(--console-line);
  background: var(--console-line);
}

.query-diagnostic-strip div {
  min-width: 0;
  padding: 11px;
  background: var(--console-panel-2);
}

.query-diagnostic-strip span,
.query-diagnostic-strip strong {
  display: block;
  font-family: var(--console-mono);
}

.query-diagnostic-strip span {
  color: var(--console-dim);
  font-size: 8px;
}

.query-diagnostic-strip strong {
  margin-top: 5px;
  font-size: 11px;
}

.query-history {
  margin-top: 14px;
  border: 1px solid var(--console-line-strong);
  background: var(--console-panel);
}

.query-history .console-panel-head h2 {
  margin: 5px 0 0;
  font-size: 16px;
}

.query-history-list {
  display: grid;
  gap: 1px;
  background: var(--console-line);
}

.query-history-list button {
  display: grid;
  grid-template-columns: 90px minmax(180px, 1fr) 90px minmax(180px, 1fr) 30px;
  align-items: center;
  gap: 12px;
  min-height: 50px;
  padding: 10px 14px;
  border: 0;
  border-radius: 0;
  background: var(--console-panel);
  color: var(--console-text);
  cursor: pointer;
  text-align: left;
}

.query-history-list button:hover,
.query-history-list button:focus-visible {
  background: var(--console-panel-2);
}

.query-history-list span,
.query-history-list code,
.query-history-list small {
  color: var(--console-dim);
  font: 9px/1.4 var(--console-mono);
}

.query-history-list strong {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
}

.query-history-list i {
  font-style: normal;
  font-size: 18px;
}

.history-empty {
  min-height: 100px;
  display: grid;
  place-items: center;
  color: var(--console-dim);
  font: 10px/1.5 var(--console-mono);
  text-align: center;
}

@media (max-width: 760px) {
  .query-manifest {
    grid-template-columns: 1fr 1fr;
  }

  .payload-intelligence {
    grid-template-columns: 1fr 1fr;
  }

  .payload-intelligence span:last-child {
    grid-column: 1 / -1;
  }

  .query-diagnostic-strip {
    grid-template-columns: 1fr 1fr;
  }

  .query-history-list button {
    grid-template-columns: 1fr 1fr;
  }

  .query-history-list i {
    display: none;
  }
}
</style>
