<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElDrawer, ElMessage } from 'element-plus'
import { useRuntimeSession } from '@/stores/session'
import { useContextRail } from '@/stores/contextRail'
import { useNamespaceWorkspaceData } from '@/features/namespace/useNamespaceWorkspaceData'

const route = useRoute()
const router = useRouter()
const session = useRuntimeSession()
const contextRail = useContextRail()
const namespaceData = useNamespaceWorkspaceData()
const mobileNavigationOpen = ref(false)
const mobileContextOpen = ref(false)
const namespaceLabel = computed(() => session.namespace.value || '空 Namespace')

const navigation = [
  { route: 'overview', matches: ['overview'], label: '运行概览', short: '概览', code: '01' },
  { route: 'datasources', matches: ['datasources'], label: '数据源', short: '数据源', code: '02' },
  { route: 'namespaces', matches: ['namespaces'], label: '数据与模型空间', short: '空间', code: '03' },
  { route: 'query', matches: ['query'], label: '查询工作台', short: '查询', code: '04' },
  { route: 'tables', matches: ['tables'], label: 'Tables / SQL', short: 'SQL', code: '05' },
  { route: 'compose', matches: ['compose', 'fsscript'], label: '执行工具', short: '执行工具', code: '06' },
  { route: 'artifact-lifecycle', matches: ['artifact-lifecycle'], label: '制品生命周期', short: '生命周期', code: '07' }
] as const

const namespaceOptions = computed(() => {
  const names = new Set(namespaceData.discoveredNamespaces.value)
  names.add(session.namespace.value)
  return [...names]
})

const currentLabel = computed(() =>
  navigation.find(item => item.matches.includes(String(route.name) as never))?.label || 'Runtime Console'
)

const fallbackContexts: Record<string, {
  eyebrow: string
  title: string
  description: string
}> = {
  overview: {
    eyebrow: 'Runtime status',
    title: 'Console Index',
    description: '从顶部切换工作台；左侧会随页面显示当前资源。'
  },
  compose: {
    eyebrow: 'Composition',
    title: 'Compose Workspace',
    description: '用于受限 Compose / CTE 的校验、预览与执行。'
  },
  fsscript: {
    eyebrow: 'Advanced runner',
    title: 'FSScript Workspace',
    description: '运行独立 FSScript，并检查结构化输出与诊断。'
  },
  'artifact-lifecycle': {
    eyebrow: 'Runtime-global',
    title: 'Artifact Evidence',
    description: '跨 workspace、published store 与 live registry 的只读证据；不属于当前 Namespace。'
  }
}

const currentContext = computed(() => {
  const context = contextRail.state.route === route.name
    ? contextRail.state
    : fallbackContexts[String(route.name)] || {
    eyebrow: 'Workspace',
    title: currentLabel.value,
    description: '当前页面的 Runtime 操作上下文。'
  }
  return {
    route: String(route.name || ''),
    loading: false,
    filterable: false,
    emptyText: '此工作台没有固定资源列表。',
    sections: [],
    ...context,
    description: route.name === 'artifact-lifecycle'
      ? context.description
      : `${context.description} 当前空间：${namespaceLabel.value}。`,
  }
})

const currentSections = computed(() =>
  contextRail.state.route === route.name
    ? contextRail.filteredSections.value
    : currentContext.value.sections
)

const railAvailable = computed(() =>
  currentSections.value.some(section => section.items.length)
)

function navigate(name: string): void {
  mobileNavigationOpen.value = false
  void router.push({ name })
}

function navigationActive(matches: readonly string[]): boolean {
  return matches.includes(String(route.name))
}

function selectContext(action?: () => void): void {
  mobileContextOpen.value = false
  action?.()
}

function logout(): void {
  session.logout()
}

function changeNamespace(event: Event): void {
  const value = (event.target as HTMLInputElement).value.trim()
  if (value === session.namespace.value) return
  session.setNamespace(value)
  if (route.name === 'namespaces') {
    void router.push({
      name: 'namespaces',
      params: { workspace: route.params.workspace },
      query: { ns: value }
    })
  }
  ElMessage.success(value
    ? `当前数据与模型空间已切换为 ${value}。`
    : '当前请求已切换为空 Namespace。')
}
</script>

