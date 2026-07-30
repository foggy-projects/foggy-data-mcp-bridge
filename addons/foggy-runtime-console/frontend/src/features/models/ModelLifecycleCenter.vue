<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import RuntimeResultTable from '@/components/RuntimeResultTable.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import {
  lifecycleDiagnosticRows,
  summarizeLifecycle,
  type LifecycleSummary
} from './modelLifecycle'
import type { LifecycleResult } from '@/features/namespace/types'

type OperationKind = 'validate' | 'selected' | 'all'

interface LifecycleFailure {
  code: string
  phase: string
  message: string
  suggestedNextAction: string
}

interface LifecycleHistoryEntry {
  id: number
  kind: OperationKind
  label: string
  time: string
  state: string
  successCount: number
  failedCount: number
  failed: boolean
}

const props = defineProps<{
  namespace: string
  selectedModels: string[]
  totalModels: number
}>()
const emit = defineEmits<{ reload: [] }>()
const validatePath = ref('')
const busy = ref<OperationKind | ''>('')
const lastKind = ref<OperationKind | ''>('')
const result = ref<LifecycleResult | null>(null)
const failure = ref<LifecycleFailure | null>(null)
const history = ref<LifecycleHistoryEntry[]>([])
let historyId = 1

const namespaceLabel = computed(() => props.namespace || '空 Namespace')
const summary = computed<LifecycleSummary | null>(() =>
  result.value ? summarizeLifecycle(result.value) : null
)
const diagnosticRows = computed(() =>
  result.value ? lifecycleDiagnosticRows(result.value) : []
)
const latestState = computed(() =>
  failure.value ? 'FAILED' : summary.value?.catalogState || 'IDLE'
)

function errorText(error: unknown): string {
  return error instanceof RuntimeRequestError ? error.message : '模型生命周期操作失败。'
}

function historyLabel(kind: OperationKind): string {
  if (kind === 'validate') return '候选校验'
  if (kind === 'selected') return '刷新已选'
  return '刷新全部'
}

function recordSuccess(kind: OperationKind, lifecycle: LifecycleResult): void {
  const normalized = summarizeLifecycle(lifecycle)
  history.value.unshift({
    id: historyId++,
    kind,
    label: historyLabel(kind),
    time: new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    }).format(new Date()),
    state: normalized.catalogState,
    successCount: normalized.successCount,
    failedCount: normalized.failedCount,
    failed: false
  })
  history.value = history.value.slice(0, 6)
}

function recordFailure(kind: OperationKind, error: unknown): void {
  const runtimeError = error instanceof RuntimeRequestError ? error : null
  failure.value = {
    code: runtimeError?.code || 'RUNTIME_REQUEST_FAILED',
    phase: runtimeError?.phase || `models.${kind === 'validate' ? 'validate' : 'refresh'}`,
    message: errorText(error),
    suggestedNextAction: runtimeError?.suggestedNextAction || '检查输入与 Runtime 状态后重试。'
  }
  history.value.unshift({
    id: historyId++,
    kind,
    label: historyLabel(kind),
    time: new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    }).format(new Date()),
    state: failure.value.code,
    successCount: 0,
    failedCount: 1,
    failed: true
  })
  history.value = history.value.slice(0, 6)
}

async function validateModels(): Promise<void> {
  if (!validatePath.value.trim()) {
    ElMessage.warning('请输入需要校验的模型路径。')
    return
  }
  const kind: OperationKind = 'validate'
  busy.value = kind
  lastKind.value = kind
  result.value = null
  failure.value = null
  try {
    const lifecycle = await runtimeApi.post<LifecycleResult>('models/validate', {
      path: validatePath.value.trim(),
      namespace: props.namespace,
      watch: false,
      clearExisting: false,
      includeStackTrace: false
    })
    result.value = lifecycle
    recordSuccess(kind, lifecycle)
    ElMessage.success(lifecycle.valid ? '候选模型校验通过。' : '候选模型校验完成，存在问题。')
  } catch (error) {
    recordFailure(kind, error)
    ElMessage.error(errorText(error))
  } finally {
    busy.value = ''
  }
}

