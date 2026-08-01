<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import {
  LatestRequestGate,
  exactByteTitle,
  filterLifecycleObjects,
  formatCapturedAt,
  formatLifecycleBytes,
  lifecycleCapabilitySupported,
  type ArtifactLifecycleInventory,
  type LifecycleFilters,
  type LifecycleHealth,
  type ReferenceClass
} from '@/features/lifecycle/artifactLifecycle'

interface Capabilities {
  capabilities: Record<string, string>
}

type ErrorKind = 'AUTH' | 'TRANSPORT' | ''

const inventory = ref<ArtifactLifecycleInventory | null>(null)
const loading = ref(true)
const refreshing = ref(false)
const capabilityChecked = ref(false)
const supported = ref(false)
const errorKind = ref<ErrorKind>('')
const errorMessage = ref('')
const gate = new LatestRequestGate()

const filters = reactive<LifecycleFilters>({
  store: 'ALL',
  referenceClass: 'ALL',
  blocked: 'ALL',
  query: ''
})

const filteredObjects = computed(() => filterLifecycleObjects(
  inventory.value?.objects || [],
  filters
))
const isEmpty = computed(() => Boolean(
  inventory.value && inventory.value.summary.totalObjects === 0
))
const hasActiveFilters = computed(() => filters.store !== 'ALL'
  || filters.referenceClass !== 'ALL'
  || filters.blocked !== 'ALL'
  || Boolean(filters.query.trim()))

const healthCopy: Record<LifecycleHealth, { label: string; detail: string }> = {
  NOT_INITIALIZED: { label: '未初始化', detail: 'Runtime 尚未创建该受管 store。' },
  PARTIAL: { label: '部分可用', detail: '至少一个 store 可读，另一个尚未初始化或不可完整判断。' },
  HEALTHY: { label: '健康', detail: 'ownership、内容与引用图均可验证。' },
  BLOCKED: { label: '已阻断', detail: '存在必须保留并由操作者调查的对象或引用缺口。' }
}

const referenceCopy: Record<ReferenceClass, string> = {
  MUST_RETAIN: '必须保留',
  PROVABLY_UNREACHABLE_CANDIDATE: '不可达候选',
  UNKNOWN_PRESERVE: '未知 · 保留'
}

async function loadLifecycle(): Promise<void> {
  const request = gate.begin()
  const hasSnapshot = Boolean(inventory.value)
  loading.value = !hasSnapshot
  refreshing.value = hasSnapshot
  errorKind.value = ''
  errorMessage.value = ''
  try {
    const capability = await runtimeApi.get<Capabilities>('capabilities')
    if (!gate.isLatest(request)) return
    capabilityChecked.value = true
    supported.value = lifecycleCapabilitySupported(capability.capabilities)
    if (!supported.value) {
      inventory.value = null
      return
    }
    const next = await runtimeApi.get<ArtifactLifecycleInventory>(
      'authoring/artifacts/lifecycle'
    )
    if (!gate.isLatest(request)) return
    inventory.value = next
  } catch (error) {
    if (!gate.isLatest(request)) return
    const runtimeError = error instanceof RuntimeRequestError ? error : null
    errorKind.value = runtimeError?.status === 401
      || runtimeError?.code === 'RUNTIME_AUTH_REQUIRED'
      ? 'AUTH'
      : 'TRANSPORT'
    errorMessage.value = runtimeError?.message || '无法读取制品生命周期清单。'
  } finally {
    if (gate.isLatest(request)) {
      loading.value = false
      refreshing.value = false
    }
  }
}

function resetFilters(): void {
  filters.store = 'ALL'
  filters.referenceClass = 'ALL'
  filters.blocked = 'ALL'
  filters.query = ''
}

onMounted(loadLifecycle)
</script>

