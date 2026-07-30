<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import RuntimeResultTable from '@/components/RuntimeResultTable.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { useRuntimeSession } from '@/stores/session'
import { normalizeResultRows, prettyJson } from '@/utils/json'

interface ModelItem {
  model: string
  caption?: string
  description?: string
  namespace?: string
  bundleName?: string
  sourceKnown?: boolean
  physicalTables?: string[]
  primaryTimeField?: string
  fieldCount?: number
}

interface ModelCatalog {
  data?: {
    models?: string[]
    count?: number
    items?: ModelItem[]
  }
}

interface LifecycleResult {
  valid?: boolean
  namespace?: string
  path?: string
  scope?: string
  totalFiles?: number
  validFiles?: number
  invalidFiles?: number
  cascadingErrors?: number
  loadedCount?: number
  failedCount?: number
  refreshedCount?: number
  preservedCount?: number
  durationMs?: number
  beforeCatalogGeneration?: string
  afterCatalogGeneration?: string
  sourceRevision?: string
  catalogState?: string
  refreshedModels?: string[]
  errors?: unknown[]
  warnings?: unknown[]
  failures?: unknown[]
}

const session = useRuntimeSession()
const loading = ref(true)
const busy = ref('')
const errorMessage = ref('')
const search = ref('')
const models = ref<ModelItem[]>([])
const selected = ref<string[]>([])
const describeModel = ref('')
const describeOutput = ref('')
const lifecycle = ref<LifecycleResult | null>(null)
const lifecycleRows = ref<Record<string, unknown>[]>([])
const validatePath = ref('')

const filteredModels = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return models.value
  return models.value.filter(item =>
    [item.model, item.caption, item.description, item.namespace, item.bundleName]
      .some(value => value?.toLowerCase().includes(keyword))
  )
})

