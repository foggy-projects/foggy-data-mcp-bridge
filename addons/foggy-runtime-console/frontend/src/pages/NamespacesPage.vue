<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import ModelCatalog from '@/features/models/ModelCatalog.vue'
import BundleCatalog from '@/features/bundles/BundleCatalog.vue'
import AuthoringWorkspace from '@/features/authoring/AuthoringWorkspace.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { useRuntimeSession } from '@/stores/session'
import { useContextRail } from '@/stores/contextRail'
import { useNamespaceWorkspaceData } from '@/features/namespace/useNamespaceWorkspaceData'

type Workspace = 'overview' | 'models' | 'bundles' | 'authoring' | 'settings'
const workspaces: Workspace[] = ['overview', 'models', 'bundles', 'authoring', 'settings']

const route = useRoute()
const router = useRouter()
const session = useRuntimeSession()
const contextRail = useContextRail()
const data = useNamespaceWorkspaceData()
const bindingBusy = ref(false)
const binding = reactive({ dataSource: '' })

function canonicalNamespace(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function namespaceLabel(value: string): string {
  return value || '空 Namespace'
}

const workspace = computed<Workspace>(() => {
  const value = String(route.params.workspace || 'overview')
  return workspaces.includes(value as Workspace) ? value as Workspace : 'overview'
})

const currentNamespace = computed(() => session.namespace.value)
const selectedBundles = computed(() =>
  data.bundles.value.filter(item => canonicalNamespace(item.namespace) === currentNamespace.value)
)
const selectedBinding = computed(() => data.namespaceBindings.value[currentNamespace.value] || '')
const workspaceStatus = computed(() => {
  if (data.loading.value) return 'SYNCING'
  if (data.errorMessage.value) return 'ATTENTION'
  if (!selectedBinding.value && !selectedBundles.value.length && !data.models.value.length) return 'EMPTY'
  return 'READY'
})
const namespaceEntries = computed(() => {
  const names = new Set(data.discoveredNamespaces.value)
  names.add(currentNamespace.value)
  return [...names].map(name => ({
    name,
    label: namespaceLabel(name),
    bundleCount: data.bundles.value.filter(item => canonicalNamespace(item.namespace) === name).length,
    dataSource: data.namespaceBindings.value[name] || '',
    current: name === currentNamespace.value
  }))
})

function routeNamespace(): string {
  return Object.prototype.hasOwnProperty.call(route.query, 'ns')
    ? canonicalNamespace(route.query.ns)
    : session.namespace.value
}

async function replaceWorkspaceRoute(namespace = currentNamespace.value, next = workspace.value): Promise<void> {
  await router.replace({
    name: 'namespaces',
    params: { workspace: next === 'overview' ? undefined : next },
    query: { ns: namespace, workspaceId: next === 'authoring' ? route.query.workspaceId : undefined }
  })
}

async function selectWorkspace(next: Workspace): Promise<void> {
  await router.push({
    name: 'namespaces',
    params: { workspace: next === 'overview' ? undefined : next },
    query: { ns: currentNamespace.value, workspaceId: next === 'authoring' ? route.query.workspaceId : undefined }
  })
}

async function selectNamespace(namespace: string): Promise<void> {
  const normalized = canonicalNamespace(namespace)
  session.setNamespace(normalized)
  await router.push({
    name: 'namespaces',
    params: { workspace: workspace.value === 'overview' ? undefined : workspace.value },
    query: { ns: normalized }
  })
}

function syncBinding(): void {
  binding.dataSource = selectedBinding.value || data.datasources.value[0]?.name || ''
}

function syncContextRail(): void {
  contextRail.setContext({
    route: 'namespaces',
    eyebrow: 'Data + model space',
    title: '空间索引',
    description: '当前空间同时约束默认数据源、Bundle、模型和 Runtime 请求。',
    loading: data.loading.value,
    filterable: true,
    emptyText: '尚未从绑定或 Bundle 关系发现空间。',
    sections: [
      {
        id: 'namespaces',
        label: `${namespaceEntries.value.length} spaces`,
        items: namespaceEntries.value.map(item => ({
          id: item.name || 'empty-namespace',
          label: item.label,
          meta: `${item.bundleCount} Bundle · ${item.dataSource || '未绑定数据源'}`,
          badge: item.current ? 'CURRENT' : 'NS',
          active: item.current,
          action: () => void selectNamespace(item.name)
        }))
      }
    ]
  })
}

async function load(): Promise<void> {
  await data.load()
  syncBinding()
  syncContextRail()
}

async function bindNamespace(): Promise<void> {
  if (!currentNamespace.value || !binding.dataSource) {
    ElMessage.warning('空 Namespace 不能建立默认数据源绑定，请选择具名空间与数据源。')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将空间 ${currentNamespace.value} 的默认数据源设为 ${binding.dataSource}？`,
      '确认空间默认数据源',
      { confirmButtonText: '保存绑定', cancelButtonText: '取消', type: 'warning' }
    )
    bindingBusy.value = true
    await runtimeApi.put(
      `namespaces/${encodeURIComponent(currentNamespace.value)}/datasource`,
      { namespace: currentNamespace.value, dataSource: binding.dataSource }
    )
    ElMessage.success('空间默认数据源已更新。')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof RuntimeRequestError ? error.message : '默认数据源绑定失败。')
    }
  } finally {
    bindingBusy.value = false
  }
}

watch(
  () => [route.query.ns, route.params.workspace] as const,
  async () => {
    const fromRoute = routeNamespace()
    if (fromRoute !== session.namespace.value) session.setNamespace(fromRoute)
    if (!Object.prototype.hasOwnProperty.call(route.query, 'ns')) {
      await replaceWorkspaceRoute(fromRoute, workspace.value)
      return
    }
    await load()
  },
  { immediate: true }
)

watch(() => session.namespace.value, async (next, previous) => {
  if (next === previous || next === routeNamespace()) return
  await replaceWorkspaceRoute(next, workspace.value)
})

watch([namespaceEntries, workspace], syncContextRail, { deep: true })
onMounted(syncContextRail)
onBeforeUnmount(() => contextRail.clearContext('namespaces'))
</script>

<template>
  <PageHeader
    eyebrow="Data + model space / Namespace"
    :title="`数据与模型空间 · ${namespaceLabel(currentNamespace)}`"
    description="一个空间统一隔离默认数据源、Bundle 来源、语义模型与后续 Runtime 请求；Namespace 是其技术名称。"
  >
    <template #actions>
      <span class="status-chip">{{ workspaceStatus }}</span>
      <button class="console-button ghost" type="button" :disabled="data.loading.value" @click="load">重新读取</button>
    </template>
  </PageHeader>

  <div v-if="data.errorMessage.value" class="notice error-notice" role="alert">
    {{ data.errorMessage.value }}
  </div>

  <nav class="namespace-tabs" aria-label="数据与模型空间任务">
    <button
      v-for="(item, index) in [
        { id: 'overview', label: '概览' },
        { id: 'models', label: '分析模型（QM）', count: data.models.value.length },
        { id: 'bundles', label: 'Bundle 来源', count: selectedBundles.length },
        { id: 'authoring', label: '模型创作' },
        { id: 'settings', label: '空间设置' }
      ]"
      :key="item.id"
      type="button"
      :class="{ active: workspace === item.id }"
      :aria-current="workspace === item.id ? 'page' : undefined"
      @click="selectWorkspace(item.id as Workspace)"
    >
      <span>0{{ index + 1 }}</span>
      <strong>{{ item.label }}</strong>
      <small v-if="item.count !== undefined">{{ item.count }}</small>
    </button>
  </nav>

  <section v-if="workspace === 'overview'" class="space-overview" aria-labelledby="space-overview-title">
    <div class="space-manifesto">
      <span class="console-panel-kicker">CURRENT REQUEST SCOPE / X-NS</span>
      <h2 id="space-overview-title">{{ namespaceLabel(currentNamespace) }}</h2>
      <p>
        这是你的数据与模型工作边界。选择空间后，顶部状态、当前路由、工作区选中态和所有 Runtime
        请求会使用同一个 Namespace。
      </p>
      <div class="scope-legend">
        <span>NAMESPACE → BUNDLE → RESOURCE</span>
        <span>来源关系</span>
        <span>QM ⇢ TM</span>
        <span>依赖关系（未推测）</span>
      </div>
    </div>

    <div class="space-metrics" aria-label="当前空间摘要">
      <article>
        <span>DEFAULT DATASOURCE</span>
        <strong>{{ selectedBinding || '未绑定' }}</strong>
        <small>{{ selectedBinding ? '当前默认连接' : '可在空间设置中绑定' }}</small>
      </article>
      <article>
        <span>BUNDLE SOURCES</span>
        <strong>{{ selectedBundles.length }}</strong>
        <small>{{ selectedBundles.length ? '已注册资源来源' : '尚无 Bundle 来源' }}</small>
      </article>
      <article>
        <span>VISIBLE QM</span>
        <strong>{{ data.models.value.length }}</strong>
        <small>{{ data.models.value.length ? '当前可发现分析模型' : '当前没有可见 QM' }}</small>
      </article>
      <article>
        <span>SPACE STATUS</span>
        <strong>{{ workspaceStatus }}</strong>
        <small>{{ currentNamespace ? `X-NS: ${currentNamespace}` : '请求不发送 X-NS' }}</small>
      </article>
    </div>

    <div class="overview-shortcuts">
      <button type="button" @click="selectWorkspace('models')"><span>01</span><strong>浏览分析模型</strong><small>按 Bundle 来源扫描 QM</small></button>
      <button type="button" @click="selectWorkspace('bundles')"><span>02</span><strong>查看 Bundle 来源</strong><small>注册目录与高级资源操作</small></button>
      <button type="button" @click="selectWorkspace('settings')"><span>03</span><strong>空间设置</strong><small>维护默认数据源绑定</small></button>
    </div>
  </section>

  <ModelCatalog
    v-else-if="workspace === 'models'"
    :namespace="currentNamespace"
    :models="data.models.value"
    :loading="data.loading.value"
    @reload="load"
  />

  <BundleCatalog
    v-else-if="workspace === 'bundles'"
    :namespace="currentNamespace"
    :bundles="selectedBundles"
    :models="data.models.value"
    :loading="data.loading.value"
    @reload="load"
  />

  <AuthoringWorkspace
    v-else-if="workspace === 'authoring'"
    :namespace="currentNamespace"
    :bundles="selectedBundles"
    :capabilities="data.capabilities.value?.capabilities || {}"
  />

  <section v-else class="settings-grid" aria-labelledby="space-settings-title">
    <div class="console-panel settings-copy">
      <span class="console-panel-kicker">SPACE SETTINGS / DEFAULT BINDING</span>
      <h2 id="space-settings-title">空间设置</h2>
      <p>
        数据源注册表仍是 Runtime 全局资源。这里仅维护当前空间的默认数据源引用，不复制数据源
        CRUD，也不会创建新的 Namespace 实体。
      </p>
      <button class="console-button" type="button" @click="router.push({ name: 'datasources' })">前往全局数据源</button>
    </div>
    <form class="console-panel namespace-binding-form" @submit.prevent="bindNamespace">
      <label class="console-field">
        <span class="console-label">当前数据与模型空间</span>
        <input :value="namespaceLabel(currentNamespace)" class="console-input" disabled>
      </label>
      <label class="console-field">
        <span class="console-label">默认数据源</span>
        <select v-model="binding.dataSource" class="console-select" :disabled="!currentNamespace">
          <option value="">未绑定</option>
          <option v-for="item in data.datasources.value" :key="item.name" :value="item.name">
            {{ item.name }} · {{ item.type }}
          </option>
        </select>
      </label>
      <div v-if="!data.datasources.value.length" class="notice">全局注册表中没有可选数据源。</div>
      <button
        class="console-button primary"
        type="submit"
        :disabled="bindingBusy || !currentNamespace || !binding.dataSource"
      >
        {{ bindingBusy ? '正在保存…' : '保存默认绑定' }}
      </button>
    </form>
  </section>
</template>

<style scoped>
.namespace-tabs {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin-bottom: 16px;
  border: 1px solid var(--console-line-strong);
  background: var(--console-panel);
}

.namespace-tabs button {
  min-height: 52px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 0;
  border-right: 1px solid var(--console-line);
  border-radius: 0;
  background: transparent;
  color: var(--console-muted);
  cursor: pointer;
}

.namespace-tabs button:last-child {
  border-right: 0;
}

.namespace-tabs button:hover,
.namespace-tabs button:focus-visible {
  background: var(--console-panel-2);
  color: var(--console-text);
}

.namespace-tabs button.active {
  background: var(--console-paper);
  color: var(--console-inverse);
}

.namespace-tabs span,
.namespace-tabs small {
  font: 650 10px/1 var(--console-mono);
  opacity: .72;
}

.namespace-tabs strong {
  font-size: 13px;
}

.namespace-tabs small {
  margin-left: auto;
}

.space-overview {
  display: grid;
  grid-template-columns: minmax(300px, .75fr) minmax(0, 1.25fr);
  border: 1px solid var(--console-line-strong);
  background: var(--console-panel);
}

.space-manifesto {
  min-height: 430px;
  padding: clamp(24px, 4vw, 54px);
  border-right: 1px solid var(--console-line-strong);
  background:
    linear-gradient(var(--console-grid-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--console-grid-line) 1px, transparent 1px);
  background-size: 32px 32px;
}

.space-manifesto h2 {
  margin: 42px 0 18px;
  overflow-wrap: anywhere;
  font-size: clamp(38px, 6vw, 76px);
  letter-spacing: -.06em;
  line-height: .92;
}

.space-manifesto p {
  max-width: 580px;
  margin: 0;
  color: var(--console-muted);
  font-size: 14px;
  line-height: 1.8;
}

.scope-legend {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px 14px;
  margin-top: 44px;
  padding-top: 16px;
  border-top: 1px solid var(--console-line-strong);
  color: var(--console-dim);
  font: 10px/1.4 var(--console-mono);
}

.scope-legend span:nth-child(odd) {
  color: var(--console-text);
}

.space-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-content: start;
  gap: 1px;
  background: var(--console-line);
}

.space-metrics article {
  min-height: 170px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 22px;
  background: var(--console-panel);
}

.space-metrics span,
.space-metrics small {
  color: var(--console-dim);
  font: 10px/1.4 var(--console-mono);
}

.space-metrics strong {
  overflow-wrap: anywhere;
  font: 700 clamp(20px, 3vw, 34px)/1.1 var(--console-mono);
}

.overview-shortcuts {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-top: 1px solid var(--console-line-strong);
}

.overview-shortcuts button {
  min-height: 94px;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 5px 12px;
  padding: 18px;
  border: 0;
  border-right: 1px solid var(--console-line);
  border-radius: 0;
  background: var(--console-panel);
  color: var(--console-text);
  cursor: pointer;
  text-align: left;
}

.overview-shortcuts button:last-child {
  border-right: 0;
}

.overview-shortcuts button:hover,
.overview-shortcuts button:focus-visible {
  background: var(--console-paper);
  color: var(--console-inverse);
}

.overview-shortcuts span,
.overview-shortcuts small {
  color: inherit;
  font: 10px/1.4 var(--console-mono);
  opacity: .65;
}

.overview-shortcuts span {
  grid-row: span 2;
}

.overview-shortcuts strong {
  font-size: 14px;
}

.settings-grid {
  display: grid;
  grid-template-columns: minmax(280px, .75fr) minmax(0, 1.25fr);
  gap: 12px;
}

.settings-copy,
.namespace-binding-form {
  padding: 26px;
}

.settings-copy h2 {
  margin: 12px 0;
}

.settings-copy p {
  margin: 0 0 24px;
  color: var(--console-muted);
  line-height: 1.7;
}

@media (max-width: 900px) {
  .namespace-tabs {
    grid-template-columns: 1fr 1fr;
  }

  .namespace-tabs button:nth-child(2) {
    border-right: 0;
  }

  .namespace-tabs button:nth-child(-n + 2) {
    border-bottom: 1px solid var(--console-line);
  }

  .space-overview,
  .settings-grid {
    grid-template-columns: 1fr;
  }

  .space-manifesto {
    min-height: 0;
    border-right: 0;
    border-bottom: 1px solid var(--console-line-strong);
  }
}

@media (max-width: 560px) {
  .namespace-tabs {
    grid-template-columns: 1fr;
  }

  .namespace-tabs button {
    border-right: 0;
    border-bottom: 1px solid var(--console-line);
  }

  .space-metrics,
  .overview-shortcuts {
    grid-template-columns: 1fr;
  }

  .space-metrics article {
    min-height: 130px;
  }

  .overview-shortcuts button {
    border-right: 0;
    border-bottom: 1px solid var(--console-line);
  }
}
</style>