<template>
  <PageHeader
    eyebrow="Runtime-global evidence ledger"
    title="制品生命周期"
    description="读取单个 Runtime 的 workspace、published artifact 与 live registry 证据。此视图不受顶部 Namespace 切换影响，也不执行任何清理或修复。"
  >
    <template #actions>
      <span class="global-scope-badge">GLOBAL / READ ONLY</span>
      <button
        class="console-button ghost"
        type="button"
        :disabled="loading || refreshing"
        @click="loadLifecycle"
      >
        {{ refreshing ? '读取中…' : '刷新证据' }}
      </button>
    </template>
  </PageHeader>

  <section class="lifecycle-safety" aria-label="制品生命周期安全边界">
    <strong>候选不是删除授权</strong>
    <p>不可达候选只表示当前引用图未发现引用。unknown、blocked 与所有最终 evidence 必须保留；本页面没有 cleanup、repair 或路径操作。</p>
  </section>

  <div v-if="loading" class="lifecycle-loading" role="status" aria-live="polite">
    <span class="loading-scan" aria-hidden="true" />
    <strong>正在捕获 Runtime-global evidence…</strong>
    <small>先核验 capability，再读取只读 lifecycle inventory。</small>
  </div>

  <section
    v-else-if="capabilityChecked && !supported"
    class="lifecycle-state-panel"
    aria-labelledby="unsupported-title"
  >
    <span class="state-code">CAPABILITY / UNSUPPORTED</span>
    <h2 id="unsupported-title">当前 Runtime 不支持生命周期清单</h2>
    <p>需要 capability <code>authoring.artifacts.lifecycleInventory=supported</code>。请升级 Runtime；Console 不会尝试扫描浏览器或服务器目录。</p>
  </section>

  <section
    v-else-if="errorKind === 'AUTH' && !inventory"
    class="lifecycle-state-panel blocked"
    role="alert"
  >
    <span class="state-code">AUTH / EXPIRED</span>
    <h2>管理凭据已失效</h2>
    <p>{{ errorMessage }} 请重新连接 Runtime；页面未缓存 lifecycle payload。</p>
  </section>

  <section
    v-else-if="errorKind === 'TRANSPORT' && !inventory"
    class="lifecycle-state-panel blocked"
    role="alert"
  >
    <span class="state-code">TRANSPORT / UNAVAILABLE</span>
    <h2>无法读取生命周期证据</h2>
    <p>{{ errorMessage }} 检查 Runtime 服务与网络后重试。</p>
    <button class="console-button" type="button" @click="loadLifecycle">重新读取</button>
  </section>

  <template v-else-if="inventory">
    <div v-if="errorMessage" class="lifecycle-stale-notice" role="alert">
      <strong>刷新失败，保留上一次快照</strong>
      <span>{{ errorMessage }}</span>
    </div>

    <section class="lifecycle-command-strip" aria-label="生命周期快照状态">
      <div>
        <span>OVERALL HEALTH</span>
        <strong :class="`health-${inventory.health.toLowerCase()}`">
          {{ healthCopy[inventory.health]?.label || inventory.health }}
        </strong>
      </div>
      <div>
        <span>CAPTURED AT</span>
        <strong>{{ formatCapturedAt(inventory.capturedAt) }}</strong>
      </div>
      <p>{{ healthCopy[inventory.health]?.detail }}</p>
    </section>

    <section class="lifecycle-metrics" aria-label="生命周期汇总">
      <article>
        <span>对象总数</span>
        <strong>{{ inventory.summary.totalObjects }}</strong>
        <small :title="exactByteTitle(inventory.summary.totalBytes)">
          {{ formatLifecycleBytes(inventory.summary.totalBytes) }} total
        </small>
      </article>
      <article>
        <span>必须保留</span>
        <strong>{{ inventory.summary.mustRetain }}</strong>
        <small>MUST_RETAIN</small>
      </article>
      <article class="candidate-metric">
        <span>不可达候选</span>
        <strong>{{ inventory.summary.provablyUnreachableCandidates }}</strong>
        <small>不是删除授权</small>
      </article>
      <article>
        <span>未知 · 保留</span>
        <strong>{{ inventory.summary.unknownPreserve }}</strong>
        <small>UNKNOWN_PRESERVE</small>
      </article>
      <article class="blocked-metric">
        <span>阻断对象</span>
        <strong>{{ inventory.summary.blockedObjects }}</strong>
        <small>BLOCKED EVIDENCE</small>
      </article>
    </section>

    <section class="root-grid" aria-label="受管 store 健康状态">
      <article v-for="root in inventory.roots" :key="root.store" class="root-card">
        <header>
          <div>
            <span>MANAGED ROOT</span>
            <h2>{{ root.store }}</h2>
          </div>
          <strong :class="`health-${root.health.toLowerCase()}`">
            {{ healthCopy[root.health]?.label || root.health }}
          </strong>
        </header>
        <dl>
          <div><dt>OBJECTS</dt><dd>{{ root.objectCount }}</dd></div>
          <div>
            <dt>BYTES</dt>
            <dd :title="exactByteTitle(root.bytes)">{{ formatLifecycleBytes(root.bytes) }}</dd>
          </div>
        </dl>
        <details v-if="root.blockedReasons.length">
          <summary>{{ root.blockedReasons.length }} 条阻断原因</summary>
          <ul><li v-for="reason in root.blockedReasons" :key="reason">{{ reason }}</li></ul>
        </details>
        <p v-else>未报告 root-level blocked reason。</p>
      </article>
    </section>

    <section v-if="inventory.blockedReasons.length" class="blocked-register">
      <details>
        <summary>全局阻断寄存器 · {{ inventory.blockedReasons.length }}</summary>
        <ul><li v-for="reason in inventory.blockedReasons" :key="reason">{{ reason }}</li></ul>
      </details>
    </section>

    <section class="ledger-panel" aria-labelledby="ledger-title">
      <header class="ledger-heading">
        <div>
          <span>EVIDENCE OBJECT REGISTER</span>
          <h2 id="ledger-title">对象证据账本</h2>
          <p>保持后端 deterministic 顺序；筛选只改变当前视图，不改变 server facts。</p>
        </div>
        <strong>{{ filteredObjects.length }} / {{ inventory.objects.length }}</strong>
      </header>

      <div class="ledger-filters" role="search" aria-label="筛选生命周期对象">
        <label>
          <span>STORE</span>
          <select v-model="filters.store" class="console-select" aria-label="按 store 筛选">
            <option value="ALL">全部 store</option>
            <option value="WORKSPACE">WORKSPACE</option>
            <option value="PUBLISHED">PUBLISHED</option>
            <option value="LIVE_REGISTRY">LIVE_REGISTRY</option>
          </select>
        </label>
        <label>
          <span>REFERENCE CLASS</span>
          <select v-model="filters.referenceClass" class="console-select" aria-label="按引用分类筛选">
            <option value="ALL">全部分类</option>
            <option value="MUST_RETAIN">必须保留</option>
            <option value="PROVABLY_UNREACHABLE_CANDIDATE">不可达候选</option>
            <option value="UNKNOWN_PRESERVE">未知 · 保留</option>
          </select>
        </label>
        <label>
          <span>BLOCKED</span>
          <select v-model="filters.blocked" class="console-select" aria-label="按阻断状态筛选">
            <option value="ALL">全部状态</option>
            <option value="BLOCKED">仅阻断</option>
            <option value="CLEAR">仅无阻断</option>
          </select>
        </label>
        <label class="ledger-search">
          <span>SEARCH EVIDENCE</span>
          <input
            v-model="filters.query"
            class="console-input"
            type="search"
            aria-label="搜索生命周期对象"
            placeholder="identity / type / reason / reference"
          >
        </label>
        <button
          class="console-button compact"
          type="button"
          :disabled="!hasActiveFilters"
          @click="resetFilters"
        >
          清除筛选
        </button>
      </div>

      <div v-if="isEmpty" class="ledger-empty">
        <strong>清单为空</strong>
        <p>Runtime 已返回有效快照，但当前没有 lifecycle objects。</p>
      </div>
      <div v-else-if="!filteredObjects.length" class="ledger-empty">
        <strong>没有匹配对象</strong>
        <p>调整 store、引用分类、blocked 状态或搜索条件。</p>
      </div>
      <div v-else class="ledger-table-wrap">
        <table class="ledger-table">
          <thead>
            <tr>
              <th>STORE / TYPE</th>
              <th>IDENTITY</th>
              <th>STATUS</th>
              <th>SIZE</th>
              <th>CLASS / EVIDENCE</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="object in filteredObjects" :key="`${object.store}:${object.type}:${object.identity}`">
              <td data-label="STORE / TYPE">
                <strong>{{ object.store }}</strong>
                <small>{{ object.type }}</small>
              </td>
              <td data-label="IDENTITY">
                <code :title="object.identity">{{ object.identity }}</code>
              </td>
              <td data-label="STATUS"><span class="object-status">{{ object.status }}</span></td>
              <td data-label="SIZE" :title="exactByteTitle(object.bytes)">
                {{ formatLifecycleBytes(object.bytes) }}
              </td>
              <td data-label="CLASS / EVIDENCE">
                <span class="reference-class" :class="`ref-${object.referenceClass.toLowerCase()}`">
                  {{ referenceCopy[object.referenceClass] || object.referenceClass }}
                </span>
                <details v-if="object.references.length || object.blockedReason">
                  <summary>查看证据</summary>
                  <div v-if="object.blockedReason" class="blocked-reason">
                    <span>BLOCKED REASON</span>
                    <code>{{ object.blockedReason }}</code>
                  </div>
                  <ul v-if="object.references.length">
                    <li v-for="reference in object.references" :key="reference">
                      <code>{{ reference }}</code>
                    </li>
                  </ul>
                  <p v-else-if="object.blockedReason">无可展示引用。</p>
                </details>
                <small v-else>无引用 / 无阻断</small>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </template>
