<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElDrawer } from 'element-plus'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { prettyJson } from '@/utils/json'
import {
  normalizeModelDetail,
  type ModelFieldKind,
  type StructuredModelDetail
} from './modelDetail'
import type { ModelItem } from '@/features/namespace/types'

const props = defineProps<{
  model: ModelItem | null
  namespace: string
}>()
const open = defineModel<boolean>('open', { required: true })
const loading = ref(false)
const errorMessage = ref('')
const detail = ref<StructuredModelDetail | null>(null)
const fieldSearch = ref('')
const fieldKind = ref<'all' | ModelFieldKind>('all')
let returnFocus: HTMLElement | null = null
let requestVersion = 0

const drawerTitle = computed(() =>
  props.model ? `模型详情 · ${props.model.caption || props.model.model}` : '模型详情'
)
const namespaceLabel = computed(() => props.namespace || '空 Namespace')
const modelInfo = computed(() => detail.value?.modelInfo || {})
const scenarios = computed(() =>
  Array.isArray(modelInfo.value.scenarios)
    ? modelInfo.value.scenarios.map(item => String(item))
    : []
)
const physicalTables = computed(() => {
  if (detail.value?.physicalTables.length) return detail.value.physicalTables
  return (props.model?.physicalTables || []).map(table => ({ table, role: '' }))
})
const measureCount = computed(() =>
  detail.value?.fields.filter(field => field.kind === 'measure').length || 0
)
const calculatedCount = computed(() =>
  detail.value?.fields.filter(field => field.kind === 'calculated').length || 0
)
const filteredFields = computed(() => {
  const keyword = fieldSearch.value.trim().toLowerCase()
  return (detail.value?.fields || []).filter(field => {
    const matchesKind = fieldKind.value === 'all' || field.kind === fieldKind.value
    const matchesSearch = !keyword || [
      field.name,
      field.caption,
      field.type,
      field.sourceColumn,
      field.description,
      field.usage
    ].some(value => value.toLowerCase().includes(keyword))
    return matchesKind && matchesSearch
  })
})
const sourceKnown = computed(() =>
  typeof detail.value?.source.known === 'boolean'
    ? detail.value.source.known
    : props.model?.sourceKnown !== false
)
const sourceBundle = computed(() =>
  stringInfo(detail.value?.source.bundleName) || props.model?.bundleName || '来源未知'
)
const sourceNamespace = computed(() =>
  stringInfo(detail.value?.source.namespace)
    || props.model?.sourceNamespace
    || namespaceLabel.value
)
const sourceIdentity = computed(() =>
  stringInfo(detail.value?.source.resourceIdentity)
    || props.model?.resourceIdentity
    || 'Runtime 未返回'
)

function stringInfo(value: unknown, fallback = ''): string {
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return fallback
}

function kindLabel(kind: ModelFieldKind): string {
  if (kind === 'measure') return '度量'
  if (kind === 'calculated') return '计算字段'
  return '维度 / 属性'
}