async function refreshModels(kind: 'selected' | 'all'): Promise<void> {
  if (kind === 'selected' && !props.selectedModels.length) {
    ElMessage.warning('请先选择至少一个模型。')
    return
  }
  try {
    await ElMessageBox.confirm(
      kind === 'all'
        ? `刷新空间 ${namespaceLabel.value} 的全部 ${props.totalModels} 个可见模型，并原子发布新 catalog？`
        : `刷新并发布已选择的 ${props.selectedModels.length} 个模型：${props.selectedModels.join('、')}？`,
      '确认模型刷新',
      {
        type: 'warning',
        confirmButtonText: kind === 'all' ? '刷新全部并发布' : '确认刷新',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  busy.value = kind
  lastKind.value = kind
  result.value = null
  failure.value = null
  try {
    const lifecycle = await runtimeApi.post<LifecycleResult>('models/refresh', {
      namespace: props.namespace,
      models: kind === 'selected' ? props.selectedModels : []
    })
    result.value = lifecycle
    recordSuccess(kind, lifecycle)
    ElMessage.success(`模型刷新完成：${summarizeLifecycle(lifecycle).successCount} 成功。`)
    emit('reload')
  } catch (error) {
    recordFailure(kind, error)
    ElMessage.error(errorText(error))
  } finally {
    busy.value = ''
  }
}

watch(() => props.namespace, () => {
  busy.value = ''
  lastKind.value = ''
  result.value = null
  failure.value = null
  history.value = []
})
</script>

<template>
  <section class="lifecycle-center" aria-labelledby="lifecycle-center-title">
    <div class="lifecycle-manifest">
      <div class="lifecycle-title">
        <span class="console-panel-kicker">MODEL LIFECYCLE / CANDIDATE → PUBLISHED</span>
        <h3 id="lifecycle-center-title">模型生命周期操作中心</h3>
        <p>校验只构建隔离候选；刷新通过校验后原子发布。失败不会覆盖当前 generation。</p>
      </div>
      <div class="lifecycle-context" aria-label="模型生命周期上下文">
        <div><span>NAMESPACE</span><strong>{{ namespaceLabel }}</strong></div>
        <div><span>VISIBLE QM</span><strong>{{ totalModels }}</strong></div>
        <div><span>SELECTED QM</span><strong>{{ selectedModels.length }}</strong></div>
        <div><span>LATEST STATE</span><strong>{{ latestState }}</strong></div>
      </div>
    </div>

    <div class="lifecycle-action-grid">
      <article class="lifecycle-action">
        <div class="action-code">01 / VALIDATE</div>
        <h4>候选路径校验</h4>
        <p>在隔离候选中检查模型文件，不写入当前 catalog。</p>
        <label class="console-field">
          <span class="console-label">模型路径</span>
          <input v-model="validatePath" class="console-input" placeholder="/opt/foggy/models" autocomplete="off">
        </label>
        <button class="console-button" type="button" :disabled="Boolean(busy)" @click="validateModels">
          {{ busy === 'validate' ? '正在校验…' : '校验候选路径' }}
        </button>
      </article>

      <article class="lifecycle-action">
        <div class="action-code">02 / REFRESH SELECTED</div>
        <h4>刷新已选模型</h4>
        <p>只构建并发布当前勾选的 QM；适合局部验证后的最小刷新。</p>
        <div class="selected-models">
          <code v-for="model in selectedModels" :key="model">{{ model }}</code>
          <span v-if="!selectedModels.length">尚未选择模型</span>
        </div>
        <button
          class="console-button"
          type="button"
          :disabled="Boolean(busy) || !selectedModels.length"
          @click="refreshModels('selected')"
        >
          {{ busy === 'selected' ? '正在刷新…' : '刷新已选' }}
        </button>
      </article>

      <article class="lifecycle-action lifecycle-action-risk">
        <div class="action-code">03 / REFRESH ALL</div>
        <h4>刷新全部模型</h4>
        <p>重建当前空间的完整 catalog。确认范围与 Bundle 状态后再执行。</p>
        <div class="all-model-count">{{ totalModels }} <span>VISIBLE QM</span></div>
        <button
          class="console-button primary"
          type="button"
          :disabled="Boolean(busy)"
          @click="refreshModels('all')"
        >
          {{ busy === 'all' ? '正在刷新…' : '刷新全部' }}
        </button>
      </article>
    </div>

    <div v-if="busy || summary || failure" class="lifecycle-result">
      <div class="result-head">
        <span>OPERATION RESULT / {{ lastKind || 'NONE' }}</span>
        <strong>{{ latestState }}</strong>
      </div>
      <div v-if="summary" class="generation-track">
        <div><span>BEFORE</span><strong>{{ summary.beforeGeneration || 'UNCHANGED / N.A.' }}</strong></div>
        <div class="generation-arrow" aria-hidden="true">→</div>
        <div><span>AFTER</span><strong>{{ summary.afterGeneration || 'NO PUBLISH' }}</strong></div>
        <div><span>SUCCESS</span><strong>{{ summary.successCount }}</strong></div>
        <div><span>FAILED</span><strong>{{ summary.failedCount }}</strong></div>
        <div><span>DURATION</span><strong>{{ summary.durationMs === undefined ? '—' : `${summary.durationMs} ms` }}</strong></div>
      </div>
      <div v-if="failure" class="lifecycle-failure" role="alert">
        <span>{{ failure.code }} · {{ failure.phase }}</span>
        <strong>{{ failure.message }}</strong>
        <p>{{ failure.suggestedNextAction }}</p>
      </div>
      <RuntimeResultTable
        v-if="busy || diagnosticRows.length"
        :rows="diagnosticRows"
        :loading="Boolean(busy)"
      />
      <div v-if="summary && !diagnosticRows.length" class="clean-diagnostics">
        Runtime 未返回 warning、error 或 failure 明细。
      </div>
    </div>

    <details class="lifecycle-history">
      <summary>
        <span>SESSION HISTORY</span>
        <strong>{{ history.length }} 次最近操作</strong>
      </summary>
      <div v-if="history.length" class="history-list">
        <div v-for="entry in history" :key="entry.id" :class="{ failed: entry.failed }">
          <span>{{ entry.time }}</span>
          <strong>{{ entry.label }}</strong>
          <code>{{ entry.state }}</code>
          <small>{{ entry.successCount }} success / {{ entry.failedCount }} failed</small>
        </div>
      </div>
      <div v-else class="history-empty">当前 Namespace 尚无生命周期操作。</div>
    </details>
  </section>
</template>

<style scoped>
.lifecycle-center {
  margin-bottom: 16px;
  border: 1px solid var(--console-line-strong);
  background: var(--console-panel);
}

.lifecycle-manifest {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) minmax(360px, 1.25fr);
  border-bottom: 1px solid var(--console-line-strong);
}

.lifecycle-title {
  padding: 22px;
  border-right: 1px solid var(--console-line-strong);
  background:
    linear-gradient(var(--console-grid-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--console-grid-line) 1px, transparent 1px);
  background-size: 24px 24px;
}

.lifecycle-title h3 {
  margin: 8px 0 7px;
  font-size: 19px;
}

.lifecycle-title p,
.lifecycle-action p,
.lifecycle-failure p {
  margin: 0;
  color: var(--console-muted);
  font-size: 12px;
  line-height: 1.6;
}

.lifecycle-context {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  background: var(--console-line);
}

.lifecycle-context div {
  min-width: 0;
  padding: 15px;
  background: var(--console-panel);
}

.lifecycle-context span,
.lifecycle-context strong {
  display: block;
  overflow: hidden;
  font-family: var(--console-mono);
  text-overflow: ellipsis;
}

.lifecycle-context span {
  color: var(--console-dim);
  font-size: 9px;
}

.lifecycle-context strong {
  margin-top: 7px;
  font-size: 12px;
}

.lifecycle-action-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: var(--console-line);
}

.lifecycle-action {
  min-width: 0;
  display: flex;
  align-items: stretch;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  background: var(--console-panel-2);
}

.lifecycle-action-risk {
  background:
    repeating-linear-gradient(135deg, transparent 0 10px, var(--console-hatch-line) 10px 11px),
    var(--console-panel-2);
}

.action-code {
  color: var(--console-dim);
  font: 9px/1 var(--console-mono);
  letter-spacing: .08em;
}

.lifecycle-action h4 {
  margin: 0;
  font-size: 14px;
}

.lifecycle-action .console-button {
  margin-top: auto;
}

.selected-models {
  min-height: 58px;
  display: flex;
  align-content: flex-start;
  flex-wrap: wrap;
  gap: 6px;
  padding: 9px;
  border: 1px dashed var(--console-line);
}

.selected-models code {
  height: max-content;
  padding: 5px 6px;
  border: 1px solid var(--console-line);
  background: var(--console-panel);
  font-size: 9px;
}

.selected-models span {
  color: var(--console-dim);
  font: 10px/1.5 var(--console-mono);
}

.all-model-count {
  min-height: 58px;
  display: flex;
  align-items: baseline;
  gap: 8px;
  font: 700 30px/1 var(--console-mono);
}

.all-model-count span {
  color: var(--console-dim);
  font-size: 9px;
}

.lifecycle-result {
  border-top: 1px solid var(--console-line-strong);
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 13px 16px;
}

.result-head span,
.result-head strong {
  font: 10px/1 var(--console-mono);
}

.result-head span {
  color: var(--console-dim);
}

.generation-track {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) 34px minmax(0, 1.5fr) .65fr .65fr .8fr;
  gap: 1px;
  border-top: 1px solid var(--console-line);
  border-bottom: 1px solid var(--console-line);
  background: var(--console-line);
}

