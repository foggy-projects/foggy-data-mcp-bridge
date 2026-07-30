import { computed, ref } from 'vue'
import { checkAccess, RuntimeRequestError, type AccessCheck } from '@/api/client'
import {
  clearConsoleSession,
  readNamespace,
  readRuntimeToken,
  writeNamespace,
  writeRuntimeToken
} from '@/api/storage'

const access = ref<AccessCheck | null>(null)
const validating = ref(false)
const namespace = ref(readNamespace())

export function useRuntimeSession() {
  const authenticated = computed(() => Boolean(access.value?.authenticated && readRuntimeToken()))

  async function login(token: string): Promise<void> {
    const normalized = token.trim()
    if (!normalized) {
      throw new RuntimeRequestError('请输入 Runtime API Token。', {
        code: 'RUNTIME_TOKEN_REQUIRED'
      })
    }
    validating.value = true
    try {
      const result = await checkAccess(normalized)
      if (result.authScope !== 'management-all') {
        throw new RuntimeRequestError('Runtime 未启用 management-all，Console 已拒绝进入。', {
          code: 'RUNTIME_MANAGEMENT_ALL_REQUIRED'
        })
      }
      writeRuntimeToken(normalized)
      access.value = result
    } finally {
      validating.value = false
    }
  }

  async function revalidate(): Promise<boolean> {
    const token = readRuntimeToken()
    if (!token) {
      access.value = null
      return false
    }
    validating.value = true
    try {
      const result = await checkAccess(token)
      if (!result.authenticated || result.authScope !== 'management-all') {
        logout()
        return false
      }
      access.value = result
      return true
    } catch {
      logout()
      return false
    } finally {
      validating.value = false
    }
  }

  function logout(): void {
    clearConsoleSession()
    access.value = null
    window.dispatchEvent(new CustomEvent('foggy:runtime-session-cleared'))
  }

  function setNamespace(value: string): void {
    const normalized = value.trim() || 'default'
    namespace.value = normalized
    writeNamespace(normalized)
  }

  return {
    access,
    authenticated,
    validating,
    namespace,
    login,
    logout,
    revalidate,
    setNamespace
  }
}

export function resetSessionStateForTests(): void {
  access.value = null
  validating.value = false
  namespace.value = readNamespace()
}