<template>
  <a class="skip-link" href="#console-main">跳到主内容</a>
  <div class="console-shell">
    <header class="console-header">
      <div class="console-commandbar">
        <button
          class="console-mobile-menu"
          type="button"
          aria-label="打开主导航"
          @click="mobileNavigationOpen = true"
        >
          <span /><span /><span />
        </button>

        <div class="console-brand">
          <div class="console-brand-mark" aria-hidden="true"><span /><span /></div>
          <div>
            <strong>Foggy Runtime</strong>
            <small>CONSOLE / 9.5.2</small>
          </div>
        </div>

        <div class="console-location">
          RUNTIME / <strong>{{ currentLabel }}</strong>
        </div>
        <div class="console-topbar-spacer" />
        <label class="namespace-control">
          <span>当前数据与模型空间</span>
          <input
            :value="session.namespace.value"
            list="namespace-options"
            aria-label="当前数据与模型空间"
            autocomplete="off"
            placeholder="空 Namespace"
            @change="changeNamespace"
          >
          <datalist id="namespace-options">
            <option v-for="namespace in namespaceOptions" :key="namespace || 'empty'" :value="namespace">
              {{ namespace || '空 Namespace' }}
            </option>
          </datalist>
        </label>
        <span class="status-chip console-scope-chip">management-all</span>
        <button class="console-icon-button" type="button" aria-label="退出 Console" @click="logout">
          ↗
        </button>
      </div>

      <nav class="console-top-navigation" aria-label="Runtime Console 主导航">
        <button
          v-for="item in navigation"
          :key="item.route"
          type="button"
          class="console-top-nav-item"
          :class="{ active: navigationActive(item.matches) }"
          :aria-current="navigationActive(item.matches) ? 'page' : undefined"
          @click="navigate(item.route)"
        >
          <span class="console-nav-code">{{ item.code }}</span>
          <span>{{ item.label }}</span>
        </button>
      </nav>
    </header>

    <div class="console-workspace">
      <aside class="console-context-rail" aria-label="当前页面资源导航">
        <div class="context-rail-heading">
          <span>{{ currentContext.eyebrow }}</span>
          <h2>{{ currentContext.title }}</h2>
          <p>{{ currentContext.description }}</p>
        </div>

        <label v-if="currentContext.filterable" class="context-rail-search">
          <span class="visually-hidden">筛选当前资源</span>
          <input
            v-model="contextRail.query.value"
            type="search"
            placeholder="筛选资源…"
            autocomplete="off"
          >
          <kbd>/</kbd>
        </label>

        <div class="context-rail-scroll">
          <div v-if="currentContext.loading" class="context-rail-state">正在读取 Runtime…</div>
          <template v-else-if="currentSections.length">
            <section
              v-for="section in currentSections"
              :key="section.id"
              class="context-section"
            >
              <div v-if="section.label" class="context-section-label">{{ section.label }}</div>
              <button
                v-for="item in section.items"
                :key="item.id"
                type="button"
                class="context-item"
                :class="{ active: item.active }"
                :aria-pressed="Boolean(item.active)"
                @click="selectContext(item.action)"
              >
                <span class="context-item-indicator" aria-hidden="true" />
                <span class="context-item-copy">
                  <strong>{{ item.label }}</strong>
                  <small v-if="item.meta">{{ item.meta }}</small>
                </span>
                <span v-if="item.badge" class="context-item-badge">{{ item.badge }}</span>
              </button>
            </section>
          </template>
          <div v-else class="context-rail-state">{{ currentContext.emptyText }}</div>
        </div>

        <div class="console-runtime-card">
          <span class="status-chip">CONNECTED</span>
          <div>
            <strong>{{ session.namespace.value || '空 Namespace' }}</strong>
            <small>{{ session.access.value?.runtimeApiVersion || 'runtime-api/v1' }}</small>
          </div>
        </div>
      </aside>

      <main id="console-main" class="console-content" tabindex="-1">
        <button
          class="console-mobile-context"
          type="button"
          :disabled="!railAvailable"
          @click="mobileContextOpen = true"
        >
          <span>资源列表</span>
          <strong>{{ currentContext.title }}</strong>
          <span aria-hidden="true">→</span>
        </button>
        <RouterView />
      </main>
    </div>
  </div>

  <ElDrawer
    v-model="mobileNavigationOpen"
    title="Runtime operations"
    direction="ltr"
    size="min(350px, 88vw)"
    class="console-mobile-drawer"
  >
    <nav class="console-navigation" aria-label="移动端 Runtime Console 主导航">
      <button
        v-for="item in navigation"
        :key="item.route"
        type="button"
        class="console-nav-item"
        :class="{ active: navigationActive(item.matches) }"
        :aria-current="navigationActive(item.matches) ? 'page' : undefined"
        @click="navigate(item.route)"
      >
        <span class="console-nav-code">{{ item.code }}</span>
        <span>{{ item.label }}</span>
      </button>
    </nav>
  </ElDrawer>

  <ElDrawer
    v-model="mobileContextOpen"
    :title="currentContext.title"
    direction="ltr"
    size="min(350px, 88vw)"
    class="console-mobile-drawer"
  >
    <div class="context-mobile-copy">{{ currentContext.description }}</div>
    <section
      v-for="section in currentSections"
      :key="section.id"
      class="context-section"
    >
      <div v-if="section.label" class="context-section-label">{{ section.label }}</div>
      <button
        v-for="item in section.items"
        :key="item.id"
        type="button"
        class="context-item"
        :class="{ active: item.active }"
        :aria-pressed="Boolean(item.active)"
        @click="selectContext(item.action)"
      >
        <span class="context-item-indicator" aria-hidden="true" />
        <span class="context-item-copy">
          <strong>{{ item.label }}</strong>
          <small v-if="item.meta">{{ item.meta }}</small>
        </span>
        <span v-if="item.badge" class="context-item-badge">{{ item.badge }}</span>
      </button>
    </section>
  </ElDrawer>
