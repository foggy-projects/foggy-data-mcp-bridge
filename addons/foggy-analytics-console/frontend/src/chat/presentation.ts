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
