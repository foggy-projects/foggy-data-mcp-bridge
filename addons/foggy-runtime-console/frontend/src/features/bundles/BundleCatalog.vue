<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElDialog, ElDrawer, ElMessage, ElMessageBox } from 'element-plus'
import ResourceCard from '@/components/resources/ResourceCard.vue'
import RuntimeResultTable from '@/components/RuntimeResultTable.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import {
  buildExportPayload,
  buildSavePayload,
  normalizeResourcePaths,
  resourcePathError,
  type BundleResourceFileDraft
} from './bundleResources'
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
const resourceWarnings = ref<string[]>([])
const resourceRoot = ref('')
const expertOverride = ref(false)
const exportScope = ref<'all' | 'selected'>('all')
const exportPaths = ref('')
const exportIncludeContent = ref(false)
const saveFiles = ref<BundleResourceFileDraft[]>([])
let nextFileId = 1
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
const writableBundle = computed(() =>
  advancedBundle.value?.managedByRuntimeApi !== false
  && advancedBundle.value?.canUpdate !== false
)
const exportPathErrors = computed(() =>
  exportScope.value === 'selected'
    ? normalizeResourcePaths(exportPaths.value)
      .map(path => ({ path, error: resourcePathError(path) }))
      .filter(item => item.error)
    : []
)
const savePathErrors = computed(() =>
  saveFiles.value
    .map(file => ({ id: file.id, path: file.path, error: resourcePathError(file.path) }))
    .filter(item => item.error)
)
const generatedPayload = computed(() =>
  resourceMode.value === 'export'
    ? buildExportPayload(
      props.namespace,
      advancedBundle.value?.name || '',
      exportScope.value === 'selected' ? exportPaths.value : '',
      exportIncludeContent.value
    )
    : buildSavePayload(
      props.namespace,
      advancedBundle.value?.name || '',
      saveFiles.value
    )
)

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

function clearResourceResult(): void {
  resourceResult.value = []
  resourceWarnings.value = []
  resourceRoot.value = ''
}

function syncGeneratedPayload(): void {
  if (!expertOverride.value) {
    resourcePayload.value = JSON.stringify(generatedPayload.value, null, 2)
  }
}

function selectResourceMode(mode: 'export' | 'save'): void {
  if (mode === 'save' && !writableBundle.value) return
  resourceMode.value = mode
  expertOverride.value = false
  clearResourceResult()
  syncGeneratedPayload()
}

function openAdvanced(item: BundleItem): void {
  advancedBundle.value = item
  exportScope.value = 'all'
  exportPaths.value = ''
  exportIncludeContent.value = false
  saveFiles.value = [{
    id: nextFileId++,
    path: '',
    content: '',
    baseSha256: ''
  }]
  expertOverride.value = false
  selectResourceMode('export')
  advancedOpen.value = true
}

function addSaveFile(): void {
  saveFiles.value.push({
    id: nextFileId++,
    path: '',
    content: '',
    baseSha256: ''
  })
}