function errorText(error: unknown): string {
  return error instanceof RuntimeRequestError ? error.message : '模型操作失败。'
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await runtimeApi.get<ModelCatalog>('models', {
      format: 'json',
      fieldLimit: 6
    })
    models.value = result.data?.items || []
    selected.value = selected.value.filter(name => models.value.some(item => item.model === name))
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

function toggleModel(name: string): void {
  selected.value = selected.value.includes(name)
    ? selected.value.filter(item => item !== name)
    : [...selected.value, name]
}

async function describe(item: ModelItem): Promise<void> {
  busy.value = `describe:${item.model}`
  describeModel.value = item.model
  describeOutput.value = ''
  try {
    const result = await runtimeApi.post<Record<string, unknown>>(
      `models/${encodeURIComponent(item.model)}/describe`,
      { format: 'json', namespace: session.namespace.value, includeExamples: true }
    )
    describeOutput.value = typeof result.content === 'string'
      ? result.content
      : prettyJson(result.data || result)
  } catch (error) {
    ElMessage.error(errorText(error))
  } finally {
    busy.value = ''
  }
}

function setLifecycle(result: LifecycleResult): void {
  lifecycle.value = result
  const detail = result.errors?.length
    ? result.errors
    : result.failures?.length
      ? result.failures
      : result.warnings?.length
        ? result.warnings
        : [result]
  lifecycleRows.value = normalizeResultRows(detail)
}

async function validateModels(): Promise<void> {
  if (!validatePath.value.trim()) {
    ElMessage.warning('请输入需要校验的模型路径。')
    return
  }
  busy.value = 'validate'
  try {
    const result = await runtimeApi.post<LifecycleResult>('models/validate', {
      path: validatePath.value.trim(),
      namespace: session.namespace.value,
      watch: false,
      clearExisting: false,
      includeStackTrace: false
    })
    setLifecycle(result)
    ElMessage.success(result.valid ? '模型校验通过。' : '模型校验完成，存在问题。')
  } catch (error) {
    ElMessage.error(errorText(error))
  } finally {
    busy.value = ''
  }
}

async function refreshModels(scope: 'selected' | 'all'): Promise<void> {
  if (scope === 'selected' && !selected.value.length) {
    ElMessage.warning('请先选择至少一个模型。')
    return
  }
  try {
    await ElMessageBox.confirm(
      scope === 'all'
        ? `刷新 namespace ${session.namespace.value} 的全部模型？`
        : `刷新已选择的 ${selected.value.length} 个模型？`,
      '确认模型刷新',
      { type: 'warning', confirmButtonText: '确认刷新', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  busy.value = `refresh:${scope}`
  try {
    const result = await runtimeApi.post<LifecycleResult>('models/refresh', {
      namespace: session.namespace.value,
      models: scope === 'selected' ? selected.value : []
    })
    setLifecycle(result)
    ElMessage.success(`模型刷新完成：${result.refreshedCount ?? result.loadedCount ?? 0} 成功。`)
    await load()
  } catch (error) {
    ElMessage.error(errorText(error))
  } finally {
    busy.value = ''
  }
}

onMounted(load)
</script>

<template>
  <PageHeader
    eyebrow="Semantic catalog"
    title="语义模型"
    description="查看当前 namespace 可发现的 QM，执行描述、隔离校验与原子刷新，并保留 catalog generation 诊断。"
  >
    <template #actions>
      <button class="console-button ghost" type="button" :disabled="loading" @click="load">重新读取</button>
      <button class="console-button" type="button" :disabled="Boolean(busy)" @click="refreshModels('selected')">刷新已选</button>
      <button class="console-button primary" type="button" :disabled="Boolean(busy)" @click="refreshModels('all')">刷新全部</button>
    </template>
  </PageHeader>

  <div v-if="errorMessage" class="notice error-notice" role="alert">{{ errorMessage }}</div>

  <div class="toolbar">
    <label class="console-field">
      <span class="console-label">搜索模型</span>
      <input v-model="search" class="console-input" type="search" placeholder="名称、描述、Bundle">
    </label>
    <div class="toolbar-spacer" />
    <span class="status-chip">{{ filteredModels.length }} models</span>
    <span class="status-chip">{{ selected.length }} selected</span>
  </div>

  <div class="model-grid">
    <article v-for="item in filteredModels" :key="item.model" class="model-card console-card">
      <div class="model-card-top">
        <label class="model-select">
          <input
            type="checkbox"
            :checked="selected.includes(item.model)"
            :aria-label="`选择 ${item.model}`"
            @change="toggleModel(item.model)"
          >
        </label>
        <span class="status-chip" :class="{ warning: !item.sourceKnown }">
          {{ item.sourceKnown ? 'SOURCE KNOWN' : 'SOURCE UNKNOWN' }}
        </span>
      </div>
      <div class="model-code">{{ item.model }}</div>
      <h2>{{ item.caption || item.model }}</h2>
      <p>{{ item.description || '此模型暂未提供语义描述。' }}</p>
      <div class="model-meta">
        <span>{{ item.fieldCount ?? '—' }} fields</span>
        <span>{{ item.bundleName || 'runtime catalog' }}</span>
        <span>{{ item.primaryTimeField || 'no primary time' }}</span>
      </div>
      <button
        class="console-button compact"
        type="button"
        :disabled="busy === `describe:${item.model}`"
        @click="describe(item)"
      >
        {{ busy === `describe:${item.model}` ? '读取中…' : '描述模型' }}
      </button>
    </article>
  </div>

  <div v-if="!loading && !filteredModels.length" class="empty-state console-panel">
    <div><strong>没有可见模型</strong>检查 namespace、Bundle 或模型权限后重新读取。</div>
  </div>

  <div class="split-grid model-detail-grid">
    <section class="console-panel">
      <div class="console-panel-head">
        <span class="console-panel-title">模型描述</span>
        <span class="console-panel-kicker">{{ describeModel || 'SELECT A MODEL' }}</span>
      </div>
      <pre class="raw-output describe-output">{{ describeOutput || '从上方模型卡片选择“描述模型”。' }}</pre>
    </section>

    <section class="console-panel">
      <div class="console-panel-head">
        <span class="console-panel-title">路径校验</span>
        <span class="console-panel-kicker">DETACHED VALIDATION</span>
      </div>
      <div class="console-panel-body dialog-form">
        <label class="console-field">
          <span class="console-label">模型路径</span>
          <input v-model="validatePath" class="console-input" placeholder="/opt/foggy/models" autocomplete="off">
        </label>
        <button class="console-button primary" type="button" :disabled="Boolean(busy)" @click="validateModels">
          {{ busy === 'validate' ? '校验中…' : '校验路径' }}
        </button>
        <div class="notice">校验在隔离候选中进行，不会因失败覆盖当前可用 catalog。</div>
      </div>
    </section>
  </div>

  <section class="console-panel lifecycle-panel">
    <div class="console-panel-head">
      <span class="console-panel-title">生命周期诊断</span>
      <span class="console-panel-kicker">{{ lifecycle?.catalogState || 'NO RUN' }}</span>
      <div class="toolbar-spacer" />
      <span v-if="lifecycle?.durationMs !== undefined" class="status-chip">{{ lifecycle.durationMs }} ms</span>
    </div>
    <div v-if="lifecycle" class="lifecycle-strip">
      <div><span>BEFORE</span><strong>{{ lifecycle.beforeCatalogGeneration || '—' }}</strong></div>
      <div><span>AFTER</span><strong>{{ lifecycle.afterCatalogGeneration || '—' }}</strong></div>
      <div><span>REFRESHED</span><strong>{{ lifecycle.refreshedCount ?? lifecycle.loadedCount ?? lifecycle.validFiles ?? 0 }}</strong></div>
      <div><span>FAILED</span><strong>{{ lifecycle.failedCount ?? lifecycle.invalidFiles ?? 0 }}</strong></div>
    </div>
    <RuntimeResultTable :rows="lifecycleRows" :loading="busy.startsWith('refresh') || busy === 'validate'" />
  </section>
</template>

<style scoped>
.model-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(270px, 1fr));
  gap: 14px;
}

.model-card {
  min-height: 286px;
  display: flex;
  flex-direction: column;
  padding: 20px;
}

.model-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.model-select {
  min-width: 44px;
  min-height: 44px;
  display: grid;
  place-items: center;
  margin: -12px 0 0 -12px;
}

.model-code {
  margin-top: 13px;
  color: var(--console-lime);
  font: 11px/1.4 var(--console-mono);
}

.model-card h2 {
  margin: 8px 0;
  font-size: 21px;
}

.model-card p {
  flex: 1;
  margin: 0 0 18px;
  color: var(--console-muted);
  font-size: 13px;
  line-height: 1.6;
}

.model-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 12px;
  margin-bottom: 16px;
  color: var(--console-dim);
  font: 10px/1.4 var(--console-mono);
}

.model-detail-grid,
.lifecycle-panel {
  margin-top: 14px;
}

.describe-output {
  min-height: 266px;
  border-radius: 0;
}

.lifecycle-strip {
  display: grid;
  grid-template-columns: 2fr 2fr 1fr 1fr;
  gap: 1px;
  padding: 1px;
  background: var(--console-line);
}

.lifecycle-strip > div {
  min-width: 0;
  padding: 14px 16px;
  background: var(--console-panel);
}

.lifecycle-strip span {
  display: block;
  color: var(--console-dim);
  font: 10px/1 var(--console-mono);
}

.lifecycle-strip strong {
  display: block;
  margin-top: 8px;
  overflow: hidden;
  color: var(--console-text);
  font: 12px/1.4 var(--console-mono);
  text-overflow: ellipsis;
}

@media (max-width: 780px) {
  .lifecycle-strip {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
