import type { AgentTurn } from '../api'

export const conversationIdFromHash = (hash: string) => {
  const match = /^#\/chat\/([^/?#]+)$/.exec(hash)
  return match ? decodeURIComponent(match[1]) : null
}

export const conversationHash = (conversationId?: string | null) =>
  conversationId ? `#/chat/${encodeURIComponent(conversationId)}` : '#/chat'

export const turnSequenceFinished = (turns: AgentTurn[], pendingPrompt: string) => {
  if (pendingPrompt) return false
  const latest = turns.at(-1)
  return Boolean(latest?.definitiveTerminal)
}

export const formatDuration = (milliseconds: number | null | undefined) => {
  if (milliseconds === null || milliseconds === undefined || milliseconds < 0) return '—'
  if (milliseconds < 1_000) return `${Math.max(1, Math.round(milliseconds))} 毫秒`
  const seconds = milliseconds / 1_000
  if (seconds < 60) {
    const value = seconds < 10 ? seconds.toFixed(1).replace(/\.0$/, '') : Math.round(seconds)
    return `${value} 秒`
  }
  const minutes = Math.floor(seconds / 60)
  const remainder = Math.round(seconds % 60)
  return remainder ? `${minutes} 分 ${remainder} 秒` : `${minutes} 分钟`
}

export const formatJsonPayload = (value: Record<string, unknown> | null) =>
  value === null ? '' : JSON.stringify(value, null, 2)

export const formatConversationTime = (value: string, now = Date.now()) => {
  const timestamp = Date.parse(value)
  if (!Number.isFinite(timestamp)) return ''
  const elapsed = Math.max(0, now - timestamp)
  if (elapsed < 60_000) return '刚刚'
  if (elapsed < 3_600_000) return `${Math.floor(elapsed / 60_000)} 分钟前`
  if (elapsed < 86_400_000) return `${Math.floor(elapsed / 3_600_000)} 小时前`
  const date = new Date(timestamp)
  const current = new Date(now)
  if (date.getFullYear() === current.getFullYear()) {
    return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(date)
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: 'numeric', day: 'numeric'
  }).format(date)
}
