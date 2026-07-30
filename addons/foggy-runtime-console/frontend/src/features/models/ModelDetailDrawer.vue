<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElDrawer } from 'element-plus'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { prettyJson } from '@/utils/json'
import type { ModelItem } from '@/features/namespace/types'

const props = defineProps<{
  model: ModelItem | null
  namespace: string
}>()
const open = defineModel<boolean>('open', { required: true })
const loading = ref(false)
const errorMessage = ref('')
const content = ref('')
let returnFocus: HTMLElement | null = null
let requestVersion = 0

const drawerTitle = computed(() =>
  props.model ? `模型详情 · ${props.model.caption || props.model.model}` : '模型详情'
)

async function loadDescription(): Promise<void> {
  if (!open.value || !props.model) return
  const version = ++requestVersion
  loading.value = true
  errorMessage.value = ''
  content.value = ''
  try {
    const result = await runtimeApi.post<Record<string, unknown>>(
      `models/${encodeURIComponent(props.model.model)}/describe`,
      { format: 'json', namespace: props.namespace, includeExamples: true }
    )
    if (version !== requestVersion) return
    content.value = typeof result.content === 'string'
      ? result.content
      : prettyJson(result.data || result)
  } catch (error) {
    if (version !== requestVersion) return
    errorMessage.value = error instanceof RuntimeRequestError ? error.message : '模型详情读取失败。'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function onOpen(): void {
  returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  void loadDescription()
}

function onClosed(): void {
  requestVersion++
  void nextTick(() => returnFocus?.focus())
}

watch(() => props.model?.model, () => void loadDescription())
</script>

<template>
  <ElDrawer
    v-model="open"
    :title="drawerTitle"
    direction="rtl"
    size="min(580px, 100vw)"
    class="model-detail-drawer"
    @open="onOpen"
    @closed="onClosed"
  >
    <div v-if="model" class="model-detail">
      <div class="detail-heading">
        <span class="status-chip" :class="{ warning: !model.sourceKnown }">
          {{ model.sourceKnown ? 'SOURCE KNOWN' : 'SOURCE UNKNOWN' }}
        </span>
        <strong>{{ model.model }}</strong>
        <p>{{ model.description || '当前模型未提供目录级语义说明。' }}</p>
      </div>

      <section class="detail-section">
        <h3>来源与生命周期</h3>
        <dl>
          <div><dt>Bundle 来源</dt><dd>{{ model.bundleName || '来源未知' }}</dd></div>
          <div><dt>来源空间</dt><dd>{{ model.sourceNamespace || namespace || '空 Namespace' }}</dd></div>
          <div><dt>资源标识</dt><dd>{{ model.resourceIdentity || 'Runtime 未返回' }}</dd></div>
          <div><dt>字段数量</dt><dd>{{ model.fieldCount ?? '未返回' }}</dd></div>
          <div><dt>主时间字段</dt><dd>{{ model.primaryTimeField || '未设置' }}</dd></div>
        </dl>
      </section>

      <section class="detail-section">
        <h3>物理表映射</h3>
        <div v-if="model.physicalTables?.length" class="token-list">
          <code v-for="table in model.physicalTables" :key="table">{{ table }}</code>
        </div>
        <p v-else class="detail-empty">Runtime 未返回物理表信息。</p>
      </section>

      <section class="detail-section dependency-note">
        <h3>QM → TM 依赖</h3>
        <p>当前 Runtime API 未提供 typed 模型依赖；物理表映射不会被推测为 TM 依赖。</p>
      </section>

      <section class="detail-section">
        <h3>语义描述与示例</h3>
        <div v-if="loading" class="drawer-state" role="status">正在读取模型描述…</div>
        <div v-else-if="errorMessage" class="notice error-notice" role="alert">
          {{ errorMessage }}
          <button class="console-button compact" type="button" @click="loadDescription">重试</button>
        </div>
        <pre v-else-if="content" class="raw-output">{{ content }}</pre>
        <div v-else class="drawer-state">Runtime 返回了空描述。</div>
      </section>
    </div>
  </ElDrawer>
</template>

<style scoped>
.model-detail {
  display: grid;
  gap: 12px;
  padding-bottom: 24px;
}

.detail-heading {
  padding: 4px 0 18px;
  border-bottom: 1px solid var(--console-line-strong);
}

.detail-heading strong {
  display: block;
  margin: 15px 0 8px;
  font: 700 18px/1.3 var(--console-mono);
}

.detail-heading p,
.detail-section p {
  margin: 0;
  color: var(--console-muted);
  font-size: 13px;
  line-height: 1.65;
}

.detail-section {
  padding: 18px;
  border: 1px solid var(--console-line);
  background: var(--console-panel-2);
}

.detail-section h3 {
  margin: 0 0 14px;
  font-size: 13px;
  letter-spacing: 0.04em;
}

.detail-section dl {
  display: grid;
  gap: 1px;
  margin: 0;
  background: var(--console-line);
}

.detail-section dl div {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
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

.dependency-note {
  border-style: dashed;
}

.drawer-state {
  min-height: 150px;
  display: grid;
  place-items: center;
  color: var(--console-dim);
  font: 12px/1.5 var(--console-mono);
}

.raw-output {
  max-height: none;
  min-height: 180px;
}

@media (max-width: 560px) {
  .detail-section {
    padding: 14px;
  }

  .detail-section dl div {
    grid-template-columns: 1fr;
    gap: 4px;
  }
}
</style>
