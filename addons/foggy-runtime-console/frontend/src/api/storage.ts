const TOKEN_KEY = 'foggy.runtime-console.token'
const NAMESPACE_KEY = 'foggy.runtime-console.namespace'

let volatileAuthorization: string | null = null

export function readRuntimeToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function writeRuntimeToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function clearRuntimeToken(): void {
  sessionStorage.removeItem(TOKEN_KEY)
}

export function readNamespace(): string {
  return sessionStorage.getItem(NAMESPACE_KEY) || 'default'
}

export function writeNamespace(namespace: string): void {
  sessionStorage.setItem(NAMESPACE_KEY, namespace.trim() || 'default')
}

export function setDataAuthorization(value: string | null): void {
  volatileAuthorization = value?.trim() || null
}

export function readDataAuthorization(): string | null {
  return volatileAuthorization
}

export function clearConsoleSession(): void {
  clearRuntimeToken()
  volatileAuthorization = null
}

export const consoleStorageKeys = {
  token: TOKEN_KEY,
  namespace: NAMESPACE_KEY
} as const