</template>

<style scoped>
.global-scope-badge {
  min-height: 42px;
  display: inline-flex;
  align-items: center;
  padding: 0 13px;
  border: 1px solid var(--console-line-strong);
  font: 700 10px/1 var(--console-mono);
  letter-spacing: 0.12em;
}

.lifecycle-safety {
  display: grid;
  grid-template-columns: minmax(180px, 0.28fr) minmax(0, 1fr);
  border: 1px solid var(--console-line-strong);
  background: repeating-linear-gradient(135deg, transparent 0 10px, var(--console-hatch-line) 10px 11px);
}

.lifecycle-safety strong,
.lifecycle-safety p { margin: 0; padding: 15px 18px; }
.lifecycle-safety strong { border-right: 1px solid var(--console-line-strong); font: 700 13px/1.4 var(--console-mono); }
.lifecycle-safety p { color: var(--console-muted); font-size: 13px; }

.lifecycle-loading,
.lifecycle-state-panel {
  min-height: 310px;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 12px;
  margin-top: 12px;
  padding: 36px;
  border: 1px solid var(--console-line-strong);
  background: var(--console-panel);
  text-align: center;
}

.loading-scan { width: 82px; height: 20px; border: 1px solid var(--console-paper); background: linear-gradient(90deg, var(--console-paper) 0 32%, transparent 32% 38%, var(--console-paper) 38% 70%, transparent 70%); animation: scan 900ms steps(3) infinite; }
@keyframes scan { 50% { opacity: 0.28; transform: translateX(4px); } }
.lifecycle-loading small, .lifecycle-state-panel p { max-width: 640px; color: var(--console-muted); }
.lifecycle-state-panel h2 { margin: 0; font-size: 23px; }
.state-code { font: 700 10px/1 var(--console-mono); letter-spacing: 0.16em; }
.lifecycle-state-panel code { padding: 2px 4px; background: var(--console-panel-2); font: 12px/1.5 var(--console-mono); }