</template>

<style scoped>
.console-shell {
  min-height: 100vh;
}

.console-header {
  position: sticky;
  top: 0;
  z-index: 30;
  border-bottom: 1px solid var(--console-line-strong);
  background: var(--console-bg);
}

.console-commandbar {
  min-height: 64px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 24px;
  border-bottom: 1px solid var(--console-line);
}

.console-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.console-brand strong,
.console-brand small {
  display: block;
}

.console-brand strong {
  font-size: 14px;
  letter-spacing: 0.035em;
  text-transform: uppercase;
}

.console-brand small {
  margin-top: 4px;
  color: var(--console-dim);
  font: 9px/1 var(--console-mono);
  letter-spacing: 0.16em;
}

.console-brand-mark {
  position: relative;
  width: 38px;
  height: 38px;
  flex: none;
  border: 1px solid var(--console-paper);
  border-radius: 0;
  background:
    linear-gradient(90deg, transparent 48%, var(--console-line-strong) 48% 52%, transparent 52%),
    linear-gradient(transparent 48%, var(--console-line-strong) 48% 52%, transparent 52%);
}

.console-brand-mark::before,
.console-brand-mark::after {
  position: absolute;
  content: "";
}

.console-brand-mark::before {
  inset: 5px;
  border: 1px solid var(--console-line-strong);
}

.console-brand-mark::after {
  top: -4px;
  left: 50%;
  width: 1px;
  height: 46px;
  background: var(--console-paper);
}

.console-brand-mark span {
  display: none;
}

.console-location {
  margin-left: 10px;
  padding-left: 18px;
  border-left: 1px solid var(--console-line);
  color: var(--console-dim);
  font: 10px/1 var(--console-mono);
  letter-spacing: 0.09em;
}

.console-location strong {
  color: var(--console-text);
}

.console-topbar-spacer {
  flex: 1;
}

.console-top-navigation {
  min-height: 51px;
  display: grid;
  grid-template-columns: repeat(7, minmax(108px, 1fr));
  padding: 0 24px;
  counter-reset: top-nav;
}

.console-top-nav-item {
  position: relative;
  min-width: 0;
  min-height: 50px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  padding: 0 14px;
  border: 0;
  border-right: 1px solid var(--console-line);
  border-radius: 0;
  background: transparent;
  color: var(--console-muted);
  cursor: pointer;
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
  transition:
    color var(--console-transition),
    background var(--console-transition);
}

.console-top-nav-item:first-child {
  border-left: 1px solid var(--console-line);
}

.console-top-nav-item:hover {
  background:
    repeating-linear-gradient(
      135deg,
      transparent 0 6px,
      var(--console-hatch-line) 6px 7px
    );
  color: var(--console-text);
}

.console-top-nav-item.active {
  background: var(--console-paper);
  color: var(--console-inverse);
  box-shadow: none;
}

.console-top-nav-item.active::after {
  position: absolute;
  right: 6px;
  bottom: 5px;
  width: 7px;
  height: 7px;
  border-right: 1px solid currentColor;
  border-bottom: 1px solid currentColor;
  content: "";
}