async function loadDescription(): Promise<void> {
  if (!open.value || !props.model) return
  const version = ++requestVersion
  loading.value = true
  errorMessage.value = ''
  detail.value = null
  try {
    const result = await runtimeApi.post<Record<string, unknown>>(
      `models/${encodeURIComponent(props.model.model)}/describe`,
      { format: 'json', namespace: props.namespace, includeExamples: true }
    )
    if (version !== requestVersion) return
    detail.value = normalizeModelDetail(result, props.model.model)
  } catch (error) {
    if (version !== requestVersion) return
    errorMessage.value = error instanceof RuntimeRequestError ? error.message : '模型详情读取失败。'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function onOpen(): void {
  returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  fieldSearch.value = ''
  fieldKind.value = 'all'
  void loadDescription()
}

function onClosed(): void {
  requestVersion++
  void nextTick(() => returnFocus?.focus())
}

watch(
  () => [props.model?.model, props.namespace] as const,
  () => {
    fieldSearch.value = ''
    fieldKind.value = 'all'
    void loadDescription()
  }
)
</script>

<template>
  <ElDrawer
    v-model="open"
    :title="drawerTitle"
    direction="rtl"
    size="min(760px, 100vw)"
    class="model-detail-drawer"
    @open="onOpen"
    @closed="onClosed"
  >
    <div v-if="model" class="model-detail">
      <section class="detail-manifest">
        <div class="detail-heading">
          <div class="manifest-labels">
            <span class="status-chip" :class="{ warning: !sourceKnown }">
              {{ sourceKnown ? 'SOURCE KNOWN' : 'SOURCE UNKNOWN' }}
            </span>
            <span v-if="detail?.version" class="status-chip">{{ detail.version }}</span>
          </div>
          <span class="console-panel-kicker">SEMANTIC MODEL CONTRACT / {{ namespaceLabel }}</span>
          <strong>{{ model.model }}</strong>
          <p>{{ model.description || stringInfo(modelInfo.purpose, '当前模型未提供目录级语义说明。') }}</p>
        </div>

        <div class="manifest-stats" aria-label="模型详情摘要">
          <div><span>FIELDS</span><strong>{{ detail?.fields.length ?? model.fieldCount ?? '—' }}</strong></div>
          <div><span>MEASURES</span><strong>{{ measureCount }}</strong></div>
          <div><span>CALCULATED</span><strong>{{ calculatedCount }}</strong></div>
          <div><span>PRIMARY TIME</span><strong>{{ model.primaryTimeField || '未设置' }}</strong></div>
        </div>
      </section>

      <div v-if="loading" class="drawer-loading" role="status" aria-live="polite">
        <span /><span /><span />
        <strong>正在读取模型契约…</strong>
      </div>
      <div v-else-if="errorMessage" class="notice error-notice drawer-error" role="alert">
        <span>{{ errorMessage }}</span>
        <button class="console-button compact" type="button" @click="loadDescription">重试</button>
      </div>
      <template v-else-if="detail">
        <div v-if="!detail.hasStructuredContent" class="drawer-empty">
          <span>EMPTY DESCRIPTION</span>
          <strong>Runtime 未返回可结构化的模型详情</strong>
          <p>可展开页面底部的原始响应确认格式，或刷新模型后重试。</p>
        </div>

        <div class="contract-grid">
          <section class="detail-section">
            <div class="section-index">01</div>
            <div>
              <h3>来源与生命周期</h3>
              <dl>
                <div><dt>Bundle 来源</dt><dd>{{ sourceBundle }}</dd></div>
                <div><dt>来源空间</dt><dd>{{ sourceNamespace }}</dd></div>
                <div><dt>资源标识</dt><dd>{{ sourceIdentity }}</dd></div>
                <div><dt>模型类型</dt><dd>{{ stringInfo(modelInfo.type, 'JDBC / DEFAULT') }}</dd></div>
              </dl>
            </div>
          </section>

          <section class="detail-section">
            <div class="section-index">02</div>
            <div>
              <h3>模型用途</h3>
              <dl>
                <div><dt>显示名称</dt><dd>{{ stringInfo(modelInfo.name, model.caption || model.model) }}</dd></div>
                <div><dt>事实表</dt><dd>{{ stringInfo(modelInfo.factTable, '未返回') }}</dd></div>
                <div><dt>用途</dt><dd>{{ stringInfo(modelInfo.purpose, '数据查询和分析') }}</dd></div>
              </dl>
              <div v-if="scenarios.length" class="token-list scenario-list">
                <code v-for="scenario in scenarios" :key="scenario">{{ scenario }}</code>
              </div>
            </div>
          </section>
        </div>

        <section class="detail-section field-directory">
          <div class="section-index">03</div>
          <div class="section-body">
            <div class="section-title-row">
              <div>
                <h3>字段目录</h3>
                <p>{{ detail.fields.length }} 个字段，按 Runtime 显式 metadata 分类。</p>
              </div>
              <span class="status-chip">{{ filteredFields.length }} VISIBLE</span>
            </div>
            <div class="field-toolbar">
              <label class="console-field field-search">
                <span class="console-label">搜索字段</span>
                <input
                  v-model="fieldSearch"
                  class="console-input"
                  type="search"
                  placeholder="名称、类型、来源列、说明"
                  autocomplete="off"
                >
              </label>
              <div class="field-kind-filter" aria-label="字段类型筛选">
                <button
                  v-for="item in [
                    { value: 'all', label: '全部' },
                    { value: 'dimension', label: '维度' },
                    { value: 'measure', label: '度量' },
                    { value: 'calculated', label: '计算' }
                  ]"
                  :key="item.value"
                  type="button"
                  :class="{ active: fieldKind === item.value }"
                  :aria-pressed="fieldKind === item.value"
                  @click="fieldKind = item.value as typeof fieldKind"
                >
                  {{ item.label }}
                </button>
              </div>
            </div>

            <div v-if="filteredFields.length" class="field-list">
              <article v-for="field in filteredFields" :key="field.id" class="field-row">
                <div class="field-kind-mark">{{ field.kind === 'measure' ? 'Σ' : field.kind === 'calculated' ? 'ƒ' : '◇' }}</div>
                <div class="field-copy">
                  <div class="field-name">
                    <strong>{{ field.caption }}</strong>
                    <code>{{ field.name }}</code>
                  </div>
                  <p>{{ field.description || field.usage || 'Runtime 未返回字段说明。' }}</p>
                  <small v-if="field.usage && field.usage !== field.description">{{ field.usage }}</small>
                </div>
                <div class="field-contract">
                  <span>{{ kindLabel(field.kind) }}</span>
                  <strong>{{ field.type }}</strong>
                  <small v-if="field.aggregation">{{ field.aggregation }}</small>
                  <code v-if="field.sourceColumn">{{ field.sourceColumn }}</code>
                </div>
              </article>
            </div>
            <div v-else class="field-empty">
              {{ detail.fields.length ? '没有匹配当前条件的字段。' : 'Runtime 未返回字段 metadata。' }}
            </div>
          </div>
        </section>

        <div class="contract-grid">
          <section class="detail-section">
            <div class="section-index">04</div>
            <div>
              <h3>物理表映射</h3>
              <div v-if="physicalTables.length" class="physical-table-list">
                <div v-for="table in physicalTables" :key="`${table.role}:${table.table}`">
                  <span>{{ table.role || 'PHYSICAL' }}</span>
                  <code>{{ table.table }}</code>
                </div>
              </div>
              <p v-else class="detail-empty">Runtime 未返回物理表信息。</p>
            </div>
          </section>

          <section class="detail-section dependency-note">
            <div class="section-index">05</div>
            <div>
              <h3>QM → TM 依赖</h3>
              <p>当前 Runtime API 未提供 typed 模型依赖；物理表映射不会被推测为 TM 依赖。</p>
            </div>
          </section>
        </div>

        <section v-if="detail.examples.length || detail.modelErrors.length" class="detail-section">
          <div class="section-index">06</div>
          <div>
            <h3>示例与诊断</h3>
            <div v-if="detail.examples.length" class="example-list">
              <pre v-for="(example, index) in detail.examples" :key="index">{{ prettyJson(example) }}</pre>
            </div>
            <div v-if="detail.modelErrors.length" class="notice error-notice">
              {{ prettyJson(detail.modelErrors) }}
            </div>
          </div>
        </section>

        <details class="detail-section raw-detail">
          <summary>
            <span>ADVANCED / RAW</span>
            <strong>Runtime 原始模型 JSON</strong>
          </summary>
          <pre class="raw-output">{{ detail.rawText || 'Runtime 返回了空描述。' }}</pre>
        </details>
      </template>
    </div>
  </ElDrawer>
</template>

<style scoped>
.model-detail {
  display: grid;
  gap: 12px;
  padding-bottom: 28px;
}

.detail-manifest {
  border: 1px solid var(--console-line-strong);
  background:
    linear-gradient(var(--console-grid-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--console-grid-line) 1px, transparent 1px),
    var(--console-panel);
  background-size: 24px 24px;
}

.detail-heading {
  padding: 24px;
  border-bottom: 1px solid var(--console-line-strong);
}

.manifest-labels {
  display: flex;
  gap: 6px;
  margin-bottom: 22px;
}

.detail-heading strong {
  display: block;
  margin: 9px 0;
  overflow-wrap: anywhere;
  font: 700 clamp(20px, 4vw, 30px)/1.1 var(--console-mono);
}

.detail-heading p,
.detail-section p,
.drawer-empty p {
  margin: 0;
  color: var(--console-muted);
  font-size: 13px;
  line-height: 1.65;
}

.manifest-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: var(--console-line-strong);
  gap: 1px;
}

