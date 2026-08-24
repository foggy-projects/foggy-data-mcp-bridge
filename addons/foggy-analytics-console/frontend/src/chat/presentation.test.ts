import { describe, expect, it } from 'vitest'
import type { AgentTurn } from '../api'
import {
  conversationHash,
  conversationIdFromHash,
  formatConversationTime,
  turnSequenceFinished
} from './presentation'

const turn = (terminal: boolean): AgentTurn => ({
  askInvocationRef: 'ask-1',
  operation: 'START',
  displayState: terminal ? 'COMPLETED' : 'RUNNING',
  definitiveTerminal: terminal,
  userMessage: '订单量？',
  assistantMessage: terminal ? '6 单' : null,
  failureCode: null
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
})
