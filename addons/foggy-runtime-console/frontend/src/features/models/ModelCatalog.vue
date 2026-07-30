<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ResourceCard from '@/components/resources/ResourceCard.vue'
import RuntimeResultTable from '@/components/RuntimeResultTable.vue'
import ModelDetailDrawer from './ModelDetailDrawer.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { normalizeResultRows } from '@/utils/json'
import type { LifecycleResult, ModelItem } from '@/features/namespace/types'

const props = defineProps<{
  namespace: string
  models: ModelItem[]
  loading: boolean
}>()
const emit = defineEmits<{ reload: [] }>()
const search = ref('')
const bundleFilter = ref('')
const selected = ref<string[]>([])
const detailModel = ref<ModelItem | null>(null)
const detailOpen = ref(false)
const busy = ref('')
const validatePath = ref('')
const lifecycle = ref<LifecycleResult | null>(null)
const lifecycleRows = ref<Record<string, unknown>[]>([])

const bundleOptions = computed(() =>
  [...new Set(props.models.map(item => item.bundleName || '').filter(Boolean))].sort()
)

const filteredModels = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return props.models.filter(item => {
    const matchesBundle = !bundleFilter.value || item.bundleName === bundleFilter.value
    const matchesSearch = !keyword || [
      item.model,
      item.caption,
      item.description,
      item.bundleName,
      item.primaryTimeField
    ].some(value => value?.toLowerCase().includes(keyword))
    return matchesBundle && matchesSearch
  })
})

function errorText(error: unknown): string {
  return error instanceof RuntimeRequestError ? error.message : '模型操作失败。'
}

function toggleModel(name: string): void {
  selected.value = selected.value.includes(name)
    ? selected.value.filter(item => item !== name)
    : [...selected.value, name]
}

