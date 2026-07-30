<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { useRuntimeSession } from '@/stores/session'

interface Capabilities {
  engine: string
  runtimeApiVersion: string
  schemaVersion: string
  enabled: boolean
  securityMode: string
  capabilities: Record<string, string>
  warnings: string[]
}

interface DatasourceList {
  datasources?: unknown[]
  warnings?: string[]
}

const session = useRuntimeSession()
const loading = ref(true)
const errorMessage = ref('')
const capabilities = ref<Capabilities | null>(null)
const datasourceCount = ref<number | null>(null)
const modelCount = ref<number | null>(null)

const capabilityCount = computed(() => Object.keys(capabilities.value?.capabilities || {}).length)

function countModels(value: unknown): number {
  if (Array.isArray(value)) return value.length
  if (!value || typeof value !== 'object') return 0
  const record = value as Record<string, unknown>
  const direct = record.models
  if (Array.isArray(direct)) return direct.length
  if (direct && typeof direct === 'object') return Object.keys(direct).length
  if (record.data) return countModels(record.data)
  return 0
}

async function loadOverview(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [capabilityResult, datasourceResult, modelResult] = await Promise.allSettled([
      runtimeApi.get<Capabilities>('capabilities'),
      runtimeApi.get<DatasourceList>('datasources'),
      runtimeApi.get<Record<string, unknown>>('models')
    ])

    if (capabilityResult.status === 'rejected') throw capabilityResult.reason
    capabilities.value = capabilityResult.value
    datasourceCount.value = datasourceResult.status === 'fulfilled'
      ? datasourceResult.value.datasources?.length || 0
      : null
    modelCount.value = modelResult.status === 'fulfilled' ? countModels(modelResult.value) : null
  } catch (error) {
    errorMessage.value = error instanceof RuntimeRequestError
      ? error.message
      : '无法加载 Runtime 概览。'
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)
</script>

<template>
  <PageHeader
    eyebrow="Runtime pulse"
    title="运行概览"
    description="单个 Runtime 的能力、安全模式与管理资源概况。这里不聚合多实例，也不替代平台级监控。"
  >
    <template #actions>
      <button class="console-button ghost" type="button" :disabled="loading" @click="loadOverview">
        重新读取
      </button>
      <RouterLink class="console-button primary" :to="{ name: 'query' }">
        打开查询工作台
      </RouterLink>
    </template>
  </PageHeader>

  <div v-if="errorMessage" class="notice error-notice" role="alert">
    {{ errorMessage }}
  </div>

  <section class="overview-metrics" aria-label="Runtime 关键指标">
    <article class="console-card metric-card">
      <span class="metric-line" />
      <div class="metric-label">Runtime 状态</div>
      <div class="metric-value">{{ loading ? '···' : capabilities?.enabled ? 'READY' : 'OFFLINE' }}</div>
      <div class="metric-foot">{{ capabilities?.engine || 'java' }} · {{ capabilities?.runtimeApiVersion || 'runtime-api/v1' }}</div>
    </article>
    <article class="console-card metric-card">
      <span class="metric-line cyan" />
      <div class="metric-label">管理能力</div>
      <div class="metric-value">{{ loading ? '···' : capabilityCount }}</div>
      <div class="metric-foot">API capabilities</div>
    </article>
    <article class="console-card metric-card">
      <span class="metric-line amber" />
      <div class="metric-label">已注册数据源</div>
      <div class="metric-value">{{ loading ? '···' : datasourceCount ?? 'N/A' }}</div>
      <div class="metric-foot">当前 Registry 可见</div>
    </article>
    <article class="console-card metric-card">
      <span class="metric-line" />
      <div class="metric-label">可见模型</div>
      <div class="metric-value">{{ loading ? '···' : modelCount ?? 'N/A' }}</div>
      <div class="metric-foot">namespace / {{ session.namespace.value }}</div>
    </article>
  </section>

  <section class="overview-details">
    <div class="console-panel">
      <div class="console-panel-head">
        <span class="console-panel-title">能力矩阵</span>
        <span class="console-panel-kicker">SERVER DECLARED</span>
      </div>
      <div class="console-panel-body capability-grid">
        <div
          v-for="(status, name) in capabilities?.capabilities"
          :key="name"
          class="capability-item"
        >
          <span>{{ name }}</span>
          <strong>{{ status }}</strong>
        </div>
        <div v-if="!loading && !capabilityCount" class="empty-state">
          <div><strong>暂无能力声明</strong>检查 Runtime API 是否启用。</div>
        </div>
      </div>
    </div>

    <aside class="console-panel">
      <div class="console-panel-head">
        <span class="console-panel-title">管理面防护</span>
        <span class="console-panel-kicker">AUTH SCOPE</span>
      </div>
      <div class="console-panel-body guard-list">
        <div class="guard-item"><span class="status-chip">Token 有效</span><strong>VALID</strong></div>
        <div class="guard-item"><span>API scope</span><strong>{{ session.access.value?.authScope }}</strong></div>
        <div class="guard-item"><span>Security mode</span><strong>{{ capabilities?.securityMode || 'auth-code' }}</strong></div>
        <div class="guard-item"><span>Schema</span><strong>{{ capabilities?.schemaVersion || 'unknown' }}</strong></div>
        <div v-for="warning in capabilities?.warnings" :key="warning" class="notice">{{ warning }}</div>
      </div>
    </aside>
  </section>
</template>

<style scoped>
.overview-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  position: relative;
  min-height: 160px;
  overflow: hidden;
  padding: 21px;
}

.metric-card::after {
  position: absolute;
  right: -40px;
  bottom: -50px;
  width: 116px;
  height: 116px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 50%;
  box-shadow: 0 0 0 18px rgba(255, 255, 255, 0.015), 0 0 0 36px rgba(255, 255, 255, 0.008);
  content: "";
}

.metric-line {
  position: absolute;
  top: 0;
  left: 20px;
  width: 38px;
  height: 2px;
  background: var(--console-lime);
  box-shadow: 0 0 14px var(--console-lime);
}

.metric-line.cyan {
  background: var(--console-cyan);
}

.metric-line.amber {
  background: var(--console-amber);
}

.metric-label {
  color: var(--console-muted);
  font-size: 12px;
}

.metric-value {
  margin-top: 25px;
  font: 520 35px/1 var(--console-mono);
  letter-spacing: -0.05em;
}

.metric-foot {
  margin-top: 18px;
  color: var(--console-dim);
  font: 11px/1.35 var(--console-mono);
}

.overview-details {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(330px, 0.7fr);
  gap: 14px;
  margin-top: 14px;
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.capability-item,
.guard-item {
  min-height: 46px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 13px;
  border: 1px solid var(--console-line);
  border-radius: 10px;
  color: var(--console-muted);
  font-size: 12px;
}

.capability-item strong,
.guard-item strong {
  color: var(--console-text);
  font: 600 11px/1 var(--console-mono);
}

.guard-list {
  display: grid;
  gap: 11px;
}

@media (max-width: 1180px) {
  .overview-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-details {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .overview-metrics,
  .capability-grid {
    grid-template-columns: 1fr;
  }
}
</style>