.generation-track > div:not(.generation-arrow) {
  min-width: 0;
  padding: 12px;
  background: var(--console-panel-2);
}

.generation-track span,
.generation-track strong {
  display: block;
  overflow: hidden;
  font-family: var(--console-mono);
  text-overflow: ellipsis;
}

.generation-track span {
  color: var(--console-dim);
  font-size: 8px;
}

.generation-track strong {
  margin-top: 5px;
  font-size: 10px;
}

.generation-arrow {
  display: grid;
  place-items: center;
  background: var(--console-paper);
  color: var(--console-inverse);
  font: 16px/1 var(--console-mono);
}

.lifecycle-failure {
  margin: 12px;
  padding: 14px;
  border: 3px double var(--console-line-strong);
}

.lifecycle-failure span,
.lifecycle-failure strong {
  display: block;
  font-family: var(--console-mono);
}

.lifecycle-failure span {
  color: var(--console-dim);
  font-size: 9px;
}

.lifecycle-failure strong {
  margin: 7px 0;
  font-size: 12px;
}

.clean-diagnostics {
  padding: 12px 16px;
  border-top: 1px dashed var(--console-line);
  color: var(--console-dim);
  font: 10px/1.5 var(--console-mono);
}

.lifecycle-history {
  border-top: 1px solid var(--console-line-strong);
}