function openDetail(item: ModelItem): void {
  detailModel.value = item
  detailOpen.value = true
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

async function refreshModels(scope: 'selected' | 'all'): Promise<void> {
  if (scope === 'selected' && !selected.value.length) {
    ElMessage.warning('请先选择至少一个模型。')
    return
  }
  try {
    await ElMessageBox.confirm(
      scope === 'all'
        ? `刷新数据与模型空间 ${props.namespace || '空 Namespace'} 的全部模型？`
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
      namespace: props.namespace,
      models: scope === 'selected' ? selected.value : []
    })
    setLifecycle(result)
    ElMessage.success(`模型刷新完成：${result.refreshedCount ?? result.loadedCount ?? 0} 成功。`)
    emit('reload')
  } catch (error) {
    ElMessage.error(errorText(error))
  } finally {
    busy.value = ''
  }
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
      namespace: props.namespace,
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

watch(() => props.models, models => {
  selected.value = selected.value.filter(name => models.some(item => item.model === name))
})
</script>

<template>
  <section aria-labelledby="model-catalog-title">
    <div class="catalog-intro">
      <div>
        <span class="console-panel-kicker">QM CATALOG / NAMESPACE SCOPED</span>
        <h2 id="model-catalog-title">分析模型（QM）</h2>
        <p>Bundle 表示模型资源来源；QM 与 TM 是可复用的依赖关系，不是级联所有权。</p>
      </div>
      <div class="catalog-actions">
        <button class="console-button" type="button" :disabled="Boolean(busy)" @click="refreshModels('selected')">刷新已选</button>
        <button class="console-button primary" type="button" :disabled="Boolean(busy)" @click="refreshModels('all')">刷新全部</button>
      </div>
    </div>

    <div class="toolbar">
      <label class="console-field">
        <span class="console-label">搜索模型</span>
        <input v-model="search" class="console-input" type="search" placeholder="名称、说明、Bundle">
      </label>
      <label class="console-field">
        <span class="console-label">Bundle 来源</span>
        <select v-model="bundleFilter" class="console-select">
          <option value="">全部来源</option>
          <option v-for="bundle in bundleOptions" :key="bundle" :value="bundle">{{ bundle }}</option>
        </select>
      </label>
      <div class="toolbar-spacer" />
      <span class="status-chip">{{ filteredModels.length }} QM</span>
      <span class="status-chip">{{ selected.length }} SELECTED</span>
    </div>

    <div class="resource-grid" aria-live="polite">
      <ResourceCard
        v-for="item in filteredModels"
        :key="item.model"
        code="QM"
        :title="item.caption || item.model"
        :caption="item.model"
        :description="item.description || '此模型暂未提供语义描述。'"
        :selected="detailOpen && detailModel?.model === item.model"
      >
        <template #status>
          <span class="status-chip" :class="{ warning: !item.sourceKnown }">
            {{ item.sourceKnown ? 'SOURCE KNOWN' : 'SOURCE UNKNOWN' }}
          </span>
        </template>
        <template #meta>
          <span>{{ item.fieldCount ?? '—' }} fields</span>
          <span>{{ item.bundleName || '来源未知' }}</span>
          <span>{{ item.primaryTimeField || 'no primary time' }}</span>
        </template>
        <template #actions>
          <label class="card-check">
            <input
              type="checkbox"
              :checked="selected.includes(item.model)"
              :aria-label="`选择 ${item.model}`"
              @change="toggleModel(item.model)"
            >
            <span>选择</span>
          </label>
          <button class="console-button compact primary" type="button" @click="openDetail(item)">查看详情</button>
        </template>
      </ResourceCard>
    </div>

    <div v-if="!loading && !filteredModels.length" class="empty-state console-panel">
      <div><strong>没有可见 QM</strong>检查当前空间、Bundle 或模型权限后重新读取。</div>
    </div>

    <details class="console-panel lifecycle-tools">
      <summary>模型维护工具 · 路径校验与生命周期诊断</summary>
      <div class="maintenance-grid">
        <div class="dialog-form">
          <label class="console-field">
            <span class="console-label">模型路径</span>
            <input v-model="validatePath" class="console-input" placeholder="/opt/foggy/models" autocomplete="off">
          </label>
          <button class="console-button primary" type="button" :disabled="Boolean(busy)" @click="validateModels">
            {{ busy === 'validate' ? '校验中…' : '校验路径' }}
          </button>
          <div class="notice">校验在隔离候选中进行，不会因失败覆盖当前 catalog。</div>
        </div>
        <div>
          <div v-if="lifecycle" class="lifecycle-strip">
            <div><span>BEFORE</span><strong>{{ lifecycle.beforeCatalogGeneration || '—' }}</strong></div>
            <div><span>AFTER</span><strong>{{ lifecycle.afterCatalogGeneration || '—' }}</strong></div>
            <div><span>REFRESHED</span><strong>{{ lifecycle.refreshedCount ?? lifecycle.loadedCount ?? lifecycle.validFiles ?? 0 }}</strong></div>
            <div><span>FAILED</span><strong>{{ lifecycle.failedCount ?? lifecycle.invalidFiles ?? 0 }}</strong></div>
          </div>
          <RuntimeResultTable :rows="lifecycleRows" :loading="busy.startsWith('refresh') || busy === 'validate'" />
        </div>
      </div>
    </details>

    <ModelDetailDrawer
      v-model:open="detailOpen"
      :model="detailModel"
      :namespace="namespace"
    />
  </section>
</template>

<style scoped>
.catalog-intro {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.catalog-intro h2 {
  margin: 7px 0 6px;
  font-size: 24px;
}

.catalog-intro p {
  max-width: 720px;
  margin: 0;
  color: var(--console-muted);
  font-size: 13px;
}

.catalog-actions {
  display: flex;
  gap: 8px;
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.card-check {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--console-muted);
  font: 11px/1 var(--console-mono);
}

.lifecycle-tools {
  margin-top: 14px;
}

.lifecycle-tools summary {
  padding: 16px 18px;
  cursor: pointer;
  font: 650 12px/1.4 var(--console-mono);
}

.maintenance-grid {
  display: grid;
  grid-template-columns: minmax(270px, .7fr) minmax(0, 1.3fr);
  gap: 1px;
  border-top: 1px solid var(--console-line);
  background: var(--console-line);
}

.maintenance-grid > div {
  padding: 18px;
  background: var(--console-panel);
}

.lifecycle-strip {
  display: grid;
  grid-template-columns: 2fr 2fr 1fr 1fr;
  gap: 1px;
  margin-bottom: 1px;
  background: var(--console-line);
}

.lifecycle-strip > div {
  min-width: 0;
  padding: 12px;
  background: var(--console-panel-2);
}

.lifecycle-strip span,
.lifecycle-strip strong {
  display: block;
  overflow: hidden;
  font: 10px/1.4 var(--console-mono);
  text-overflow: ellipsis;
}

.lifecycle-strip span {
  color: var(--console-dim);
}

.lifecycle-strip strong {
  margin-top: 5px;
}

@media (max-width: 760px) {
  .catalog-intro {
    align-items: stretch;
    flex-direction: column;
  }

  .catalog-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .maintenance-grid {
    grid-template-columns: 1fr;
  }

  .lifecycle-strip {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
