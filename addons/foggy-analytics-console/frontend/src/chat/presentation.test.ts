import { describe, expect, it } from 'vitest'
import type { AgentTurn } from '../api'
import {
  conversationHash,
  conversationIdFromHash,
  formatConversationTime,
  formatDuration,
  turnSequenceFinished
} from './presentation'

const turn = (terminal: boolean): AgentTurn => ({
  askInvocationRef: 'ask-1',
  operation: 'START',
  displayState: terminal ? 'COMPLETED' : 'RUNNING',
  definitiveTerminal: terminal,
  userMessage: '订单量？',
  assistantMessage: terminal ? '6 单' : null,
  failureCode: null,
  startedAt: '2026-08-24T09:59:48Z',
  updatedAt: '2026-08-24T10:00:00Z',
  durationMs: 12_000
})

describe('chat presentation', () => {
  it('round-trips conversation hashes without accepting unrelated routes', () => {
    expect(conversationHash('conversation/a')).toBe('#/chat/conversation%2Fa')
    expect(conversationIdFromHash('#/chat/conversation%2Fa')).toBe('conversation/a')
    expect(conversationIdFromHash('#/studio')).toBeNull()
  })

  it('keeps polling while a prompt or the latest turn is unfinished', () => {
    expect(turnSequenceFinished([turn(true)], '追问')).toBe(false)
    expect(turnSequenceFinished([turn(false)], '')).toBe(false)
    expect(turnSequenceFinished([turn(true)], '')).toBe(true)
  })

  it('formats recent activity without exposing message content', () => {
    const now = Date.parse('2026-08-24T10:00:00Z')
    expect(formatConversationTime('2026-08-24T09:55:00Z', now)).toBe('5 分钟前')
  })

  it('formats turn and tool durations for the disclosure summary', () => {
    expect(formatDuration(850)).toBe('850 毫秒')
    expect(formatDuration(12_400)).toBe('12 秒')
    expect(formatDuration(72_000)).toBe('1 分 12 秒')
  })
})
