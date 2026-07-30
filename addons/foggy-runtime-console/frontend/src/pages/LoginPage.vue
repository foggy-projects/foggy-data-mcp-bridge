<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RuntimeRequestError } from '@/api/client'
import { useRuntimeSession } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useRuntimeSession()
const token = ref('')
const errorMessage = ref('')
const tokenInput = ref<HTMLInputElement>()

onMounted(() => {
  void nextTick(() => tokenInput.value?.focus())
})

async function submit(): Promise<void> {
  errorMessage.value = ''
  try {
    await session.login(token.value)
    token.value = ''
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/overview'
    await router.replace(redirect)
  } catch (error) {
    token.value = ''
    errorMessage.value = error instanceof RuntimeRequestError
      ? error.message
      : '无法校验 Runtime Token，请检查服务配置。'
    await nextTick()
    tokenInput.value?.focus()
  }
}
</script>

<template>
  <main class="login-screen">
    <section class="login-frame" aria-labelledby="login-title">
      <div class="login-story">
        <div class="login-wordmark">Foggy Runtime / Console</div>
        <h1>One runtime.<em>Clear control.</em></h1>
        <p>
          面向单个 Java Runtime 的同源管理入口。Console 不建立账号体系，
          管理权限始终由服务端 <code>management-all</code> gate 校验。
        </p>
        <div class="login-scope">TARGET AUTH SCOPE / <strong>MANAGEMENT-ALL</strong></div>
      </div>

      <form class="login-form" novalidate @submit.prevent="submit">
        <div class="login-kicker">Same-origin management</div>
        <h2 id="login-title">连接 Runtime</h2>
        <p>输入部署方提供的 Runtime API Token。请求仅发送到当前页面同源的 API。</p>

        <div class="console-field">
          <label for="runtime-token">Runtime API Token</label>
          <input
            id="runtime-token"
            ref="tokenInput"
            v-model="token"
            class="console-input"
            type="password"
            placeholder="X-Foggy-Runtime-Code"
            autocomplete="new-password"
            :aria-invalid="Boolean(errorMessage)"
            aria-describedby="token-help token-error"
            :disabled="session.validating.value"
          >
        </div>

        <div id="token-help" class="login-help">
          <span aria-hidden="true">◆</span>
          <span>Token 只保存在当前标签页的 sessionStorage，不进入 URL、localStorage 或诊断输出。</span>
        </div>

        <button
          class="console-button primary login-submit"
          type="submit"
          :disabled="session.validating.value"
        >
          {{ session.validating.value ? '正在安全校验…' : '校验并进入 Console' }}
        </button>

        <div id="token-error" class="login-error" role="alert" aria-live="assertive">
          {{ errorMessage }}
        </div>
      </form>
    </section>
  </main>
</template>

<style scoped>
.login-screen {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 30px;
}

.login-frame {
  position: relative;
  width: min(1040px, 100%);
  min-height: 580px;
  display: grid;
  grid-template-columns: 1.12fr 0.88fr;
  border: 1px solid var(--console-line-strong);
  border-radius: 0;
  overflow: hidden;
  background: var(--console-panel);
  box-shadow: none;
}

.login-frame::before,
.login-frame::after {
  position: absolute;
  z-index: 3;
  width: 32px;
  height: 32px;
  content: "";
  pointer-events: none;
}

.login-frame::before {
  top: -1px;
  left: -1px;
  border-top: 3px solid var(--console-paper);
  border-left: 3px solid var(--console-paper);
}

.login-frame::after {
  right: -1px;
  bottom: -1px;
  border-right: 3px solid var(--console-paper);
  border-bottom: 3px solid var(--console-paper);
}

.login-story {
  position: relative;
  overflow: hidden;
  padding: 48px;
  border-right: 1px solid var(--console-line-strong);
  background: var(--console-panel);
}