.manifest-stats div {
  min-width: 0;
  padding: 14px;
  background: var(--console-panel);
}

.manifest-stats span,
.manifest-stats strong {
  display: block;
  overflow: hidden;
  font-family: var(--console-mono);
  text-overflow: ellipsis;
}

.manifest-stats span {
  color: var(--console-dim);
  font-size: 9px;
}

.manifest-stats strong {
  margin-top: 6px;
  font-size: 15px;
}

.drawer-loading {
  min-height: 260px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  align-content: center;
  gap: 8px;
  padding: 24px;
  border: 1px solid var(--console-line);
}

.drawer-loading span {
  height: 64px;
  border: 1px solid var(--console-line);
  background: var(--console-panel-2);
  animation: detail-pulse 1.3s steps(2, end) infinite;
}

.drawer-loading strong {
  grid-column: 1 / -1;
  margin-top: 12px;
  color: var(--console-dim);
  font: 11px/1.4 var(--console-mono);
  text-align: center;
}

@keyframes detail-pulse {
  50% { opacity: .45; }
}

.drawer-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 80px;
}

.drawer-empty {
  padding: 22px;
  border: 3px double var(--console-line-strong);
}

.drawer-empty span,
.drawer-empty strong {
  display: block;
}

.drawer-empty span {
  color: var(--console-dim);
  font: 9px/1 var(--console-mono);
  letter-spacing: .12em;
}

