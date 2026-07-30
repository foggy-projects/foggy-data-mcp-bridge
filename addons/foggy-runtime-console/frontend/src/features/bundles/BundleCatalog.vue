<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElDialog, ElDrawer, ElMessage, ElMessageBox } from 'element-plus'
import ResourceCard from '@/components/resources/ResourceCard.vue'
import RuntimeResultTable from '@/components/RuntimeResultTable.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import type { BundleItem, ModelItem } from '@/features/namespace/types'

const props = defineProps<{
  namespace: string
  bundles: BundleItem[]
  models: ModelItem[]
  loading: boolean
}>()
const emit = defineEmits<{ reload: [] }>()
const search = ref('')
const dialogOpen = ref(false)
const editingName = ref('')
const advancedOpen = ref(false)
const advancedBundle = ref<BundleItem | null>(null)
const resourceBusy = ref(false)
const resourceMode = ref<'export' | 'save'>('export')
const resourcePayload = ref('{}')
const resourceResult = ref<Record<string, unknown>[]>([])
const form = reactive({
  name: '',
  namespace: props.namespace,
  path: '',
  watch: false,
  enabled: true,
  validate: true,
  refresh: true
})

const filteredBundles = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return props.bundles
  return props.bundles.filter(item =>
    [item.name, item.path, item.status, item.source]
      .some(value => value?.toLowerCase().includes(keyword))
  )
})

function modelCount(bundle: BundleItem): number {
  return props.models.filter(model => model.bundleName === bundle.name).length
}

function errorText(error: unknown): string {
  return error instanceof RuntimeRequestError ? error.message : 'Bundle 操作失败。'
}

function resetForm(): void {
  editingName.value = ''
  Object.assign(form, {
    name: '',
    namespace: props.namespace,
    path: '',
    watch: false,
    enabled: true,
    validate: true,
    refresh: true
  })
}

function openCreate(): void {
  resetForm()
  dialogOpen.value = true
}

function openEdit(item: BundleItem): void {
  editingName.value = item.name
  Object.assign(form, {
    name: item.name,
    namespace: props.namespace,
    path: item.path,
    watch: Boolean(item.watch),
    enabled: item.enabled !== false,
    validate: true,
    refresh: true
  })
  dialogOpen.value = true
}

async function saveBundle(): Promise<void> {
  if (!form.name.trim() || !form.path.trim()) {
    ElMessage.warning('请填写 Bundle 名称和路径。')
    return
  }
  try {
    if (editingName.value) {
      await runtimeApi.put(`bundles/${encodeURIComponent(editingName.value)}`, {
        ...form,
        replace: true
      })
    } else {
      await runtimeApi.post('bundles', form)
    }
    dialogOpen.value = false
    ElMessage.success(editingName.value ? 'Bundle 已更新。' : 'Bundle 已注册。')
    emit('reload')
  } catch (error) {
    ElMessage.error(errorText(error))
  }
}

async function removeBundle(item: BundleItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `从空间 ${props.namespace || '空 Namespace'} 移除 Bundle ${item.name}？模型文件不会被删除。`,
      '确认移除 Bundle',
      { type: 'warning', confirmButtonText: '确认移除', cancelButtonText: '取消' }
    )
    await runtimeApi.delete(`bundles/${encodeURIComponent(item.name)}`)
    ElMessage.success('Bundle 已移除，磁盘目录未删除。')
    emit('reload')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorText(error))
  }
}

function syncResourcePayload(mode: 'export' | 'save'): void {
  resourceMode.value = mode
  resourceResult.value = []
  resourcePayload.value = mode === 'export'
    ? JSON.stringify({
      namespace: props.namespace,
      bundle: advancedBundle.value?.name || '',
      paths: [],
      includeContent: false
    }, null, 2)
    : JSON.stringify({
      namespace: props.namespace,
      bundle: advancedBundle.value?.name || '',
      files: [{ path: 'models/example.qm', content: '// QM content', baseSha256: null }],
      validate: true,
      refresh: false
    }, null, 2)
}

