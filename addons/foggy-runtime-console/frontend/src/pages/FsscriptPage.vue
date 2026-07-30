<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import RuntimeResultTable from '@/components/RuntimeResultTable.vue'
import ExecutionToolTabs from '@/components/ExecutionToolTabs.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { useRuntimeSession } from '@/stores/session'
import { normalizeResultRows, parseJsonObject, prettyJson } from '@/utils/json'

interface FsscriptResponse {
  valid?: boolean
  scriptKind?: string
  mode?: string
  value?: unknown
  warnings?: string[]
}

const session = useRuntimeSession()
const acknowledged = ref(false)
const advancedOpen = ref(false)
const script = ref('')
const params = ref('{}')
const options = ref('{}')
const capabilities = ref('{}')
const busy = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const output = ref('')

function errorText(error: unknown): string {
  return error instanceof RuntimeRequestError ? error.message : 'Fsscript 执行失败。'
}

async function execute(): Promise<void> {
  if (!acknowledged.value) {
    ElMessage.warning('请先确认已理解 Fsscript 的高级执行风险。')
    return
  }
  if (!script.value.trim()) {
    ElMessage.warning('请输入 Fsscript。')
    return
  }
  let parsedParams: Record<string, unknown>
  let parsedOptions: Record<string, unknown>
  let parsedCapabilities: Record<string, unknown>
  try {
    parsedParams = parseJsonObject(params.value, 'Params JSON')
    parsedOptions = parseJsonObject(options.value, 'Options JSON')
    parsedCapabilities = parseJsonObject(capabilities.value, 'Capabilities JSON')
  } catch (error) {
    ElMessage.error((error as Error).message)
    return
  }
  try {
    await ElMessageBox.confirm(
      'Fsscript 是高级执行入口，可能触发脚本能力。确认脚本、参数、capabilities 与目标 Runtime 均可信。',
      '最终确认 Fsscript 执行',
      { type: 'warning', confirmButtonText: '确认执行', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  busy.value = true
  rows.value = []
  output.value = ''
  try {
    const result = await runtimeApi.post<FsscriptResponse>('fsscript/execute', {
      script: script.value,
      params: parsedParams,
      options: parsedOptions,
      capabilities: parsedCapabilities,
      namespace: session.namespace.value
    })
    rows.value = normalizeResultRows(result.value)
    output.value = prettyJson({
      valid: result.valid,
      scriptKind: result.scriptKind,
      mode: result.mode,
      warnings: result.warnings
    })
    ElMessage.success('Fsscript 执行完成。')
  } catch (error) {
    ElMessage.error(errorText(error))
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <ExecutionToolTabs />
  <PageHeader
    eyebrow="Advanced runtime"
    title="Fsscript"
    description="高级脚本执行入口默认收起并锁定。仅在理解脚本能力边界、确认输入可信后启用。"
  />

  <section class="risk-gate console-panel">
    <div class="risk-mark">!</div>
    <div>
      <div class="console-panel-kicker">HIGH-RISK OPERATION</div>
      <h2>脚本执行可能超出普通查询的影响范围</h2>
      <p>Console 不会绕过 Runtime 的能力限制，但错误或不可信脚本仍可能消耗资源或产生副作用。请勿粘贴来源不明的脚本、参数或 capabilities。</p>
      <label class="acknowledge">
        <input v-model="acknowledged" type="checkbox">
        我已核对脚本来源、目标 Runtime、namespace 和能力参数
      </label>
      <button class="console-button" type="button" :disabled="!acknowledged" @click="advancedOpen = !advancedOpen">
        {{ advancedOpen ? '收起高级工作台' : '展开高级工作台' }}
      </button>
    </div>
  </section>

  <div v-if="advancedOpen" class="workbench-grid fsscript-workbench">
    <section class="workbench-editor">
      <div class="workbench-toolbar">
        <span class="console-panel-kicker">FSSCRIPT</span>
        <button class="console-button compact danger" type="button" :disabled="busy || !acknowledged" @click="execute">
          {{ busy ? '执行中…' : '确认并执行' }}
        </button>
      </div>
      <label><span class="visually-hidden">Fsscript</span><textarea v-model="script" class="console-textarea" placeholder="// 在此输入可信 Fsscript" spellcheck="false" /></label>
      <div class="advanced-json-grid">
        <label class="console-field"><span class="console-label">Params JSON</span><textarea v-model="params" class="console-textarea mini-editor" spellcheck="false" /></label>
        <label class="console-field"><span class="console-label">Options JSON</span><textarea v-model="options" class="console-textarea mini-editor" spellcheck="false" /></label>
        <label class="console-field"><span class="console-label">Capabilities JSON</span><textarea v-model="capabilities" class="console-textarea mini-editor" spellcheck="false" /></label>
      </div>
    </section>
    <section class="workbench-result">
      <div class="console-panel-head"><span class="console-panel-title">执行结果</span><span class="console-panel-kicker">{{ rows.length }} ROWS</span></div>
      <RuntimeResultTable :rows="rows" :loading="busy" />
      <details class="diagnostics-details" open><summary>执行元数据</summary><pre class="raw-output">{{ output || '暂无执行结果。' }}</pre></details>
    </section>
  </div>
</template>

<style scoped>
.risk-gate {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 22px;
  padding: 24px;
  border: 3px double var(--console-line-strong);
  background:
    repeating-linear-gradient(
      135deg,
      transparent 0 9px,
      var(--console-hatch-line) 9px 10px
    ),
    var(--console-panel);
}

.risk-mark {
  width: 64px;
  height: 64px;
  display: grid;
  place-items: center;
  border: 3px double var(--console-paper);
  border-radius: 0;
  color: var(--console-paper);
  font: 700 30px/1 var(--console-mono);
}

.risk-gate h2 {
  margin: 8px 0;
  font-size: 22px;
}

.risk-gate p {
  max-width: 800px;
  color: var(--console-muted);
  font-size: 14px;
  line-height: 1.65;
}

.acknowledge {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px 0;
  color: var(--console-text);
  font-size: 14px;
}

.fsscript-workbench {
  margin-top: 14px;
}

.advanced-json-grid {
  display: grid;
  gap: 12px;
  padding: 14px;
  border-top: 1px solid var(--console-line);
}

.mini-editor {
  min-height: 120px !important;
}

@media (max-width: 620px) {
  .risk-gate {
    grid-template-columns: 1fr;
  }
}
</style>