.drawer-empty strong {
  margin: 8px 0;
  font-size: 15px;
}

.contract-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.detail-section {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  padding: 18px;
  border: 1px solid var(--console-line);
  background: var(--console-panel-2);
}

.section-index {
  color: var(--console-dim);
  font: 10px/1 var(--console-mono);
}

.detail-section h3 {
  margin: 0 0 14px;
  font-size: 13px;
  letter-spacing: .04em;
}

.detail-section dl {
  display: grid;
  gap: 1px;
  margin: 0;
  background: var(--console-line);
}

.detail-section dl div {
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  gap: 12px;
  padding: 10px 12px;
  background: var(--console-panel);
}

dt {
  color: var(--console-dim);
  font: 10px/1.45 var(--console-mono);
}

dd {
  margin: 0;
  overflow-wrap: anywhere;
  font: 12px/1.45 var(--console-mono);
}

.token-list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.token-list code {
  padding: 7px 9px;
  border: 1px solid var(--console-line);
  background: var(--console-panel);
  font-size: 11px;
}

.scenario-list {
  margin-top: 10px;
}

.field-directory {
  grid-template-columns: 34px minmax(0, 1fr);
}

.section-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.section-title-row h3 {
  margin-bottom: 4px;
}

.field-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto;
  align-items: end;
  gap: 12px;
  margin: 16px 0 10px;
}

.field-kind-filter {
  display: flex;
  border: 1px solid var(--console-line-strong);
}