function openAdvanced(item: BundleItem): void {
  advancedBundle.value = item
  syncResourcePayload('export')
  advancedOpen.value = true
}

function resultRows(value: unknown): Record<string, unknown>[] {
  if (!value || typeof value !== 'object') return []
  const record = value as Record<string, unknown>
  for (const key of ['resources', 'savedResources']) {
    if (Array.isArray(record[key])) return record[key] as Record<string, unknown>[]
  }
  return [record]
}

async function executeResourceOperation(): Promise<void> {
  let payload: unknown
  try {
    payload = JSON.parse(resourcePayload.value)
  } catch {
    ElMessage.error('资源请求 JSON 格式无效，尚未发送。')
    return
  }
  if (resourceMode.value === 'save') {
    try {
      await ElMessageBox.confirm(
        `保存资源会写入 ${advancedBundle.value?.name || 'Bundle'} 的文件。请核对目标与文件数量。`,
        '确认保存 Bundle 资源',
        { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  resourceBusy.value = true
  try {
    const result = await runtimeApi.post<Record<string, unknown>>(`resources/${resourceMode.value}`, payload)
    resourceResult.value = resultRows(result)
    ElMessage.success(resourceMode.value === 'export' ? '资源已导出。' : '资源已保存。')
  } catch (error) {
    ElMessage.error(errorText(error))
  } finally {
    resourceBusy.value = false
  }
}

watch(() => props.namespace, () => {
  advancedOpen.value = false
  dialogOpen.value = false
  search.value = ''
})
</script>

<template>
  <section aria-labelledby="bundle-catalog-title">
    <div class="catalog-intro">
      <div>
        <span class="console-panel-kicker">MODEL SOURCES / NAMESPACE SCOPED</span>
        <h2 id="bundle-catalog-title">Bundle 来源</h2>
        <p>Bundle 是 TM/QM 的资源来源；编辑文件与原始 JSON 操作被收纳在每张卡片的高级操作中。</p>
      </div>
      <button class="console-button primary" type="button" @click="openCreate">注册 Bundle</button>
    </div>

    <div class="toolbar">
      <label class="console-field">
        <span class="console-label">搜索 Bundle</span>
        <input v-model="search" class="console-input" type="search" placeholder="名称、路径、状态或来源">
      </label>
      <div class="toolbar-spacer" />
      <span class="status-chip">{{ filteredBundles.length }} BUNDLES</span>
    </div>

    <div class="resource-grid">
      <ResourceCard
        v-for="item in filteredBundles"
        :key="item.name"
        code="BUNDLE"
        :title="item.name"
        :caption="item.source || 'runtime registry'"
        :description="item.path"
      >
        <template #status>
          <span
            class="status-chip"
            :class="{ warning: item.status && !['active', 'ready', 'READY'].includes(item.status) }"
          >
            {{ item.status || (item.enabled === false ? 'DISABLED' : 'REGISTERED') }}
          </span>
        </template>
        <template #meta>
          <span>{{ item.watch ? 'watch enabled' : 'manual refresh' }}</span>
          <span>{{ modelCount(item) }} visible QM</span>
          <span>TM count not provided</span>
        </template>
        <template #actions>
          <button class="console-button compact" type="button" :disabled="item.canUpdate === false" @click="openEdit(item)">编辑</button>
          <button class="console-button compact primary" type="button" @click="openAdvanced(item)">高级操作</button>
          <button class="console-button compact danger" type="button" :disabled="item.canRemove === false" @click="removeBundle(item)">移除</button>
        </template>
      </ResourceCard>
    </div>

    <div v-if="!loading && !filteredBundles.length" class="empty-state console-panel">
      <div>
        <strong>此空间尚未注册 Bundle</strong>
        注册模型目录后，TM/QM 将在当前 Namespace 下隔离加载。
        <button class="console-button compact" type="button" @click="openCreate">注册第一个 Bundle</button>
      </div>
    </div>
  </section>

  <ElDialog v-model="dialogOpen" :title="editingName ? '编辑 Bundle' : '注册 Bundle'" width="min(680px, 94vw)">
    <form class="dialog-form" @submit.prevent="saveBundle">
      <div class="form-grid">
        <label class="console-field">
          <span class="console-label">名称</span>
          <input v-model="form.name" class="console-input" :disabled="Boolean(editingName)" autocomplete="off">
        </label>
        <label class="console-field">
          <span class="console-label">数据与模型空间（Namespace）</span>
          <input :value="namespace || '空 Namespace'" class="console-input" disabled>
        </label>
        <label class="console-field span-2">
          <span class="console-label">Bundle 路径</span>
          <input v-model="form.path" class="console-input" autocomplete="off">
        </label>
        <label><input v-model="form.watch" type="checkbox"> 监听文件变化</label>
        <label><input v-model="form.enabled" type="checkbox"> 启用 Bundle</label>
        <label><input v-model="form.validate" type="checkbox"> 保存后校验</label>
        <label><input v-model="form.refresh" type="checkbox"> 保存后刷新</label>
      </div>
      <div class="notice">同一空间内 TM/QM canonical name 不能冲突；Bundle 名称仍需全局唯一。</div>
      <button class="console-button primary" type="submit">保存 Bundle</button>
    </form>
  </ElDialog>

  <ElDrawer
    v-model="advancedOpen"
    :title="`Bundle 高级操作 · ${advancedBundle?.name || ''}`"
    direction="rtl"
    size="min(680px, 100vw)"
    class="bundle-advanced-drawer"
  >
    <div class="advanced-warning">
      <span>ADVANCED / RAW RESOURCE API</span>
      <p>此处直接操作 Bundle 资源文件，不会改变 Bundle 来源关系或推测模型依赖。</p>
    </div>
    <nav class="advanced-tabs" aria-label="Bundle 高级操作类型">
      <button type="button" :class="{ active: resourceMode === 'export' }" @click="syncResourcePayload('export')">导出资源</button>
      <button type="button" :class="{ active: resourceMode === 'save' }" @click="syncResourcePayload('save')">保存资源</button>
    </nav>
    <label class="console-field">
      <span class="console-label">原始请求 JSON</span>
      <textarea v-model="resourcePayload" class="console-textarea advanced-editor" aria-label="Bundle 原始请求 JSON" spellcheck="false" />
    </label>
    <button class="console-button primary" type="button" :disabled="resourceBusy" @click="executeResourceOperation">
      {{ resourceBusy ? '执行中…' : resourceMode === 'export' ? '执行导出' : '确认并保存' }}
    </button>
    <RuntimeResultTable :rows="resourceResult" :loading="resourceBusy" />
  </ElDrawer>
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

.resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.advanced-warning {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px dashed var(--console-line-strong);
}

.advanced-warning span {
  font: 650 10px/1 var(--console-mono);
  letter-spacing: .1em;
}

.advanced-warning p {
  margin: 9px 0 0;
  color: var(--console-muted);
  font-size: 13px;
  line-height: 1.6;
}

.advanced-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin-bottom: 16px;
  border: 1px solid var(--console-line-strong);
}

.advanced-tabs button {
  min-height: 42px;
  border: 0;
  border-right: 1px solid var(--console-line);
  border-radius: 0;
  background: var(--console-panel);
  color: var(--console-muted);
  cursor: pointer;
  font: 650 11px/1 var(--console-mono);
}

.advanced-tabs button:last-child {
  border-right: 0;
}

.advanced-tabs button.active {
  background: var(--console-paper);
  color: var(--console-inverse);
}

.advanced-editor {
  min-height: 290px;
  font-family: var(--console-mono);
}

@media (max-width: 560px) {
  .catalog-intro {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