.console-nav-code {
  color: inherit;
  font: 9px/1 var(--console-mono);
  opacity: 0.72;
}

.console-workspace {
  display: grid;
  grid-template-columns: 284px minmax(0, 1fr);
}

.console-context-rail {
  position: sticky;
  top: 116px;
  height: calc(100vh - 116px);
  min-width: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--console-line-strong);
  background: var(--console-panel-2);
}

.context-rail-heading {
  position: relative;
  padding: 25px 22px 19px;
  border-bottom: 1px solid var(--console-line);
}

.context-rail-heading::after {
  position: absolute;
  right: 16px;
  bottom: -4px;
  width: 24px;
  height: 7px;
  border: 1px solid var(--console-line);
  background: var(--console-panel-2);
  content: "";
}

.context-rail-heading > span {
  color: var(--console-muted);
  font: 700 10px/1 var(--console-mono);
  letter-spacing: 0.17em;
  text-transform: uppercase;
}

.context-rail-heading h2 {
  margin: 9px 0 0;
  font: 700 19px/1.1 var(--console-sans);
  letter-spacing: -0.02em;
}

.context-rail-heading p,
.context-mobile-copy {
  margin: 8px 0 0;
  color: var(--console-muted);
  font-size: 13px;
  line-height: 1.55;
}

.context-rail-search {
  min-height: 38px;
  display: flex;
  align-items: center;
  margin: 14px 14px 10px;
  padding: 0 10px;
  border: 1px solid var(--console-line-strong);
  border-radius: 0;
  background: var(--console-panel);
}

.context-rail-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  background: transparent;
  color: var(--console-text);
  font-size: 13px;
}

.context-rail-search kbd {
  min-width: 18px;
  padding: 3px;
  border: 1px solid var(--console-line);
  color: var(--console-dim);
  font: 10px/1 var(--console-mono);
  text-align: center;
}

.context-rail-scroll {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 4px 0 16px;
}

.context-section + .context-section {
  margin-top: 18px;
}

