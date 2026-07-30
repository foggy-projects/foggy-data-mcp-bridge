<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElDrawer } from 'element-plus'
import { useRuntimeSession } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useRuntimeSession()
const mobileNavigationOpen = ref(false)

const navigation = [
  { route: 'overview', label: '运行概览', short: '概览', code: '01' },
  { route: 'datasources', label: '数据源与命名空间', short: '数据源', code: '02' },
  { route: 'bundles', label: 'Bundle 与资源', short: 'Bundle', code: '03' },
  { route: 'models', label: '语义模型', short: '模型', code: '04' },
  { route: 'query', label: '查询工作台', short: '查询', code: '05' },
  { route: 'tables', label: 'Tables 与 SQL', short: 'SQL', code: '06' },
  { route: 'compose', label: 'Compose', short: 'Compose', code: '07' },
  { route: 'fsscript', label: '高级 FSScript', short: 'FSScript', code: '08' }
] as const

const currentLabel = computed(() =>
  navigation.find(item => item.route === route.name)?.label || 'Runtime Console'
)

function navigate(name: string): void {
  mobileNavigationOpen.value = false
  void router.push({ name })
}

function logout(): void {
  session.logout()
}
</script>

<template>
  <a class="skip-link" href="#console-main">跳到主内容</a>
  <div class="console-shell">
    <aside class="console-sidebar" aria-label="Runtime Console 主导航">
      <div class="console-brand">
        <div class="console-brand-mark" aria-hidden="true"><span /><span /></div>
        <div>
          <strong>Foggy Runtime</strong>
          <small>CONSOLE / 9.5.2</small>
        </div>
      </div>

      <div class="console-nav-label">Runtime operations</div>
      <nav class="console-navigation">
        <button
          v-for="item in navigation"
          :key="item.route"
          type="button"
          class="console-nav-item"
          :class="{ active: route.name === item.route }"
          :aria-current="route.name === item.route ? 'page' : undefined"
          @click="navigate(item.route)"
        >
          <span class="console-nav-code">{{ item.code }}</span>
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="console-runtime-card">
        <span class="status-chip">CONNECTED</span>
        <strong>management-all</strong>
        <small>{{ session.access.value?.runtimeApiVersion || 'runtime-api/v1' }}</small>
      </div>
    </aside>

    <div class="console-main-column">
      <header class="console-topbar">
        <button
          class="console-mobile-menu"
          type="button"
          aria-label="打开主导航"
          @click="mobileNavigationOpen = true"
        >
          <span /><span /><span />
        </button>
        <div class="console-breadcrumb">
          RUNTIME / <strong>{{ currentLabel }}</strong>
        </div>
        <div class="console-topbar-spacer" />
        <label class="namespace-control">
          <span>Namespace</span>
          <input
            :value="session.namespace.value"
            aria-label="当前命名空间"
            autocomplete="off"
            @change="session.setNamespace(($event.target as HTMLInputElement).value)"
          >
        </label>
        <span class="status-chip console-scope-chip">management-all</span>
        <button class="console-icon-button" type="button" aria-label="退出 Console" @click="logout">
          ↗
        </button>
      </header>

      <main id="console-main" class="console-content" tabindex="-1">
        <RouterView />
      </main>
    </div>
  </div>

  <ElDrawer
    v-model="mobileNavigationOpen"
    title="Runtime operations"
    direction="ltr"
    size="min(330px, 86vw)"
    class="console-mobile-drawer"
  >
    <nav class="console-navigation" aria-label="移动端 Runtime Console 主导航">
      <button
        v-for="item in navigation"
        :key="item.route"
        type="button"
        class="console-nav-item"
        :class="{ active: route.name === item.route }"
        :aria-current="route.name === item.route ? 'page' : undefined"
        @click="navigate(item.route)"
      >
        <span class="console-nav-code">{{ item.code }}</span>
        <span>{{ item.label }}</span>
      </button>
    </nav>
  </ElDrawer>
</template>

<style scoped>
.console-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
}

.console-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 25px 18px 20px;
  border-right: 1px solid var(--console-line);
  background: rgba(11, 16, 13, 0.92);
  backdrop-filter: blur(20px);
}

.console-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 8px 30px;
}

.console-brand strong,
.console-brand small {
  display: block;
}

.console-brand strong {
  font-size: 16px;
  letter-spacing: 0.02em;
}

.console-brand small {
  margin-top: 5px;
  color: var(--console-muted);
  font: 10px/1 var(--console-mono);
  letter-spacing: 0.13em;
}