.login-story::before {
  position: absolute;
  right: 42px;
  bottom: 48px;
  width: 164px;
  height: 164px;
  border: 1px solid var(--console-line-strong);
  background:
    linear-gradient(90deg, transparent 49.5%, var(--console-line-strong) 49.5% 50.5%, transparent 50.5%),
    linear-gradient(transparent 49.5%, var(--console-line-strong) 49.5% 50.5%, transparent 50.5%);
  content: "";
}

.login-story::after {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--console-grid-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--console-grid-line) 1px, transparent 1px);
  background-size: 32px 32px;
  content: "";
  opacity: 0.55;
}

.login-wordmark,
.login-story h1,
.login-story p,
.login-scope {
  position: relative;
  z-index: 1;
}

.login-wordmark,
.login-kicker {
  color: var(--console-muted);
  font: 700 9px/1 var(--console-mono);
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.login-wordmark {
  display: inline-flex;
  gap: 12px;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--console-line-strong);
}

.login-wordmark::before {
  width: 8px;
  height: 8px;
  border: 1px solid var(--console-paper);
  background: var(--console-paper);
  content: "";
}

.login-story h1 {
  max-width: 470px;
  margin: 82px 0 0;
  font-size: clamp(43px, 5vw, 66px);
  line-height: 0.94;
  letter-spacing: -0.052em;
  font-weight: 720;
}

.login-story h1 em {
  display: block;
  color: var(--console-paper);
  font-style: normal;
  -webkit-text-fill-color: transparent;
  -webkit-text-stroke: 1px var(--console-paper);
}

.login-story p {
  max-width: 420px;
  margin: 28px 0 0;
  color: var(--console-muted);
  font-size: 14px;
  line-height: 1.75;
}

.login-story code {
  color: var(--console-paper);
  font-family: var(--console-mono);
}

.login-scope {
  position: absolute;
  left: 48px;
  bottom: 43px;
  color: var(--console-dim);
  font: 9px/1 var(--console-mono);
  letter-spacing: 0.06em;
}

.login-scope strong {
  color: var(--console-paper);
}

.login-form {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 52px;
  background:
    repeating-linear-gradient(
      135deg,
      transparent 0 10px,
      var(--console-hatch-line) 10px 11px
    ),
    var(--console-panel-2);
}

.login-form::before {
  position: absolute;
  top: 24px;
  right: 24px;
  color: var(--console-dim);
  content: "AUTH / 01";
  font: 8px/1 var(--console-mono);
  letter-spacing: 0.15em;
}

.login-form h2 {
  margin: 15px 0 0;
  font-size: 28px;
  letter-spacing: -0.03em;
}

.login-form > p {
  margin: 11px 0 31px;
  color: var(--console-muted);
  font-size: 14px;
  line-height: 1.65;
}

.login-help {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin: 17px 0 21px;
  color: var(--console-dim);
  font-size: 13px;
  line-height: 1.55;
}

.login-help > span:first-child {
  color: var(--console-paper);
  font-size: 9px;
}

.login-submit {
  width: 100%;
}

.login-error {
  min-height: 42px;
  padding-top: 13px;
  color: var(--console-text);
  font-size: 13px;
  line-height: 1.4;
  text-align: center;
}

@media (max-width: 760px) {
  .login-screen {
    padding: 18px;
  }

  .login-frame {
    grid-template-columns: 1fr;
  }

  .login-story {
    min-height: 250px;
    padding: 30px;
    border-right: 0;
    border-bottom: 1px solid var(--console-line-strong);
  }

  .login-story h1 {
    margin-top: 42px;
    font-size: 43px;
  }

  .login-story p,
  .login-scope {
    display: none;
  }

  .login-form {
    padding: 32px 30px;
  }
}

@media (max-width: 460px) {
  .login-screen {
    padding: 10px;
  }

  .login-story {
    min-height: 220px;
    padding: 24px;
  }

  .login-story h1 {
    font-size: 36px;
  }

  .login-form {
    padding: 30px 22px 24px;
  }
}
</style>
