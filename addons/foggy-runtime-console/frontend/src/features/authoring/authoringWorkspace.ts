import type { AuthoringWorkspaceInfo, AuthoringWorkspaceState } from './types'

export interface WorkspaceActions {
  read: boolean
  mutate: boolean
  diff: boolean
  validate: boolean
  query: boolean
  discard: boolean
}

export function workspaceActions(state: AuthoringWorkspaceState): WorkspaceActions {
  if (state === 'DISCARDED') {
    return { read: false, mutate: false, diff: false, validate: false, query: false, discard: false }
  }
  if (state === 'STALE') {
    return { read: true, mutate: true, diff: true, validate: false, query: false, discard: true }
  }
  return {
    read: true,
    mutate: true,
    diff: true,
    validate: true,
    query: state === 'VALIDATED',
    discard: true
  }
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

export function suggestedModelName(path: string): string {
  const filename = path.split('/').at(-1) || ''
  return filename.endsWith('.qm') ? filename.slice(0, -3) : ''
}

export function shortRevision(value?: string): string {
  if (!value) return '—'
  const digest = value.startsWith('sha256:') ? value.slice(7) : value
  return digest.length > 14 ? `${digest.slice(0, 10)}…${digest.slice(-4)}` : digest
}
