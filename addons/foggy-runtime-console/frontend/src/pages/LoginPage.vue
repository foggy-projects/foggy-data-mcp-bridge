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
  width: min(980px, 100%);
  min-height: 570px;
  display: grid;
  grid-template-columns: 1.08fr 0.92fr;
  border: 1px solid var(--console-line-strong);
  border-radius: 27px;
  overflow: hidden;
  background: #101613;
  box-shadow: var(--console-shadow);
}

.login-story {
  position: relative;
  overflow: hidden;
  padding: 48px;
  background: #0c110e;
}

.login-story::before {
  position: absolute;
  width: 490px;
  height: 490px;
  left: -215px;
  bottom: -265px;
  border: 1px solid rgba(185, 243, 107, 0.22);
  border-radius: 50%;
  box-shadow:
    0 0 0 54px rgba(185, 243, 107, 0.027),
    0 0 0 108px rgba(104, 217, 207, 0.018);
  content: "";
}

.login-story::after {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--console-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--console-line) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: linear-gradient(to bottom, black, transparent 90%);
  content: "";
  opacity: 0.18;
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
  color: var(--console-lime);
  font: 650 10px/1 var(--console-mono);
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.login-story h1 {
  max-width: 430px;
  margin: 86px 0 0;
  font-size: clamp(43px, 5vw, 67px);
  line-height: 0.91;
  letter-spacing: -0.055em;
  font-weight: 680;
}

.login-story h1 em {
  display: block;
  color: var(--console-lime);
  font-style: normal;
}

.login-story p {
  max-width: 420px;
  margin: 28px 0 0;
  color: var(--console-muted);
  font-size: 14px;
  line-height: 1.75;
}

.login-story code {
  color: var(--console-amber);
}

.login-scope {
  position: absolute;
  left: 48px;
  bottom: 43px;
  color: var(--console-dim);
  font: 10px/1 var(--console-mono);
}

.login-scope strong {
  color: var(--console-amber);
}

.login-form {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 52px;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.027), transparent);
}

.login-form h2 {
  margin: 13px 0 0;
  font-size: 27px;
  letter-spacing: -0.025em;
}

.login-form > p {
  margin: 11px 0 31px;
  color: var(--console-muted);
  font-size: 13px;
  line-height: 1.65;
}

.login-help {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin: 17px 0 21px;
  color: var(--console-dim);
  font-size: 12px;
  line-height: 1.55;
}

.login-help > span:first-child {
  color: var(--console-cyan);
}

.login-submit {
  width: 100%;
}

.login-error {
  min-height: 42px;
  padding-top: 13px;
  color: var(--console-red);
  font-size: 12px;
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
  }

  .login-story h1 {
    margin-top: 43px;
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
</style>
