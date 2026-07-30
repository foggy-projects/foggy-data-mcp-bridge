<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import RuntimeResultTable from '@/components/RuntimeResultTable.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { normalizeResultRows, parseJsonObject, prettyJson } from '@/utils/json'

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

const models = ref<string[]>([])
const model = ref('')
const mode = ref<'validate' | 'execute'>('execute')
const payload = ref(prettyJson({
  columns: [],
  slice: [],
  groupBy: [],
  orderBy: [],
  page: { start: 0, limit: 100 }
}))
const busy = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const diagnostics = ref('')
const warnings = ref<string[]>([])

function errorText(error: unknown): string {
  return error instanceof RuntimeRequestError ? error.message : '语义查询失败。'
}

async function loadModels(): Promise<void> {
  try {
    const result = await runtimeApi.get<ModelCatalog>('models', { format: 'json', fieldLimit: 0 })
    models.value = result.data?.models || []
    model.value ||= models.value[0] || ''
  } catch (error) {
    ElMessage.error(errorText(error))
  }
}

async function run(nextMode = mode.value): Promise<void> {
  if (!model.value.trim()) {
    ElMessage.warning('请选择或输入 QM 模型名。')
    return
  }
  let request: Record<string, unknown>
  try {
    request = parseJsonObject(payload.value, '查询 DSL JSON')
  } catch (error) {
    ElMessage.error((error as Error).message)
    return
  }

  mode.value = nextMode
  busy.value = true
  rows.value = []
  diagnostics.value = ''
  warnings.value = []
  try {
    const result = await runtimeApi.post<QueryResponse>(
      `query/${encodeURIComponent(model.value.trim())}/${nextMode}`,
      request
    )
    rows.value = normalizeResultRows(result.items || [])
    warnings.value = result.warnings || []
    diagnostics.value = prettyJson({
      total: result.total,
      hasNext: result.hasNext,
      pagination: result.pagination,
      execution: result.execution
    })
    ElMessage.success(nextMode === 'validate' ? '查询 DSL 校验通过。' : `查询完成，返回 ${rows.value.length} 行。`)
  } catch (error) {
    ElMessage.error(errorText(error))
  } finally {
    busy.value = false
  }
}

onMounted(loadModels)
</script>

<template>
  <PageHeader
    eyebrow="Semantic workbench"
    title="查询 DSL"
    description="选择现有 QM，先在浏览器内检查 JSON，再调用 Runtime validate 或 execute。数据权限 Authorization 与管理 Token 独立处理。"
  />

  <div class="toolbar">
    <label class="console-field">
      <span class="console-label">QM 模型</span>
      <input v-model="model" class="console-input" list="query-models" placeholder="例如 OrderModel" autocomplete="off">
      <datalist id="query-models">
        <option v-for="item in models" :key="item" :value="item" />
      </datalist>
    </label>
    <div class="toolbar-spacer" />
    <span class="status-chip">{{ mode.toUpperCase() }}</span>
  </div>

  <div class="workbench-grid">
    <section class="workbench-editor">
      <div class="workbench-toolbar">
        <span class="console-panel-kicker">QUERY PAYLOAD · JSON</span>
        <button class="console-button compact" type="button" :disabled="busy" @click="run('validate')">校验</button>
        <button class="console-button compact primary" type="button" :disabled="busy" @click="run('execute')">
          {{ busy ? '运行中…' : '运行查询' }}
        </button>
      </div>
      <label>
        <span class="visually-hidden">查询 DSL JSON</span>
        <textarea v-model="payload" class="console-textarea" spellcheck="false" />
      </label>
      <div class="workbench-hint">请求固定发送到当前页面同源的 /api/v1/query/{model}，不会接受可编辑 Runtime 地址。</div>
    </section>
    <section class="workbench-result">
      <div class="console-panel-head">
        <span class="console-panel-title">查询结果</span>
        <span class="console-panel-kicker">{{ rows.length }} ROWS</span>
      </div>
      <div v-if="warnings.length" class="notice">{{ warnings.join(' · ') }}</div>
      <RuntimeResultTable :rows="rows" :loading="busy" />
      <details class="diagnostics-details">
        <summary>分页与执行诊断</summary>
        <pre class="raw-output">{{ diagnostics || '暂无诊断。' }}</pre>
      </details>
    </section>
  </div>
</template>
