<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import RuntimeResultTable from '@/components/RuntimeResultTable.vue'
import { queryRowsToCsv } from '@/features/query/queryWorkbench'
import {
  candidateExecutionFacts,
  candidateQueryCsvFilename,
  shortRevision,
  type WorkspaceActions
} from './authoringWorkspace'
import type {
  AuthoringDiffResponse,
  AuthoringQueryResponse,
  ValidationEvidence
} from './types'

const props = defineProps<{
  inspector: 'diff' | 'validate' | 'query'
  actions: WorkspaceActions
  busy: string
  dirty: boolean
  diffResult: AuthoringDiffResponse | null
  validation: ValidationEvidence | null
  validationRows: Record<string, unknown>[]
  currentValidation: boolean
  queryModel: string
  queryPayload: string
  selectedQmSuggestion: string
  queryResult: AuthoringQueryResponse | null
  queryRows: Record<string, unknown>[]
}>()

const emit = defineEmits<{
  'update:inspector': [value: 'diff' | 'validate' | 'query']
  'update:queryModel': [value: string]
  'update:queryPayload': [value: string]
  diff: []
  validate: []
  query: [mode: 'validate' | 'execute']
  useSuggestion: []
}>()

const executionFacts = computed(() => candidateExecutionFacts(props.queryResult?.response?.execution))

