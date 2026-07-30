<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElDialog, ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import RuntimeResultTable from '@/components/RuntimeResultTable.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { useRuntimeSession } from '@/stores/session'
import { useContextRail } from '@/stores/contextRail'

interface Bundle {
  name: string
  namespace?: string
  path: string
  watch?: boolean
  enabled?: boolean
  source?: string
  managedByRuntimeApi?: boolean
  canUpdate?: boolean
  canRemove?: boolean
  status?: string
  message?: string
}

interface BundleList {
  bundles: Bundle[]
  warnings?: string[]
}

const session = useRuntimeSession()
const contextRail = useContextRail()
const loading = ref(true)
const errorMessage = ref('')
const search = ref('')
const bundles = ref<Bundle[]>([])
const activeBundle = ref('')
const dialogOpen = ref(false)
const editingName = ref('')
const resourceBusy = ref(false)
const resourceResult = ref<Record<string, unknown>[]>([])
const resourceMode = ref<'export' | 'save'>('export')
const resourcePayload = ref(JSON.stringify({
  namespace: session.namespace.value,
  bundle: '',
  paths: [],
  includeContent: false
}, null, 2))
const form = reactive({
  name: '',
  namespace: session.namespace.value,
  path: '',
  watch: false,
  enabled: true,
  validate: true,
  refresh: true
})

const filteredBundles = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return bundles.value
  return bundles.value.filter(item =>
    [item.name, item.namespace, item.path, item.status, item.source]
      .some(value => value?.toLowerCase().includes(keyword))
  )
})

function errorText(error: unknown): string {
  return error instanceof RuntimeRequestError ? error.message : 'Bundle 操作失败。'
}

function syncContextRail(): void {
  contextRail.setContext({
    route: 'bundles',
    eyebrow: 'Runtime registry',
    title: 'Bundle List',
    description: '快速定位已注册的模型资源目录。',
    loading: loading.value,
    filterable: true,
    emptyText: '尚未注册 Bundle。',
    sections: [{
      id: 'bundles',
      label: `${bundles.value.length} registered`,
      items: bundles.value.map(item => ({
        id: item.name,
        label: item.name,
        meta: item.path,
        badge: item.status || 'registered',
        active: activeBundle.value === item.name,
        action: () => {
          activeBundle.value = item.name
          search.value = item.name
          syncContextRail()
        }
      }))
    }]
  })
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  syncContextRail()
  try {
    const result = await runtimeApi.get<BundleList>('bundles')
    bundles.value = result.bundles || []
    if (activeBundle.value && !bundles.value.some(item => item.name === activeBundle.value)) {
      activeBundle.value = ''
    }
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
    syncContextRail()
  }
}

function resetForm(): void {
  editingName.value = ''
  Object.assign(form, {
    name: '',
    namespace: session.namespace.value,
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

function openEdit(item: Bundle): void {
  activeBundle.value = item.name
  syncContextRail()
  editingName.value = item.name
  Object.assign(form, {
    name: item.name,
    namespace: item.namespace || session.namespace.value,
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
    ElMessage.success(editingName.value ? 'Bundle 已更新。' : 'Bundle 已添加。')
    await load()
  } catch (error) {
    ElMessage.error(errorText(error))
  }
}

async function removeBundle(item: Bundle): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `移除 Bundle ${item.name}？这会改变 Runtime 当前加载的模型资源。`,
      '确认移除 Bundle',
      { type: 'warning', confirmButtonText: '确认移除', cancelButtonText: '取消' }
    )
    await runtimeApi.delete(`bundles/${encodeURIComponent(item.name)}`)
    ElMessage.success('Bundle 已移除。')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorText(error))
  }
}

function selectResourceMode(mode: 'export' | 'save'): void {
  resourceMode.value = mode
  resourceResult.value = []
  resourcePayload.value = mode === 'export'
    ? JSON.stringify({
      namespace: session.namespace.value,
      bundle: bundles.value[0]?.name || '',
      paths: [],
      includeContent: false
    }, null, 2)
    : JSON.stringify({
      namespace: session.namespace.value,
      bundle: bundles.value[0]?.name || '',
      files: [{ path: 'models/example.qm', content: '// QM content', baseSha256: null }],
      validate: true,
      refresh: false
    }, null, 2)
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
        '保存资源会写入 Bundle 文件。请确认目标 bundle、路径和文件数量均正确。',
        '确认保存资源',
        { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }

  resourceBusy.value = true
  try {
    const result = await runtimeApi.post<Record<string, unknown>>(
      `resources/${resourceMode.value}`,
      payload
    )
    resourceResult.value = resultRows(result)
    ElMessage.success(resourceMode.value === 'export' ? '资源已导出。' : '资源已保存。')
  } catch (error) {
    ElMessage.error(errorText(error))
  } finally {
    resourceBusy.value = false
  }
}

