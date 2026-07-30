<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import RuntimeResultTable from '@/components/RuntimeResultTable.vue'
import ExecutionToolTabs from '@/components/ExecutionToolTabs.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { useRuntimeSession } from '@/stores/session'
import { normalizeResultRows, parseJsonObject, prettyJson } from '@/utils/json'

interface ComposeResponse {
  valid?: boolean
  scriptKind?: string
  mode?: string
  value?: unknown
  sql?: string
  params?: unknown[]
  warnings?: string[]
  diagnostics?: Record<string, unknown>
}

const session = useRuntimeSession()
const script = ref(`query OrderModel {
  columns: ["id"]
  limit: 20
}`)
const params = ref('{}')
const options = ref('{}')
const busy = ref('')
const rows = ref<Record<string, unknown>[]>([])
const output = ref('')
const warnings = ref<string[]>([])

function errorText(error: unknown): string {
  return error instanceof RuntimeRequestError ? error.message : 'Compose 操作失败。'
}

async function run(mode: 'validate' | 'preview' | 'execute'): Promise<void> {
  if (!script.value.trim()) {
    ElMessage.warning('请输入 Compose/CTE 脚本。')
    return
  }
  let parsedParams: Record<string, unknown>
  let parsedOptions: Record<string, unknown>
  try {
    parsedParams = parseJsonObject(params.value, 'Params JSON')
    parsedOptions = parseJsonObject(options.value, 'Options JSON')
  } catch (error) {
    ElMessage.error((error as Error).message)
    return
  }

  if (mode === 'execute') {
    try {
      await ElMessageBox.confirm(
        '执行 Compose 会运行已通过治理检查的脚本。确认当前 namespace、参数与脚本均正确。',
        '确认执行 Compose',
        { type: 'warning', confirmButtonText: '确认执行', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }

  busy.value = mode
  rows.value = []
  output.value = ''
  try {
    const result = await runtimeApi.post<ComposeResponse>(`compose/${mode}`, {
      script: script.value,
      params: parsedParams,
      options: parsedOptions,
      namespace: session.namespace.value
    })
    rows.value = normalizeResultRows(result.value)
    warnings.value = result.warnings || []
    output.value = prettyJson({
      valid: result.valid,
      scriptKind: result.scriptKind,
      mode: result.mode,
      sql: result.sql,
      params: result.params,
      diagnostics: result.diagnostics
    })
    ElMessage.success(`Compose ${mode} 完成。`)
  } catch (error) {
    ElMessage.error(errorText(error))
  } finally {
    busy.value = ''
  }
}
</script>

<template>
  <ExecutionToolTabs />
  <PageHeader
    eyebrow="Governed composition"
    title="Compose / CTE"
    description="面向受限 Compose 与 CTE 的校验、预览和执行入口。执行前需要二次确认，服务端仍负责最终语法与治理边界。"
  />

  <div class="workbench-grid">
    <section class="workbench-editor">
      <div class="workbench-toolbar">
        <span class="console-panel-kicker">COMPOSE SCRIPT</span>
        <button class="console-button compact" type="button" :disabled="Boolean(busy)" @click="run('validate')">校验</button>
        <button class="console-button compact" type="button" :disabled="Boolean(busy)" @click="run('preview')">预览</button>
        <button class="console-button compact primary" type="button" :disabled="Boolean(busy)" @click="run('execute')">
          {{ busy === 'execute' ? '执行中…' : '执行' }}
        </button>
      </div>
      <label><span class="visually-hidden">Compose 脚本</span><textarea v-model="script" class="console-textarea compose-editor" spellcheck="false" /></label>
      <div class="parameter-grid">
        <label class="console-field">
          <span class="console-label">Params JSON</span>
          <textarea v-model="params" class="console-textarea mini-editor" spellcheck="false" />
        </label>
        <label class="console-field">
          <span class="console-label">Options JSON</span>
          <textarea v-model="options" class="console-textarea mini-editor" spellcheck="false" />
        </label>
      </div>
      <div class="workbench-hint">校验和预览不会自动升级为执行；每次执行都需要显式确认。</div>
    </section>
    <section class="workbench-result">
      <div class="console-panel-head"><span class="console-panel-title">Compose 结果</span><span class="console-panel-kicker">{{ rows.length }} ROWS</span></div>
      <div v-if="warnings.length" class="notice">{{ warnings.join(' · ') }}</div>
      <RuntimeResultTable :rows="rows" :loading="Boolean(busy)" />
      <details class="diagnostics-details" open><summary>SQL 与诊断</summary><pre class="raw-output">{{ output || '运行校验、预览或执行后显示结果。' }}</pre></details>
    </section>
  </div>
</template>

<style scoped>
.compose-editor {
  min-height: 330px !important;
}

.parameter-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 14px;
  border-top: 1px solid var(--console-line);
}

.mini-editor {
  min-height: 130px !important;
}

@media (max-width: 780px) {
  .parameter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
