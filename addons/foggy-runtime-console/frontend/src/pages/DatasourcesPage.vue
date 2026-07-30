<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElDialog, ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { useContextRail } from '@/stores/contextRail'

interface Datasource {
  name: string
  type: string
  jdbcUrl: string
  username?: string
  enabled: boolean
  source?: string
  managedByRuntimeApi?: boolean
  canUpdate?: boolean
  canRemove?: boolean
  canTest?: boolean
  status?: string
  message?: string
}

interface DatasourceList {
  datasources: Datasource[]
  warnings?: string[]
}

interface DatasourceDiagnostics extends DatasourceList {
  registryEnabled?: boolean
  registryExists?: boolean
  managedDatasourceCount?: number
  namespaceBindings?: Record<string, string>
}

interface DatasourceForm {
  name: string
  type: string
  jdbcUrl: string
  username: string
  password: string
  passwordRef: string
  enabled: boolean
}

const contextRail = useContextRail()
const loading = ref(true)
const busyName = ref('')
const errorMessage = ref('')
const search = ref('')
const datasources = ref<Datasource[]>([])
const activeDatasource = ref('')
const diagnostics = ref<DatasourceDiagnostics | null>(null)
const dialogOpen = ref(false)
const editingName = ref('')
const form = reactive<DatasourceForm>({
  name: '',
  type: 'mysql',
  jdbcUrl: '',
  username: '',
  password: '',
  passwordRef: '',
  enabled: true
})
const filteredDatasources = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return datasources.value
  return datasources.value.filter(item =>
    [item.name, item.type, item.jdbcUrl, item.status, item.source]
      .some(value => value?.toLowerCase().includes(keyword))
  )
})

function errorText(error: unknown): string {
  return error instanceof RuntimeRequestError ? error.message : 'Runtime 数据源操作失败。'
}

function syncContextRail(): void {
  contextRail.setContext({
    route: 'datasources',
    eyebrow: 'Connections',
    title: 'Datasource List',
    description: '只管理 Runtime 连接；Namespace 关系在独立工作区维护。',
    loading: loading.value,
    filterable: true,
    emptyText: '没有可用的数据源。',
    sections: [
      {
        id: 'datasources',
        label: `${datasources.value.length} connections`,
        items: datasources.value.map(item => ({
          id: item.name,
          label: item.name,
          meta: item.jdbcUrl || item.source || item.type,
          badge: item.type,
          active: activeDatasource.value === item.name,
          action: () => {
            activeDatasource.value = item.name
            search.value = item.name
            syncContextRail()
          }
        }))
      }
    ]
  })
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  syncContextRail()
  try {
    const [list, detail] = await Promise.all([
      runtimeApi.get<DatasourceList>('datasources'),
      runtimeApi.get<DatasourceDiagnostics>('datasources/diagnostics')
    ])
    datasources.value = list.datasources || []
    diagnostics.value = detail
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
    syncContextRail()
  }
}

function resetForm(): void {
  Object.assign(form, {
    name: '',
    type: 'mysql',
    jdbcUrl: '',
    username: '',
    password: '',
    passwordRef: '',
    enabled: true
  })
  editingName.value = ''
}

function openCreate(): void {
  resetForm()
  dialogOpen.value = true
}

function openEdit(item: Datasource): void {
  activeDatasource.value = item.name
  syncContextRail()
  editingName.value = item.name
  Object.assign(form, {
    name: item.name,
    type: item.type,
    jdbcUrl: item.jdbcUrl,
    username: item.username || '',
    password: '',
    passwordRef: '',
    enabled: item.enabled
  })
  dialogOpen.value = true
}

async function saveDatasource(): Promise<void> {
  if (!form.name.trim() || !form.jdbcUrl.trim()) {
    ElMessage.warning('请填写数据源名称和 JDBC URL。')
    return
  }
  busyName.value = form.name
  const payload = {
    ...form,
    password: form.password || undefined,
    passwordRef: form.passwordRef || undefined,
    replace: Boolean(editingName.value)
  }
  try {
    if (editingName.value) {
      await runtimeApi.put(`datasources/${encodeURIComponent(editingName.value)}`, payload)
    } else {
      await runtimeApi.post('datasources', payload)
    }
    form.password = ''
    dialogOpen.value = false
    ElMessage.success(editingName.value ? '数据源已更新。' : '数据源已添加。')
    await load()
  } catch (error) {
    form.password = ''
    ElMessage.error(errorText(error))
  } finally {
    busyName.value = ''
  }
}

async function testDatasource(item: Datasource): Promise<void> {
  busyName.value = item.name
  try {
    await runtimeApi.post(`datasources/${encodeURIComponent(item.name)}/test`, {})
    ElMessage.success(`${item.name} 连接测试通过。`)
    await load()
  } catch (error) {
    ElMessage.error(errorText(error))
  } finally {
    busyName.value = ''
  }
}