function exportCandidateCsv(): void {
  const csv = queryRowsToCsv(props.queryRows)
  if (!csv) {
    ElMessage.warning('当前 candidate query 没有可导出的结果。')
    return
  }
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = candidateQueryCsvFilename(props.queryModel, props.queryResult?.workspaceId || '')
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <section class="candidate-inspector">
    <nav aria-label="Candidate 检查工具">
      <button type="button" :class="{ active: inspector === 'diff' }" @click="emit('update:inspector', 'diff')">DIFF</button>
      <button type="button" :class="{ active: inspector === 'validate' }" @click="emit('update:inspector', 'validate')">VALIDATE</button>
      <button type="button" :class="{ active: inspector === 'query' }" @click="emit('update:inspector', 'query')">CANDIDATE QUERY</button>
    </nav>

    <div v-if="inspector === 'diff'" class="inspector-body">
      <div class="inspector-command">
        <div><span>IMMUTABLE BASE → PINNED CANDIDATE</span><p>按资源查看新增、修改与删除；这里不提供 merge/rebase。</p></div>
        <button class="console-button" type="button" :disabled="!actions.diff || Boolean(busy)" @click="emit('diff')">{{ busy === 'diff' ? '读取中…' : '读取 exact diff' }}</button>
      </div>
      <div v-if="diffResult" class="diff-list">
        <article v-for="change in diffResult.changes" :key="change.path">
          <header><span>{{ change.changeType }}</span><strong>{{ change.path }}</strong><code>{{ change.type }}</code></header>
          <div class="diff-content">
            <div><span>BASE · {{ shortRevision(change.baseSha256 || '') }}</span><pre>{{ change.baseContent ?? '∅' }}</pre></div>
            <div><span>CANDIDATE · {{ shortRevision(change.candidateSha256 || '') }}</span><pre>{{ change.candidateContent ?? '∅' }}</pre></div>
          </div>
        </article>
        <div v-if="!diffResult.changes.length" class="studio-empty">当前 candidate 与 immutable base 一致。</div>
      </div>
    </div>

    <div v-else-if="inspector === 'validate'" class="inspector-body">
      <div class="inspector-command">
        <div><span>FULL DETACHED VALIDATION</span><p>校验当前 exact candidate revision；失败 evidence 会在 workspace metadata 中保留。</p></div>
        <button class="console-button primary" type="button" :disabled="!actions.validate || dirty || Boolean(busy)" @click="emit('validate')">{{ busy === 'validate' ? '校验中…' : '校验当前 revision' }}</button>
      </div>
      <div v-if="dirty" class="notice">请先显式保存当前资源，再校验 server-owned candidate revision。</div>
      <div v-if="validation" class="validation-evidence" :class="{ stale: !currentValidation }">
        <div><span>RESULT</span><strong>{{ validation.valid ? 'VALID' : 'INVALID' }}</strong></div>
        <div><span>TOTAL</span><strong>{{ validation.totalFiles }}</strong></div>
        <div><span>VALID</span><strong>{{ validation.validFiles }}</strong></div>
        <div><span>INVALID</span><strong>{{ validation.invalidFiles }}</strong></div>
        <div><span>CASCADE</span><strong>{{ validation.cascadingErrors }}</strong></div>
        <div><span>EVIDENCE</span><strong>{{ currentValidation ? 'CURRENT' : 'HISTORICAL' }}</strong></div>
      </div>
      <RuntimeResultTable v-if="validationRows.length" :rows="validationRows" />
      <div v-if="validation && !validationRows.length" class="studio-empty">Runtime 未返回 validation issue。</div>
    </div>

    <div v-else class="inspector-body query-inspector">
      <div class="inspector-command">
        <div><span>GOVERNED CANDIDATE QUERY</span><p>只查询当前已完整验证的 workspace revision；使用当前 Runtime 依赖、数据源与业务 Authorization。</p></div>
        <span :class="['status-chip', actions.query ? '' : 'warning']">{{ actions.query ? 'REVISION VALIDATED' : 'VALIDATE REQUIRED' }}</span>
      </div>
      <div class="query-form-grid">
        <label class="console-field">
          <span class="console-label">QM canonical model name</span>
          <input
            :value="queryModel"
            class="console-input"
            aria-label="Candidate QM 模型"
            placeholder="OrderQueryModel"
            autocomplete="off"
            @input="emit('update:queryModel', ($event.target as HTMLInputElement).value)"
          >
          <button v-if="selectedQmSuggestion" class="model-suggestion" type="button" @click="emit('useSuggestion')">使用文件名建议：{{ selectedQmSuggestion }}</button>
        </label>
        <label class="console-field query-payload-field">
          <span class="console-label">Candidate Query DSL JSON</span>
          <textarea
            :value="queryPayload"
            class="console-textarea"
            aria-label="Candidate Query DSL JSON"
            spellcheck="false"
            @input="emit('update:queryPayload', ($event.target as HTMLTextAreaElement).value)"
          />
        </label>
      </div>
      <div class="editor-actions">
        <button class="console-button" type="button" :disabled="!actions.query || dirty || Boolean(busy)" @click="emit('query', 'validate')">Validate query</button>
        <button class="console-button primary" type="button" :disabled="!actions.query || dirty || Boolean(busy)" @click="emit('query', 'execute')">Execute candidate</button>
        <button class="console-button" type="button" :disabled="!queryRows.length || Boolean(busy)" title="仅导出当前已展示 candidate rows" @click="exportCandidateCsv">导出 candidate CSV</button>
      </div>
      <div v-if="queryResult" class="query-identity" aria-label="Candidate query 执行事实">
        <span>WORKSPACE <strong>{{ queryResult.workspaceId }}</strong></span>
        <span>REVISION <strong>{{ shortRevision(queryResult.candidateRevision) }}</strong></span>
        <span>PHASE <strong>{{ queryResult.phase }}</strong></span>
        <span>CATALOG <strong>{{ JSON.stringify(queryResult.catalogIdentity || {}) }}</strong></span>
        <span>PROVIDER <strong>{{ executionFacts.provider }}</strong></span>
        <span>STATUS <strong>{{ executionFacts.status }}</strong></span>
        <span>DURATION <strong>{{ executionFacts.duration }}</strong></span>
      </div>
      <RuntimeResultTable v-if="queryRows.length" :rows="queryRows" />
      <div v-else-if="queryResult" class="studio-empty">当前 candidate query 没有结果行；CSV 导出不可用。</div>
      <div v-for="warning in queryResult?.response?.warnings || []" :key="warning" class="notice">{{ warning }}</div>
    </div>
  </section>
</template>

<style scoped>
.candidate-inspector > nav { display: grid; grid-template-columns: repeat(3, 1fr); border-bottom: 1px solid var(--console-line-strong); }
.candidate-inspector > nav button { min-height: 45px; border: 0; border-right: 1px solid var(--console-line); background: var(--console-panel-2); color: var(--console-muted); font: 700 10px/1 var(--console-mono); cursor: pointer; }
.candidate-inspector > nav button:last-child { border-right: 0; }
.candidate-inspector > nav button.active { background: var(--console-paper); color: var(--console-inverse); }
.inspector-body { padding: 16px; border-bottom: 1px solid var(--console-line-strong); }
.inspector-command { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 14px; }
.inspector-command span { color: var(--console-dim); font: 700 9px/1 var(--console-mono); }
.inspector-command p { margin: 0; color: var(--console-muted); font-size: 12px; line-height: 1.65; }
.diff-list { display: grid; gap: 10px; }
.diff-list article { border: 1px solid var(--console-line-strong); }
.diff-list header { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 12px; padding: 9px; border-bottom: 1px solid var(--console-line); background: var(--console-panel-2); }
.diff-list header span, .diff-list header code { font: 9px/1 var(--console-mono); }
.diff-list header strong { overflow: hidden; font: 11px/1 var(--console-mono); text-overflow: ellipsis; }
.diff-content { display: grid; grid-template-columns: 1fr 1fr; gap: 1px; background: var(--console-line); }
.diff-content > div { min-width: 0; padding: 10px; background: var(--console-panel); }
.diff-content span { color: var(--console-dim); font: 8px/1 var(--console-mono); }
.diff-content pre { min-height: 90px; max-height: 250px; margin: 8px 0 0; overflow: auto; white-space: pre-wrap; word-break: break-word; font: 10px/1.55 var(--console-mono); }
.validation-evidence { display: grid; grid-template-columns: repeat(6, 1fr); gap: 1px; margin-bottom: 12px; background: var(--console-line); }
.validation-evidence div { padding: 12px; background: var(--console-panel-2); }
.validation-evidence span, .validation-evidence strong { display: block; font-family: var(--console-mono); }
.validation-evidence span { color: var(--console-dim); font-size: 8px; }
.validation-evidence strong { margin-top: 8px; font-size: 12px; }
.validation-evidence.stale { opacity: .58; }
.query-form-grid { display: grid; grid-template-columns: minmax(180px, .4fr) minmax(0, 1fr); gap: 12px; }
.query-payload-field textarea { min-height: 180px; }
.model-suggestion { margin-top: 6px; border: 0; background: transparent; color: var(--console-muted); font: 9px/1.4 var(--console-mono); text-decoration: underline; cursor: pointer; text-align: left; }
.editor-actions { display: flex; flex-wrap: wrap; gap: 9px; margin-top: 12px; }
.query-identity { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1px; margin: 14px 0; background: var(--console-line); }
.query-identity span { min-width: 0; padding: 10px; background: var(--console-panel-2); color: var(--console-dim); font: 8px/1.5 var(--console-mono); }
.query-identity strong { display: block; overflow: hidden; margin-top: 5px; color: var(--console-text); text-overflow: ellipsis; }
.studio-empty { padding: 18px; color: var(--console-dim); font: 11px/1.6 var(--console-mono); }

@media (max-width: 1080px) {
  .validation-evidence { grid-template-columns: repeat(3, 1fr); }
  .query-identity { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 760px) {
  .query-form-grid { grid-template-columns: 1fr; }
  .inspector-command { align-items: stretch; flex-direction: column; }
  .validation-evidence, .query-identity { grid-template-columns: 1fr 1fr; }
  .diff-content { grid-template-columns: 1fr; }
  .candidate-inspector > nav { overflow-x: auto; }
  .candidate-inspector > nav button { min-width: 130px; }
}
</style>