.field-kind-filter button {
  min-height: 38px;
  padding: 0 11px;
  border: 0;
  border-right: 1px solid var(--console-line);
  border-radius: 0;
  background: var(--console-panel);
  color: var(--console-muted);
  cursor: pointer;
  font: 10px/1 var(--console-mono);
}

.field-kind-filter button:last-child {
  border-right: 0;
}

.field-kind-filter button.active {
  background: var(--console-paper);
  color: var(--console-inverse);
}

.field-list {
  display: grid;
  gap: 1px;
  max-height: 520px;
  overflow: auto;
  border: 1px solid var(--console-line);
  background: var(--console-line);
}

.field-row {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) 118px;
  gap: 12px;
  padding: 13px;
  background: var(--console-panel);
}

.field-kind-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid var(--console-line-strong);
  font: 700 15px/1 var(--console-mono);
}

.field-name {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 7px;
}

.field-name strong {
  font-size: 13px;
}

.field-name code,
.field-contract code {
  color: var(--console-dim);
  font-size: 10px;
}

.field-copy p {
  margin-top: 5px;
  font-size: 12px;
  line-height: 1.5;
}

.field-copy small {
  display: block;
  margin-top: 4px;
  color: var(--console-dim);
  font-size: 10px;
}

.field-contract {
  display: flex;
  align-items: flex-end;
  flex-direction: column;
  gap: 3px;
  text-align: right;
}

.field-contract span,
.field-contract small {
  color: var(--console-dim);
  font: 9px/1.3 var(--console-mono);
}

.field-contract strong {
  font: 700 11px/1.3 var(--console-mono);
}

.field-empty {
  min-height: 110px;
  display: grid;
  place-items: center;
  border: 1px dashed var(--console-line);
  color: var(--console-dim);
  font: 11px/1.5 var(--console-mono);
  text-align: center;
}

.physical-table-list {
  display: grid;
  gap: 6px;
}

.physical-table-list div {
  display: grid;
  grid-template-columns: 74px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  padding: 9px;
  border: 1px solid var(--console-line);
  background: var(--console-panel);
}

.physical-table-list span {
  color: var(--console-dim);
  font: 9px/1 var(--console-mono);
  text-transform: uppercase;
}

.physical-table-list code {
  overflow-wrap: anywhere;
  font-size: 11px;
}

.dependency-note {
  border-style: dashed;
}

.example-list {
  display: grid;
  gap: 8px;
}

.example-list pre {
  margin: 0;
  padding: 12px;
  overflow: auto;
  border: 1px solid var(--console-line);
  background: var(--console-panel);
  font: 10px/1.55 var(--console-mono);
}

.raw-detail {
  display: block;
  padding: 0;
}

.raw-detail summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  cursor: pointer;
}

.raw-detail summary span {
  color: var(--console-dim);
  font: 9px/1 var(--console-mono);
}

.raw-detail summary strong {
  font-size: 12px;
}

.raw-detail .raw-output {
  max-height: none;
  min-height: 180px;
  margin: 0 14px 14px;
}

@media (max-width: 680px) {
  .manifest-stats {
    grid-template-columns: 1fr 1fr;
  }

  .contract-grid {
    grid-template-columns: 1fr;
  }

  .detail-section {
    grid-template-columns: 26px minmax(0, 1fr);
    padding: 14px;
  }

  .field-toolbar {
    grid-template-columns: 1fr;
  }

  .field-kind-filter {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
  }

  .field-kind-filter button {
    padding: 0 6px;
  }

  .field-row {
    grid-template-columns: 34px minmax(0, 1fr);
  }

  .field-contract {
    grid-column: 2;
    align-items: flex-start;
    flex-direction: row;
    flex-wrap: wrap;
    text-align: left;
  }
}

@media (prefers-reduced-motion: reduce) {
  .drawer-loading span {
    animation: none;
  }
}
</style>
