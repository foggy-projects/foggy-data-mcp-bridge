import type { AuthoringWorkspaceInfo, AuthoringWorkspaceState } from './types'

export interface WorkspaceActions {
  read: boolean
  mutate: boolean
  diff: boolean
  validate: boolean
  query: boolean
  discard: boolean
  publish: boolean
  recover: boolean
  refreshPublication: boolean
  createNext: boolean
}

const NO_ACTIONS: WorkspaceActions = {
  read: false,
  mutate: false,
  diff: false,
  validate: false,
  query: false,
  discard: false,
  publish: false,
  recover: false,
  refreshPublication: false,
  createNext: false
}

export function workspaceActions(state: AuthoringWorkspaceState): WorkspaceActions {
  if (state === 'DISCARDED') {
    return { ...NO_ACTIONS }
  }
  if (state === 'STALE') {
    return { ...NO_ACTIONS, read: true, mutate: true, diff: true, discard: true }
  }
  if (state === 'PUBLISHING') {
    return { ...NO_ACTIONS, read: true, diff: true, refreshPublication: true }
  }
  if (state === 'RECOVERY_REQUIRED') {
    return { ...NO_ACTIONS, read: true, diff: true, recover: true, refreshPublication: true }
  }
  if (state === 'PUBLISHED') {
    return { ...NO_ACTIONS, read: true, diff: true, createNext: true }
  }
  if (state === 'DRAFT' || state === 'VALIDATED') {
    return {
      ...NO_ACTIONS,
      read: true,
      mutate: true,
      diff: true,
      validate: true,
      query: state === 'VALIDATED',
      discard: true,
      publish: state === 'VALIDATED'
    }
  }
  return { ...NO_ACTIONS }
}

export function workspaceResourcePathError(value: string): string {
  const path = value.trim()
  if (!path) return '资源路径不能为空。'
  if (path.includes('\\')) return '资源路径必须使用 /，不能使用反斜杠。'
  if (path.startsWith('/') || /^[a-z]:\//i.test(path)) return '资源路径必须相对于 workspace 根目录。'
  if (/[\u0000-\u001f\u007f]/.test(path)) return '资源路径不能包含控制字符。'
  const segments = path.split('/')
  if (segments.some(segment => !segment || segment === '.' || segment === '..')) {
    return '资源路径不能包含空目录、. 或 ..。'
  }
  if (!path.endsWith('.tm') && !path.endsWith('.qm') && !path.endsWith('.fsscript')) {
    return '仅支持 .tm、.qm 和 .fsscript 资源。'
  }
  return ''
}

export function isCurrentValidation(workspace: AuthoringWorkspaceInfo): boolean {
  const evidence = workspace.lastValidation
  return Boolean(evidence
    && evidence.candidateRevision === workspace.candidateRevision
    && evidence.baseBundleRevision === workspace.baseBundleRevision
    && evidence.baseNamespaceSourceRevision === workspace.baseNamespaceSourceRevision)
}

export function canPublishWorkspace(workspace: AuthoringWorkspaceInfo): boolean {
  return workspace.state === 'VALIDATED'
    && workspaceActions(workspace.state).publish
    && isCurrentValidation(workspace)
    && workspace.lastValidation?.valid === true
}

export function suggestedModelName(path: string): string {
  const filename = path.split('/').at(-1) || ''
  return filename.endsWith('.qm') ? filename.slice(0, -3) : ''
}

export function shortRevision(value?: string): string {
  if (!value) return '—'
  const digest = value.startsWith('sha256:') ? value.slice(7) : value
  return digest.length > 14 ? `${digest.slice(0, 10)}…${digest.slice(-4)}` : digest
}

export interface CandidateExecutionFacts {
  provider: string
  status: string
  duration: string
}

function executionValue(value: unknown): string {
  return typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean'
    ? String(value)
    : '—'
}

export function candidateExecutionFacts(execution?: Record<string, unknown>): CandidateExecutionFacts {
  const durationMs = execution?.durationMs
  return {
    provider: executionValue(execution?.provider),
    status: executionValue(execution?.status),
    duration: typeof durationMs === 'number' && Number.isFinite(durationMs) ? `${durationMs} ms` : '—'
  }
}

function filenameSegment(value: string, fallback: string): string {
  const normalized = value.trim().replace(/[^a-z0-9._-]+/gi, '-').replace(/^-+|-+$/g, '')
  return normalized || fallback
}

export function candidateQueryCsvFilename(model: string, workspaceId: string): string {
  return `candidate-${filenameSegment(model, 'model')}-${filenameSegment(workspaceId, 'workspace')}.csv`
}