.lifecycle-stale-notice { display: flex; gap: 14px; margin-top: 12px; padding: 12px 15px; border: 1px solid var(--console-line-strong); background: var(--console-panel-2); }
.lifecycle-stale-notice span { color: var(--console-muted); }

.lifecycle-command-strip { display: grid; grid-template-columns: 190px 280px minmax(0, 1fr); align-items: stretch; margin-top: 12px; border: 1px solid var(--console-line-strong); background: var(--console-panel); }
.lifecycle-command-strip > div, .lifecycle-command-strip > p { margin: 0; padding: 14px 16px; border-right: 1px solid var(--console-line); }
.lifecycle-command-strip span { display: block; margin-bottom: 7px; color: var(--console-dim); font: 9px/1 var(--console-mono); letter-spacing: 0.13em; }
.lifecycle-command-strip strong { font: 700 13px/1.2 var(--console-mono); }
.lifecycle-command-strip p { border: 0; color: var(--console-muted); font-size: 13px; }

.health-healthy { color: var(--console-text); }
.health-blocked { padding: 3px 6px; background: var(--console-paper); color: var(--console-inverse); }
.health-partial, .health-not_initialized { text-decoration: underline; text-decoration-style: double; text-underline-offset: 4px; }

.lifecycle-metrics { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); margin-top: 12px; border: 1px solid var(--console-line-strong); background: var(--console-panel); }
.lifecycle-metrics article { min-height: 122px; padding: 17px; border-right: 1px solid var(--console-line); }
.lifecycle-metrics article:last-child { border: 0; }
.lifecycle-metrics span, .lifecycle-metrics small { display: block; color: var(--console-muted); font-size: 12px; }
.lifecycle-metrics strong { display: block; margin: 17px 0 13px; font: 600 28px/1 var(--console-mono); }
.lifecycle-metrics small { font: 9px/1.2 var(--console-mono); letter-spacing: 0.07em; }
.candidate-metric { background: repeating-linear-gradient(135deg, transparent 0 8px, var(--console-hatch-line) 8px 9px); }
.blocked-metric strong { display: inline-block; min-width: 42px; padding: 4px 7px; background: var(--console-paper); color: var(--console-inverse); }