.lifecycle-history summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
}

.lifecycle-history summary span {
  color: var(--console-dim);
  font: 9px/1 var(--console-mono);
}

.lifecycle-history summary strong {
  font-size: 11px;
}

.history-list {
  display: grid;
  gap: 1px;
  padding: 0 12px 12px;
  background: var(--console-line);
}

.history-list > div {
  display: grid;
  grid-template-columns: 82px 1fr 1fr auto;
  gap: 10px;
  padding: 10px;
  background: var(--console-panel-2);
}

.history-list span,
.history-list code,
.history-list small {
  color: var(--console-dim);
  font: 9px/1.4 var(--console-mono);
}

.history-list .failed {
  border-left: 3px double var(--console-line-strong);
}

.history-empty {
  padding: 20px;
  color: var(--console-dim);
  font: 10px/1.5 var(--console-mono);
  text-align: center;
}

@media (max-width: 900px) {
  .lifecycle-manifest {
    grid-template-columns: 1fr;
  }

  .lifecycle-title {
    border-right: 0;
    border-bottom: 1px solid var(--console-line-strong);
  }

  .lifecycle-action-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .lifecycle-context {
    grid-template-columns: 1fr 1fr;
  }

  .generation-track {
    grid-template-columns: 1fr 28px 1fr;
  }

  .generation-track > div:nth-child(n + 4) {
    grid-column: 1 / -1;
  }

  .history-list > div {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