async function removeDatasource(item: Datasource): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除数据源 ${item.name}？依赖它的 namespace 和模型可能无法继续工作。`,
      '确认删除数据源',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    busyName.value = item.name
    await runtimeApi.delete(`datasources/${encodeURIComponent(item.name)}`)
    ElMessage.success('数据源已删除。')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorText(error))
  } finally {
    busyName.value = ''
  }
}

contextRail.setContext({
  route: 'datasources',
  eyebrow: 'Connections',
  title: 'Datasource List',
  description: '只管理 Runtime 连接；Namespace 关系在独立工作区维护。',
  loading: true,
  filterable: true,
  emptyText: '没有可用的数据源。',
  sections: []
})
onMounted(load)
onBeforeUnmount(() => contextRail.clearContext('datasources'))
</script>

<template>
  <PageHeader
    eyebrow="Connections"
    title="数据源"
    description="管理 Runtime 数据源与连接测试。Namespace 默认绑定已移至独立 Namespace 工作区。"
  >
    <template #actions>
      <button class="console-button ghost" type="button" :disabled="loading" @click="load">重新读取</button>
      <button class="console-button primary" type="button" @click="openCreate">新增数据源</button>
    </template>
  </PageHeader>

  <div v-if="errorMessage" class="notice error-notice" role="alert">{{ errorMessage }}</div>

  <div class="toolbar">
    <label class="console-field">
      <span class="console-label">搜索数据源</span>
      <input v-model="search" class="console-input" type="search" placeholder="名称、类型、地址或状态">
    </label>
    <div class="toolbar-spacer" />
    <span class="status-chip">{{ diagnostics?.managedDatasourceCount ?? datasources.length }} managed</span>
    <span class="status-chip" :class="{ warning: !diagnostics?.registryExists }">
      Registry {{ diagnostics?.registryExists ? 'READY' : 'EMPTY' }}
    </span>
  </div>

  <section class="console-panel datasource-registry-panel">
      <div class="console-panel-head">
        <span class="console-panel-title">数据源 Registry</span>
        <span class="console-panel-kicker">{{ filteredDatasources.length }} ITEMS</span>
      </div>
      <div class="resource-table-wrap">
        <table class="resource-table">
          <thead>
            <tr>
              <th>数据源</th><th>类型</th><th>连接地址</th><th>状态</th><th>来源</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filteredDatasources" :key="item.name">
              <td>
                <div class="cell-title">{{ item.name }}</div>
                <div class="cell-subtitle">{{ item.enabled ? 'enabled' : 'disabled' }}</div>
              </td>
              <td>{{ item.type }}</td>
              <td><div class="cell-subtitle">{{ item.jdbcUrl }}</div></td>
              <td><span class="status-chip" :class="{ warning: item.status !== 'ready' && item.status !== 'READY' }">{{ item.status || 'unknown' }}</span></td>
              <td>{{ item.source || 'runtime' }}</td>
              <td>
                <div class="row-actions">
                  <button class="console-button compact" type="button" :disabled="busyName === item.name || item.canTest === false" @click="testDatasource(item)">测试</button>
                  <button class="console-button compact" type="button" :disabled="item.canUpdate === false" @click="openEdit(item)">编辑</button>
                  <button class="console-button compact danger" type="button" :disabled="item.canRemove === false" @click="removeDatasource(item)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!loading && !filteredDatasources.length" class="empty-state">
          <div><strong>没有匹配的数据源</strong>调整搜索条件，或创建第一个 Runtime 数据源。</div>
        </div>
      </div>
  </section>

  <ElDialog v-model="dialogOpen" :title="editingName ? '编辑数据源' : '新增数据源'" width="min(680px, 94vw)" destroy-on-close>
    <form class="dialog-form" @submit.prevent="saveDatasource">
      <div class="form-grid">
        <label class="console-field">
          <span class="console-label">名称</span>
          <input v-model="form.name" class="console-input" :disabled="Boolean(editingName)" autocomplete="off">
        </label>
        <label class="console-field">
          <span class="console-label">类型</span>
          <select v-model="form.type" class="console-select">
            <option value="mysql">MySQL</option>
            <option value="postgresql">PostgreSQL</option>
            <option value="sqlite">SQLite</option>
            <option value="sqlserver">SQL Server</option>
          </select>
        </label>
        <label class="console-field span-2">
          <span class="console-label">JDBC URL</span>
          <input v-model="form.jdbcUrl" class="console-input" placeholder="jdbc:mysql://host:3306/database" autocomplete="off">
        </label>
        <label class="console-field">
          <span class="console-label">用户名</span>
          <input v-model="form.username" class="console-input" autocomplete="username">
        </label>
        <label class="console-field">
          <span class="console-label">密码（不会回显）</span>
          <input v-model="form.password" class="console-input" type="password" autocomplete="new-password">
        </label>
        <label class="console-field span-2">
          <span class="console-label">Password Ref（优先于明文密码）</span>
          <input v-model="form.passwordRef" class="console-input" placeholder="env:FOGGY_DB_PASSWORD" autocomplete="off">
        </label>
      </div>
      <button class="console-button primary" type="submit" :disabled="Boolean(busyName)">
        {{ busyName ? '正在保存…' : '保存数据源' }}
      </button>
    </form>
  </ElDialog>
</template>

<style scoped>
.datasource-registry-panel {
  min-height: 424px;
}
</style>