.root-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 12px; }
.root-card { border: 1px solid var(--console-line-strong); background: var(--console-panel); }
.root-card header { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 17px 18px; border-bottom: 1px solid var(--console-line); }
.root-card header span { color: var(--console-dim); font: 9px/1 var(--console-mono); letter-spacing: 0.13em; }
.root-card h2 { margin: 7px 0 0; font: 700 18px/1 var(--console-mono); }
.root-card dl { display: grid; grid-template-columns: repeat(2, 1fr); margin: 0; }
.root-card dl div { padding: 15px 18px; border-right: 1px solid var(--console-line); }
.root-card dl div:last-child { border: 0; }
.root-card dt { color: var(--console-dim); font: 9px/1 var(--console-mono); letter-spacing: 0.12em; }
.root-card dd { margin: 9px 0 0; font: 650 16px/1 var(--console-mono); }
.root-card details, .root-card > p { margin: 0; padding: 13px 18px; border-top: 1px solid var(--console-line); color: var(--console-muted); font-size: 12px; }
.root-card summary, .blocked-register summary, .ledger-table summary { cursor: pointer; font-weight: 700; }
.root-card ul, .blocked-register ul { margin: 12px 0 0; padding-left: 18px; font: 11px/1.7 var(--console-mono); }

.blocked-register { margin-top: 12px; border: 1px solid var(--console-line-strong); background: var(--console-panel-2); }
.blocked-register details { padding: 14px 17px; }