.context-section-label {
  margin: 0 15px 7px;
  color: var(--console-dim);
  font: 10px/1 var(--console-mono);
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.context-item {
  position: relative;
  width: 100%;
  min-height: 51px;
  display: grid;
  grid-template-columns: 9px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  padding: 7px 14px;
  border: 0;
  border-top: 1px solid transparent;
  border-bottom: 1px solid transparent;
  border-radius: 0;
  background: transparent;
  color: var(--console-muted);
  cursor: pointer;
  text-align: left;
  transition: var(--console-transition);
}

.context-item:hover {
  border-color: var(--console-line);
  background:
    repeating-linear-gradient(
      135deg,
      transparent 0 7px,
      var(--console-hatch-line) 7px 8px
    );
  color: var(--console-text);
}

.context-item.active {
  border-color: var(--console-paper);
  background: var(--console-paper);
  color: var(--console-inverse);
}

.context-item-indicator {
  width: 7px;
  height: 7px;
  border: 1px solid currentColor;
  border-radius: 0;
  background: transparent;
}

.context-item.active .context-item-indicator {
  background:
    linear-gradient(45deg, transparent 40%, currentColor 41% 59%, transparent 60%),
    linear-gradient(-45deg, transparent 40%, currentColor 41% 59%, transparent 60%);
  box-shadow: none;
}

.context-item-copy {
  min-width: 0;
}

.context-item-copy strong,
.context-item-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.context-item-copy strong {
  font-size: 13px;
  font-weight: 650;
}

.context-item-copy small {
  margin-top: 5px;
  color: inherit;
  font: 10px/1.2 var(--console-mono);
  opacity: 0.68;
}

.context-item-badge {
  padding: 4px 6px;
  border: 1px solid currentColor;
  border-radius: 0;
  color: inherit;
  font: 9px/1 var(--console-mono);
  opacity: 0.72;
  text-transform: uppercase;
}

.context-rail-state {
  padding: 28px 12px;
  color: var(--console-dim);
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
}

.console-runtime-card {
  min-height: 66px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  padding: 10px 12px;
  border-top: 1px solid var(--console-line-strong);
  border-radius: 0;
  background:
    repeating-linear-gradient(
      135deg,
      transparent 0 7px,
      var(--console-hatch-line) 7px 8px
    );
}

.console-runtime-card strong,
.console-runtime-card small {
  display: block;
}

.console-runtime-card strong {
  font: 600 12px/1 var(--console-mono);
}

.console-runtime-card small {
  margin-top: 5px;
  color: var(--console-dim);
  font: 10px/1 var(--console-mono);
}

.namespace-control {
  min-height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 11px;
  border: 1px solid var(--console-line-strong);
  border-radius: 0;
  background: var(--console-panel);
}

.namespace-control span {
  color: var(--console-dim);
  font: 10px/1 var(--console-mono);
  text-transform: uppercase;
}

.namespace-control input {
  width: 116px;
  border: 0;
  background: transparent;
  color: var(--console-text);
  font: 12px/1 var(--console-mono);
}

.console-icon-button,
.console-mobile-menu {
  width: 38px;
  height: 38px;
  flex: none;
  display: grid;
  place-items: center;
  border: 1px solid var(--console-line-strong);
  border-radius: 0;
  background: var(--console-panel);
  color: var(--console-muted);
  cursor: pointer;
}

.console-icon-button:hover,
.console-mobile-menu:hover {
  border-color: var(--console-paper);
  color: var(--console-paper);
}

.console-mobile-menu {
  display: none;
}

.console-mobile-menu span {
  width: 18px;
  height: 1px;
  background: currentColor;
}

.console-content {
  width: 100%;
  min-width: 0;
  min-height: calc(100vh - 116px);
  margin: 0;
  padding: 32px 32px 56px;
}

.console-mobile-context {
  display: none;
}

.console-navigation {
  display: grid;
  gap: 0;
  border-top: 1px solid var(--console-line);
}

.console-nav-item {
  width: 100%;
  min-height: 49px;
  display: grid;
  grid-template-columns: 28px 1fr;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: 0;
  border-bottom: 1px solid var(--console-line);
  border-radius: 0;
  background: transparent;
  color: var(--console-muted);
  cursor: pointer;
  text-align: left;
  font-weight: 650;
}

.console-nav-item:hover {
  background:
    repeating-linear-gradient(
      135deg,
      transparent 0 7px,
      var(--console-hatch-line) 7px 8px
    );
  color: var(--console-text);
}

.console-nav-item.active {
  background: var(--console-paper);
  color: var(--console-inverse);
}

.context-mobile-copy {
  margin: -8px 0 24px;
}

:global(.console-mobile-drawer) {
  border-right: 1px solid var(--console-line-strong);
  background: var(--console-panel);
  color: var(--console-text);
}

:global(.console-mobile-drawer .el-drawer__header) {
  margin-bottom: 0;
  padding: 18px 20px;
  color: var(--console-text);
  font-weight: 680;
}

:global(.console-mobile-drawer .el-drawer__body) {
  padding: 18px 16px 24px;
}

@media (max-width: 1100px) {
  .console-top-navigation {
    display: none;
  }

  .console-mobile-menu {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .console-workspace {
    grid-template-columns: 258px minmax(0, 1fr);
  }

  .console-context-rail {
    top: 64px;
    height: calc(100vh - 64px);
  }
}

@media (max-width: 820px) {
  .console-location,
  .console-scope-chip,
  .namespace-control span {
    display: none;
  }

  .console-workspace {
    display: block;
  }

  .console-context-rail {
    display: none;
  }

  .console-content {
    min-height: calc(100vh - 64px);
    padding: 22px 20px 48px;
  }

  .console-mobile-context {
    width: 100%;
    min-height: 56px;
    display: grid;
    grid-template-columns: auto 1fr auto;
    align-items: center;
    gap: 11px;
    margin-bottom: 22px;
    padding: 0 14px;
    border: 1px solid var(--console-line-strong);
    border-radius: 0;
    background:
      repeating-linear-gradient(
        135deg,
        transparent 0 7px,
        var(--console-hatch-line) 7px 8px
      );
    color: var(--console-muted);
    cursor: pointer;
    text-align: left;
    font-size: 11px;
  }

  .console-mobile-context strong {
    overflow: hidden;
    color: var(--console-text);
    font-size: 13px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .console-mobile-context:disabled {
    cursor: not-allowed;
    opacity: 0.42;
  }
}

@media (max-width: 560px) {
  .console-commandbar {
    gap: 10px;
    padding: 8px 12px;
  }

  .console-brand strong {
    font-size: 13px;
  }

  .console-brand small {
    display: none;
  }

  .namespace-control {
    margin-left: auto;
    padding: 0 8px;
  }

  .namespace-control input {
    width: 72px;
  }

  .console-content {
    padding: 18px 12px 44px;
  }
}
</style>