.console-brand-mark {
  position: relative;
  width: 38px;
  height: 38px;
  flex: none;
  border: 1px solid rgba(185, 243, 107, 0.48);
  border-radius: 13px 5px;
  background: linear-gradient(145deg, rgba(185, 243, 107, 0.22), rgba(104, 217, 207, 0.03));
}

.console-brand-mark span {
  position: absolute;
  width: 7px;
  height: 7px;
  top: 9px;
  left: 9px;
  border-radius: 50%;
  background: var(--console-lime);
}

.console-brand-mark span:last-child {
  width: 5px;
  height: 5px;
  inset: auto 8px 8px auto;
  background: var(--console-cyan);
}

.console-nav-label {
  margin: 4px 10px 10px;
  color: var(--console-dim);
  font: 10px/1 var(--console-mono);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.console-navigation {
  display: grid;
  gap: 5px;
}

.console-nav-item {
  width: 100%;
  min-height: 44px;
  display: grid;
  grid-template-columns: 28px 1fr;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 11px;
  background: transparent;
  color: var(--console-muted);
  cursor: pointer;
  text-align: left;
  font-size: 13px;
  font-weight: 650;
  transition: var(--console-transition);
}

.console-nav-item:hover {
  background: rgba(255, 255, 255, 0.03);
  color: var(--console-text);
  transform: translateX(2px);
}

.console-nav-item.active {
  border-color: rgba(185, 243, 107, 0.18);
  background: linear-gradient(90deg, rgba(185, 243, 107, 0.13), rgba(185, 243, 107, 0.025));
  color: var(--console-lime);
}

.console-nav-code {
  color: var(--console-dim);
  font: 10px/1 var(--console-mono);
}

.console-runtime-card {
  margin-top: auto;
  display: grid;
  gap: 10px;
  padding: 15px;
  border: 1px solid var(--console-line);
  border-radius: 14px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.025), transparent);
}

.console-runtime-card strong {
  font: 600 12px/1 var(--console-mono);
}

.console-runtime-card small {
  color: var(--console-dim);
  font: 10px/1.4 var(--console-mono);
}

.console-main-column {
  min-width: 0;
}

.console-topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  min-height: 74px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 34px;
  border-bottom: 1px solid var(--console-line);
  background: rgba(9, 13, 11, 0.76);
  backdrop-filter: blur(20px);
}

.console-breadcrumb {
  color: var(--console-muted);
  font: 11px/1 var(--console-mono);
  letter-spacing: 0.07em;
}

.console-breadcrumb strong {
  color: var(--console-text);
}

.console-topbar-spacer {
  flex: 1;
}

.namespace-control {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 11px;
  border: 1px solid var(--console-line);
  border-radius: 10px;
  background: var(--console-panel);
}

.namespace-control span {
  color: var(--console-dim);
  font: 10px/1 var(--console-mono);
  text-transform: uppercase;
}

.namespace-control input {
  width: 132px;
  border: 0;
  background: transparent;
  color: var(--console-text);
  font: 11px/1 var(--console-mono);
}

.console-icon-button,
.console-mobile-menu {
  width: 44px;
  height: 44px;
  flex: none;
  display: grid;
  place-items: center;
  border: 1px solid var(--console-line);
  border-radius: 10px;
  background: var(--console-panel);
  color: var(--console-muted);
  cursor: pointer;
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
  width: min(100%, 1680px);
  min-height: calc(100vh - 74px);
  margin: 0 auto;
  padding: 36px 34px 56px;
}

.console-page-enter-active,
.console-page-leave-active {
  transition: opacity 150ms ease, transform 150ms ease;
}

.console-page-enter-from,
.console-page-leave-to {
  opacity: 0;
  transform: translateY(5px);
}

@media (max-width: 980px) {
  .console-shell {
    grid-template-columns: 1fr;
  }

  .console-sidebar {
    display: none;
  }

  .console-mobile-menu {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
}

@media (max-width: 680px) {
  .console-topbar {
    min-height: 64px;
    padding: 10px 16px;
  }

  .console-breadcrumb,
  .console-scope-chip,
  .namespace-control span {
    display: none;
  }

  .namespace-control {
    margin-left: auto;
  }

  .namespace-control input {
    width: 118px;
  }

  .console-content {
    min-height: calc(100vh - 64px);
    padding: 25px 16px 46px;
  }
}
</style>