.ledger-panel { margin-top: 12px; border: 1px solid var(--console-line-strong); background: var(--console-panel); }
.ledger-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 20px; border-bottom: 1px solid var(--console-line-strong); }
.ledger-heading span { color: var(--console-dim); font: 9px/1 var(--console-mono); letter-spacing: 0.14em; }
.ledger-heading h2 { margin: 8px 0 4px; font-size: 21px; }
.ledger-heading p { margin: 0; color: var(--console-muted); font-size: 12px; }
.ledger-heading > strong { font: 700 16px/1 var(--console-mono); }
.ledger-filters { display: grid; grid-template-columns: 170px 220px 160px minmax(220px, 1fr) auto; align-items: end; gap: 10px; padding: 14px; border-bottom: 1px solid var(--console-line); background: var(--console-panel-2); }
.ledger-filters label { display: grid; gap: 7px; }
.ledger-filters label > span { color: var(--console-dim); font: 9px/1 var(--console-mono); letter-spacing: 0.11em; }
.ledger-filters .console-select, .ledger-filters .console-input { min-height: 38px; font-size: 12px; }
.ledger-empty { padding: 70px 20px; text-align: center; }
.ledger-empty p { color: var(--console-muted); }
.ledger-table-wrap { overflow-x: auto; }
.ledger-table { width: 100%; min-width: 980px; border-collapse: collapse; text-align: left; }
.ledger-table th { padding: 11px 13px; border-bottom: 1px solid var(--console-line-strong); background: var(--console-panel-2); color: var(--console-dim); font: 9px/1 var(--console-mono); letter-spacing: 0.1em; }
.ledger-table td { padding: 13px; border-right: 1px solid var(--console-line); border-bottom: 1px solid var(--console-line); vertical-align: top; font-size: 12px; }
.ledger-table td:last-child { border-right: 0; }
.ledger-table tr:last-child td { border-bottom: 0; }
.ledger-table td > strong, .ledger-table td > small { display: block; }
.ledger-table td > small { margin-top: 5px; color: var(--console-dim); font: 9px/1.35 var(--console-mono); }
.ledger-table code { display: block; max-width: 370px; overflow-wrap: anywhere; color: var(--console-text); font: 11px/1.55 var(--console-mono); }
.object-status, .reference-class { display: inline-block; padding: 4px 6px; border: 1px solid var(--console-line-strong); font: 9px/1.2 var(--console-mono); }
.ref-provably_unreachable_candidate { border-style: double; border-width: 3px; }
.ref-unknown_preserve { background: var(--console-panel-2); text-decoration: underline; text-underline-offset: 3px; }
.ledger-table details { margin-top: 10px; }
.ledger-table details ul { margin: 9px 0 0; padding-left: 18px; }
.blocked-reason { margin-top: 10px; padding: 9px; border-left: 3px solid var(--console-paper); background: var(--console-panel-2); }
.blocked-reason span { display: block; margin-bottom: 5px; color: var(--console-dim); font: 8px/1 var(--console-mono); letter-spacing: 0.1em; }

@media (max-width: 1180px) {
  .lifecycle-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .lifecycle-metrics article { border-bottom: 1px solid var(--console-line); }
  .ledger-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .ledger-search { grid-column: 1 / -1; }
}

@media (max-width: 720px) {
  .lifecycle-safety { grid-template-columns: 1fr; }
  .lifecycle-safety strong { border-right: 0; border-bottom: 1px solid var(--console-line-strong); }
  .lifecycle-command-strip { grid-template-columns: 1fr 1fr; }
  .lifecycle-command-strip > p { grid-column: 1 / -1; border-top: 1px solid var(--console-line); }
  .lifecycle-metrics, .root-grid { grid-template-columns: 1fr; }
  .lifecycle-metrics article { min-height: 98px; border-right: 0; }
  .root-grid { gap: 9px; }
  .ledger-heading { align-items: flex-start; }
  .ledger-filters { grid-template-columns: 1fr; }
  .ledger-search { grid-column: auto; }
  .ledger-table { min-width: 0; }
  .ledger-table thead { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  .ledger-table, .ledger-table tbody, .ledger-table tr, .ledger-table td { display: block; width: 100%; }
  .ledger-table tr { border-bottom: 1px solid var(--console-line-strong); }
  .ledger-table tr:last-child { border: 0; }
  .ledger-table td { display: grid; grid-template-columns: 108px minmax(0, 1fr); gap: 12px; border-right: 0; }
  .ledger-table td::before { content: attr(data-label); color: var(--console-dim); font: 8px/1.4 var(--console-mono); letter-spacing: 0.08em; }
  .ledger-table code { max-width: 100%; }
}
</style>
