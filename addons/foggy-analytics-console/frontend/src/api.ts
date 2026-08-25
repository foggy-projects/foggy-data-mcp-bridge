import type { Asset, AssetDetail, Folder, RenderResult, Session, Visibility } from './domain'

interface Envelope<T> {
  success: boolean
  data: T | null
  error: { code: string; message: string } | null
  requestId: string
}

export interface CreateDraftInput {
  title: string
  description: string
  folderId: string | null
  kind: 'REPORT' | 'DASHBOARD'
  bundleRef: string
  artifactRef: string
  expectedBundleRevision: string
  definitionContent: string | null
}

export interface Conversation {
  conversationId: string
  assetId: string | null
  mode: 'QUESTION' | 'DESIGN'
  questionProfileId: string | null
  namespace: string | null
  modelName: string | null
  modelRevision: string | null
  askInvocationRef: string
}

export interface ConversationSummary {
  conversationId: string
  title: string
  questionProfileId: string
  createdAt: string
  lastActivityAt: string
  namespace: string
  modelName: string
  modelRevision: string
}

export interface QuestionProfile {
  profileId: string
  displayName: string
  description: string | null
  namespace: string
  modelName: string
}

export interface AgentTurn {
  askInvocationRef: string
  operation: 'START' | 'CONTINUE'
  displayState: string
  definitiveTerminal: boolean
  userMessage: string | null
  assistantMessage: string | null
  failureCode: string | null
  startedAt: string
  updatedAt: string
  durationMs: number
}

export interface AgentActivity {
  sequence: number
  label: string
  state: 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  occurredAt: string
  errorCode: string | null
}

export interface AgentToolCall {
  sequence: number
  functionRef: string
  state: 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  startedAt: string | null
  completedAt: string | null
  durationMs: number | null
  errorCode: string | null
}

export interface AgentTurnDetail {
  askInvocationRef: string
  historyState: 'PENDING' | 'COMPLETE' | 'PARTIAL' | 'UNAVAILABLE'
  eventsTruncated: boolean
  agentActivities: AgentActivity[]
  toolCalls: AgentToolCall[]
}

const root = '/analytics-console/api/v1'

const call = async <T>(path: string, init?: RequestInit): Promise<T> => {
  const response = await fetch(`${root}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...(init?.body ? { 'X-Foggy-Analytics-Console-Request': '1' } : {}),
      ...init?.headers
    }
  })
  const envelope = await response.json() as Envelope<T>
  if (!response.ok || !envelope.success || envelope.data === null) {
    throw new Error(envelope.error?.message ?? `请求失败 (${response.status})`)
  }
  return envelope.data
}

const json = (method: string, body: unknown): RequestInit => ({
  method,
  body: JSON.stringify(body)
})

export const api = {
  session: () => call<Session>('/session'),
  folders: () => call<Folder[]>('/folders'),
  createFolder: (name: string) => call<Folder>('/folders', json('POST', { name })),
  assets: () => call<Asset[]>('/assets'),
  asset: (assetId: string) => call<AssetDetail>(`/assets/${encodeURIComponent(assetId)}`),
  createDraft: (input: CreateDraftInput) =>
    call<Asset>('/assets/drafts', json('POST', input)),
  saveDefinition: (assetId: string, expectedBundleRevision: string, definitionContent: string) =>
    call<Asset>(`/assets/${encodeURIComponent(assetId)}/definition`,
      json('PUT', { expectedBundleRevision, definitionContent })),
  validate: (assetId: string, expectedBundleRevision: string) =>
    call<Asset>(`/assets/${encodeURIComponent(assetId)}:validate`,
      json('POST', { expectedBundleRevision })),
  preview: (assetId: string, expectedBundleRevision: string) =>
    call<RenderResult>(`/assets/${encodeURIComponent(assetId)}:preview`, json('POST', {
      expectedBundleRevision,
      parameters: {},
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
      locale: navigator.language || 'zh-CN'
    })),
  publish: (assetId: string, expectedBundleRevision: string) =>
    call<Asset>(`/assets/${encodeURIComponent(assetId)}:publish`,
      json('POST', { expectedBundleRevision })),
  audience: (assetId: string, visibility: Visibility, viewerSubjectRefs: string[]) =>
    call<Asset>(`/assets/${encodeURIComponent(assetId)}/audience`,
      json('PUT', { visibility, viewerSubjectRefs })),
  ask: (assetId: string, prompt: string) =>
    call<Conversation>(`/assets/${encodeURIComponent(assetId)}/agent/asks`,
      json('POST', { prompt })),
  questionProfiles: () => call<QuestionProfile[]>('/agent/question-profiles'),
  conversations: () => call<ConversationSummary[]>('/agent/conversations'),
  conversation: (conversationId: string) =>
    call<Conversation>(`/agent/conversations/${encodeURIComponent(conversationId)}`),
  askQuestion: (profileId: string, prompt: string) =>
    call<Conversation>('/agent/questions', json('POST', { profileId, prompt })),
  continueConversation: (conversationId: string, prompt: string) =>
    call<Conversation>(`/agent/conversations/${encodeURIComponent(conversationId)}/turns`,
      json('POST', { prompt })),
  turns: (conversationId: string) =>
    call<AgentTurn[]>(`/agent/conversations/${encodeURIComponent(conversationId)}/turns`),
  turnDetail: (conversationId: string, askInvocationRef: string) =>
    call<AgentTurnDetail>(
      `/agent/conversations/${encodeURIComponent(conversationId)}/turns/${encodeURIComponent(askInvocationRef)}/detail`)
}