contextRail.setContext({
  route: 'bundles',
  eyebrow: 'Runtime registry',
  title: 'Bundle List',
  description: '快速定位已注册的模型资源目录。',
  loading: true,
  filterable: true,
  emptyText: '尚未注册 Bundle。',
  sections: []
})
onMounted(load)
onBeforeUnmount(() => contextRail.clearContext('bundles'))
</script>

<template>
  <PageHeader
    eyebrow="Runtime resources"
    title="Bundle 与资源"
    description="管理外部 Bundle，并通过现有 Runtime API 导出或保存模型资源。配置内置 Bundle 的不可变限制由服务端返回。"
  >
    <template #actions>
      <button class="console-button ghost" type="button" :disabled="loading" @click="load">重新读取</button>
      <button class="console-button primary" type="button" @click="openCreate">新增 Bundle</button>
    </template>
  </PageHeader>

  <div v-if="errorMessage" class="notice error-notice" role="alert">{{ errorMessage }}</div>

  <div class="toolbar">
    <label class="console-field">
      <span class="console-label">搜索 Bundle</span>
      <input v-model="search" class="console-input" type="search" placeholder="名称、namespace 或路径">
    </label>
    <div class="toolbar-spacer" />
    <span class="status-chip">{{ filteredBundles.length }} bundles</span>
  </div>

  <section class="console-panel">
    <div class="console-panel-head">
      <span class="console-panel-title">Bundle Registry</span>
      <span class="console-panel-kicker">RUNTIME MANAGED</span>
    </div>
    <div class="resource-table-wrap">
      <table class="resource-table">
        <thead>
          <tr><th>Bundle</th><th>Namespace</th><th>路径</th><th>状态</th><th>来源</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredBundles" :key="item.name">
            <td><div class="cell-title">{{ item.name }}</div><div class="cell-subtitle">{{ item.watch ? 'watch enabled' : 'manual refresh' }}</div></td>
            <td>{{ item.namespace || 'default' }}</td>
            <td><div class="cell-subtitle">{{ item.path }}</div></td>
            <td><span class="status-chip" :class="{ warning: item.status !== 'ready' && item.status !== 'READY' }">{{ item.status || 'registered' }}</span></td>
            <td>{{ item.source || 'runtime' }}</td>
            <td><div class="row-actions">
              <button class="console-button compact" type="button" :disabled="item.canUpdate === false" @click="openEdit(item)">编辑</button>
              <button class="console-button compact danger" type="button" :disabled="item.canRemove === false" @click="removeBundle(item)">移除</button>
            </div></td>
          </tr>
        </tbody>
      </table>
      <div v-if="!loading && !filteredBundles.length" class="empty-state">
        <div><strong>没有匹配的 Bundle</strong>调整搜索条件，或注册一个外部 Bundle。</div>
      </div>
    </div>
  </section>

  <section class="console-panel resources-panel">
    <div class="console-panel-head">
      <span class="console-panel-title">资源操作台</span>
      <span class="console-panel-kicker">EXPORT / SAVE</span>
      <div class="toolbar-spacer" />
      <button class="console-button compact" :class="{ primary: resourceMode === 'export' }" type="button" @click="selectResourceMode('export')">导出</button>
      <button class="console-button compact" :class="{ primary: resourceMode === 'save' }" type="button" @click="selectResourceMode('save')">保存</button>
    </div>
    <div class="workbench-grid">
      <div class="workbench-editor">
        <div class="workbench-toolbar">
          <span class="console-panel-kicker">{{ resourceMode.toUpperCase() }} PAYLOAD</span>
          <button class="console-button compact primary" type="button" :disabled="resourceBusy" @click="executeResourceOperation">
            {{ resourceBusy ? '执行中…' : '执行' }}
          </button>
        </div>
        <label>
          <span class="visually-hidden">资源操作 JSON</span>
          <textarea v-model="resourcePayload" class="console-textarea" spellcheck="false" />
        </label>
        <div class="workbench-hint">保存前会再次显示目标确认；响应中的资源信息不会包含 Runtime Token。</div>
      </div>
      <div class="workbench-result">
        <div class="console-panel-head"><span class="console-panel-title">操作结果</span></div>
        <RuntimeResultTable :rows="resourceResult" :loading="resourceBusy" />
      </div>
    </div>
  </section>

  <ElDialog v-model="dialogOpen" :title="editingName ? '编辑 Bundle' : '新增 Bundle'" width="min(650px, 94vw)">
    <form class="dialog-form" @submit.prevent="saveBundle">
      <div class="form-grid">
        <label class="console-field">
          <span class="console-label">名称</span>
          <input v-model="form.name" class="console-input" :disabled="Boolean(editingName)" autocomplete="off">
        </label>
        <label class="console-field">
          <span class="console-label">Namespace</span>
          <input v-model="form.namespace" class="console-input" autocomplete="off">
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
      <button class="console-button primary" type="submit">保存 Bundle</button>
    </form>
  </ElDialog>
</template>

<style scoped>
.resources-panel {
  margin-top: 14px;
}
</style>