function removeSaveFile(id: number): void {
  if (saveFiles.value.length === 1) return
  saveFiles.value = saveFiles.value.filter(file => file.id !== id)
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
  if (!expertOverride.value) {
    if (resourceMode.value === 'export' && exportScope.value === 'selected') {
      if (!normalizeResourcePaths(exportPaths.value).length) {
        ElMessage.warning('请至少输入一个需要导出的相对资源路径。')
        return
      }
      if (exportPathErrors.value.length) {
        ElMessage.error(exportPathErrors.value[0].error)
        return
      }
    }
    if (resourceMode.value === 'save') {
      if (!writableBundle.value) {
        ElMessage.error('此 Bundle 不是 Runtime-managed 可写来源。')
        return
      }
      if (savePathErrors.value.length) {
        ElMessage.error(savePathErrors.value[0].error)
        return
      }
    }
  }

  let payload: unknown = generatedPayload.value
  if (expertOverride.value) {
    try {
      payload = JSON.parse(resourcePayload.value)
    } catch {
      ElMessage.error('资源请求 JSON 格式无效，尚未发送。')
      return
    }
  }

  if (resourceMode.value === 'save') {
    try {
      await ElMessageBox.confirm(
        `将原子写入 ${advancedBundle.value?.name || 'Bundle'} 的 ${saveFiles.value.length} 个资源文件。`
          + ' 保存不会自动完成模型校验或刷新，确认继续？',
        '确认保存 Bundle 资源',
        { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  resourceBusy.value = true
  clearResourceResult()
  try {
    const result = await runtimeApi.post<Record<string, unknown>>(`resources/${resourceMode.value}`, payload)
    resourceResult.value = resultRows(result)
    resourceWarnings.value = Array.isArray(result.warnings)
      ? result.warnings.map(item => String(item))
      : []
    resourceRoot.value = typeof result.rootPath === 'string' ? result.rootPath : ''
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
watch(generatedPayload, syncGeneratedPayload, { deep: true })
watch(expertOverride, enabled => {
  if (!enabled) syncGeneratedPayload()
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
    size="min(760px, 100vw)"
    class="bundle-advanced-drawer"
  >
    <div class="bundle-operation-manifest">
      <div>
        <span class="console-panel-kicker">RESOURCE OPERATIONS / BUNDLE ROOT</span>
        <strong>{{ advancedBundle?.name }}</strong>
        <p>{{ advancedBundle?.path }}</p>
      </div>
      <div class="bundle-operation-facts" aria-label="Bundle 资源操作摘要">
        <span><small>NAMESPACE</small><strong>{{ namespace || '空 Namespace' }}</strong></span>
        <span><small>VISIBLE QM</small><strong>{{ advancedBundle ? modelCount(advancedBundle) : 0 }}</strong></span>
        <span><small>ACCESS</small><strong>{{ writableBundle ? 'READ / WRITE' : 'READ ONLY' }}</strong></span>
      </div>
    </div>

    <nav class="advanced-tabs" aria-label="Bundle 高级操作类型">
      <button type="button" :class="{ active: resourceMode === 'export' }" @click="selectResourceMode('export')">
        <span>01</span> 导出资源
      </button>
      <button
        type="button"
        :class="{ active: resourceMode === 'save' }"
        :disabled="!writableBundle"
        @click="selectResourceMode('save')"
      >
        <span>02</span> 保存资源
      </button>
    </nav>

    <section v-if="resourceMode === 'export'" class="operation-workbench" aria-labelledby="bundle-export-title">
      <div class="operation-heading">
        <span>01 / EXPORT</span>
        <div>
          <h3 id="bundle-export-title">读取 Bundle 资源</h3>
          <p>默认导出全部 `.tm`、`.qm` 与 model-list；也可以精确指定相对路径。</p>
        </div>
      </div>
      <div class="scope-switch" aria-label="导出范围">
        <button
          type="button"
          :class="{ active: exportScope === 'all' }"
          :aria-pressed="exportScope === 'all'"
          @click="exportScope = 'all'"
        >
          <strong>全部资源</strong>
          <small>扫描 Bundle 根目录</small>
        </button>
        <button
          type="button"
          :class="{ active: exportScope === 'selected' }"
          :aria-pressed="exportScope === 'selected'"
          @click="exportScope = 'selected'"
        >
          <strong>指定路径</strong>
          <small>每行一个相对路径</small>
        </button>
      </div>
      <label v-if="exportScope === 'selected'" class="console-field">
        <span class="console-label">资源相对路径</span>
        <textarea
          v-model="exportPaths"
          class="console-textarea path-editor"
          placeholder="models/orders.qm&#10;models/orders.tm"
          spellcheck="false"
        />
      </label>
      <div v-if="exportPathErrors.length" class="notice error-notice" role="alert">
        {{ exportPathErrors[0].path }} · {{ exportPathErrors[0].error }}
      </div>
      <label class="operation-check">
        <input v-model="exportIncludeContent" type="checkbox">
        <span><strong>包含文件内容</strong><small>关闭时只读取路径、类型、大小和 SHA-256。</small></span>
      </label>
    </section>

    <section v-else class="operation-workbench" aria-labelledby="bundle-save-title">
      <div class="operation-heading">
        <span>02 / SAVE</span>
        <div>
          <h3 id="bundle-save-title">原子保存资源文件</h3>
          <p>base SHA 可防止覆盖导出后已被他人修改的文件；Runtime 是最终冲突权威。</p>
        </div>
        <button class="console-button compact" type="button" @click="addSaveFile">添加文件</button>
      </div>
      <div class="save-file-list">
        <article v-for="(file, index) in saveFiles" :key="file.id" class="save-file-card">
          <div class="save-file-index">
            <span>FILE {{ String(index + 1).padStart(2, '0') }}</span>
            <button
              type="button"
              :disabled="saveFiles.length === 1"
              :aria-label="`移除资源文件 ${index + 1}`"
              @click="removeSaveFile(file.id)"
            >
              ×
            </button>
          </div>
          <label class="console-field">
            <span class="console-label">相对路径</span>
            <input
              v-model="file.path"
              class="console-input"
              :aria-label="`资源文件 ${index + 1} 相对路径`"
              placeholder="models/orders.qm"
              autocomplete="off"
            >
          </label>
          <label class="console-field">
            <span class="console-label">Base SHA-256（可选）</span>
            <input
              v-model="file.baseSha256"
              class="console-input"
              :aria-label="`资源文件 ${index + 1} Base SHA-256`"
              placeholder="从最近一次导出复制"
              autocomplete="off"
            >
          </label>
          <label class="console-field">
            <span class="console-label">UTF-8 文件内容</span>
            <textarea
              v-model="file.content"
              class="console-textarea resource-content-editor"
              :aria-label="`资源文件 ${index + 1} 内容`"
              spellcheck="false"
            />
          </label>
          <div v-if="resourcePathError(file.path)" class="path-error">
            {{ resourcePathError(file.path) }}
          </div>
        </article>
      </div>
      <div class="advanced-warning">
        <span>SAVE ≠ PUBLISH</span>
        <p>保存只写入资源文件，不会自动完成模型校验或刷新。写入成功后请到模型生命周期中心执行后续动作。</p>
      </div>
    </section>

    <details class="expert-payload">
      <summary>
        <span>ADVANCED / RAW</span>
        <strong>专家请求 JSON</strong>
      </summary>
      <div class="expert-payload-body">
        <label class="operation-check">
          <input v-model="expertOverride" type="checkbox">
          <span>
            <strong>使用原始 JSON 覆盖向导</strong>
            <small>启用后，执行请求不再由上方结构化字段生成。</small>
          </span>
        </label>
        <label class="console-field">
          <span class="console-label">原始请求 JSON</span>
          <textarea
            v-model="resourcePayload"
            class="console-textarea advanced-editor"
            aria-label="Bundle 原始请求 JSON"
            :readonly="!expertOverride"
            spellcheck="false"
          />
        </label>
      </div>
    </details>

    <div class="operation-execute">
      <div>
        <span>{{ resourceMode === 'export' ? 'READ OPERATION' : 'ATOMIC WRITE' }}</span>
        <small>
          {{ expertOverride ? '专家 JSON 将覆盖向导 payload' : '请求由结构化向导生成' }}
        </small>
      </div>
      <button
        class="console-button primary"
        type="button"
        :disabled="resourceBusy || (resourceMode === 'save' && (!writableBundle || Boolean(savePathErrors.length)))"
        @click="executeResourceOperation"
      >
        {{ resourceBusy ? '执行中…' : resourceMode === 'export' ? '执行导出' : '确认并保存' }}
      </button>
    </div>

    <section v-if="resourceBusy || resourceResult.length || resourceWarnings.length" class="operation-result">
      <div class="console-panel-head">
        <span class="console-panel-title">资源操作结果</span>
        <span class="console-panel-kicker">{{ resourceResult.length }} FILES</span>
      </div>
      <div v-if="resourceRoot" class="result-root"><span>ROOT</span><code>{{ resourceRoot }}</code></div>
      <div v-if="resourceWarnings.length" class="notice">{{ resourceWarnings.join(' · ') }}</div>
      <RuntimeResultTable :rows="resourceResult" :loading="resourceBusy" />
    </section>
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

.bundle-operation-manifest {
  margin-bottom: 12px;
  border: 1px solid var(--console-line-strong);
  background:
    linear-gradient(var(--console-grid-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--console-grid-line) 1px, transparent 1px),
    var(--console-panel);
  background-size: 24px 24px;
}

.bundle-operation-manifest > div:first-child {
  padding: 22px;
  border-bottom: 1px solid var(--console-line-strong);
}

.bundle-operation-manifest > div:first-child > strong {
  display: block;
  margin: 9px 0 6px;
  font: 700 22px/1.2 var(--console-mono);
}

.bundle-operation-manifest p {
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--console-muted);
  font: 11px/1.5 var(--console-mono);
}

.bundle-operation-facts {
  display: grid;
  grid-template-columns: 1fr .65fr 1fr;
  gap: 1px;
  background: var(--console-line);
}

.bundle-operation-facts > span {
  min-width: 0;
  padding: 12px;
  background: var(--console-panel);
}

.bundle-operation-facts small,
.bundle-operation-facts strong {
  display: block;
  overflow: hidden;
  font-family: var(--console-mono);
  text-overflow: ellipsis;
}

.bundle-operation-facts small {
  color: var(--console-dim);
  font-size: 9px;
}

.bundle-operation-facts strong {
  margin-top: 5px;
  font-size: 11px;
}

.advanced-warning {
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

.advanced-tabs button:disabled {
  cursor: not-allowed;
  opacity: .42;
}

.advanced-tabs button span {
  margin-right: 7px;
  opacity: .65;
}

.operation-workbench {
  margin-bottom: 12px;
  padding: 18px;
  border: 1px solid var(--console-line);
  background: var(--console-panel-2);
}

.operation-heading {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
  margin-bottom: 16px;
}

.operation-heading > span {
  color: var(--console-dim);
  font: 10px/1.4 var(--console-mono);
}

.operation-heading h3 {
  margin: 0 0 5px;
  font-size: 15px;
}

.operation-heading p {
  margin: 0;
  color: var(--console-muted);
  font-size: 12px;
  line-height: 1.55;
}

.scope-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  margin-bottom: 12px;
  border: 1px solid var(--console-line);
  background: var(--console-line);
}

.scope-switch button {
  min-height: 70px;
  padding: 13px;
  border: 0;
  border-radius: 0;
  background: var(--console-panel);
  color: var(--console-muted);
  cursor: pointer;
  text-align: left;
}

.scope-switch button.active {
  background: var(--console-paper);
  color: var(--console-inverse);
}

.scope-switch strong,
.scope-switch small {
  display: block;
}

.scope-switch strong {
  font-size: 12px;
}

.scope-switch small {
  margin-top: 6px;
  font: 9px/1.4 var(--console-mono);
  opacity: .72;
}

.path-editor {
  min-height: 130px;
  margin-bottom: 10px;
  font-family: var(--console-mono);
}

.operation-check {
  min-height: 54px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--console-line);
  background: var(--console-panel);
}

.operation-check input {
  margin-top: 3px;
}

.operation-check strong,
.operation-check small {
  display: block;
}

.operation-check strong {
  font-size: 12px;
}

.operation-check small {
  margin-top: 5px;
  color: var(--console-dim);
  font-size: 10px;
  line-height: 1.45;
}

.save-file-list {
  display: grid;
  gap: 10px;
}

.save-file-card {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--console-line);
  background: var(--console-panel);
}

.save-file-index {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 9px;
  border-bottom: 1px solid var(--console-line);
}

.save-file-index span {
  color: var(--console-dim);
  font: 9px/1 var(--console-mono);
  letter-spacing: .1em;
}

.save-file-index button {
  width: 28px;
  height: 28px;
  border: 1px solid var(--console-line);
  border-radius: 0;
  background: transparent;
  color: var(--console-text);
  cursor: pointer;
}

.save-file-index button:disabled {
  opacity: .3;
}

.save-file-card .console-field:nth-of-type(3) {
  grid-column: 1 / -1;
}

.resource-content-editor {
  min-height: 210px;
  font-family: var(--console-mono);
}

.path-error {
  grid-column: 1 / -1;
  color: var(--console-text);
  font: 10px/1.5 var(--console-mono);
}

.save-file-list + .advanced-warning {
  margin-top: 12px;
}

.expert-payload {
  margin-bottom: 12px;
  border: 1px solid var(--console-line);
  background: var(--console-panel-2);
}

.expert-payload summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 15px 18px;
  cursor: pointer;
}

.expert-payload summary span {
  color: var(--console-dim);
  font: 9px/1 var(--console-mono);
}

.expert-payload summary strong {
  font-size: 12px;
}

.expert-payload-body {
  display: grid;
  gap: 12px;
  padding: 0 14px 14px;
}

.advanced-editor {
  min-height: 290px;
  font-family: var(--console-mono);
}

.advanced-editor:read-only {
  color: var(--console-muted);
}

.operation-execute {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;
  padding: 14px;
  border: 3px double var(--console-line-strong);
}

.operation-execute span,
.operation-execute small {
  display: block;
  font-family: var(--console-mono);
}

.operation-execute span {
  font-size: 10px;
  font-weight: 700;
}

.operation-execute small {
  margin-top: 5px;
  color: var(--console-dim);
  font-size: 9px;
}

.operation-result {
  border: 1px solid var(--console-line);
}

.result-root {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr);
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--console-line);
}

.result-root span {
  color: var(--console-dim);
  font: 9px/1.5 var(--console-mono);
}

.result-root code {
  overflow-wrap: anywhere;
  font: 10px/1.5 var(--console-mono);
}

@media (max-width: 560px) {
  .catalog-intro {
    align-items: stretch;
    flex-direction: column;
  }

  .bundle-operation-facts {
    grid-template-columns: 1fr;
  }

  .operation-heading {
    grid-template-columns: 1fr;
  }

  .save-file-card {
    grid-template-columns: 1fr;
  }

  .save-file-card .console-field:nth-of-type(3) {
    grid-column: auto;
  }

  .operation-execute {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
