import { describe, expect, it } from 'vitest'
import {
  canPublishWorkspace,
  candidateExecutionFacts,
  candidateQueryCsvFilename,
  isCurrentValidation,
  shortRevision,
  suggestedModelName,
  workspaceActions,
  workspaceResourcePathError
} from '@/features/authoring/authoringWorkspace'
import {
  publishWorkspaceRequest,
  recoverWorkspaceRequest
} from '@/features/authoring/useWorkspacePublication'
import type { AuthoringWorkspaceInfo } from '@/features/authoring/types'

describe('authoring workspace UI policy', () => {
  it('gates actions by the server-owned workspace state', () => {
    expect(workspaceActions('DRAFT')).toMatchObject({ mutate: true, validate: true, query: false })
    expect(workspaceActions('VALIDATED')).toMatchObject({ mutate: true, validate: true, query: true })
    expect(workspaceActions('STALE')).toMatchObject({ read: true, mutate: true, validate: false, query: false })
    expect(workspaceActions('PUBLISHING')).toMatchObject({
      read: true,
      mutate: false,
      publish: false,
      recover: false,
      refreshPublication: true
    })
    expect(workspaceActions('RECOVERY_REQUIRED')).toMatchObject({
      read: true,
      mutate: false,
      discard: false,
      recover: true,
      refreshPublication: true
    })
    expect(workspaceActions('PUBLISHED')).toMatchObject({
      read: true,
      mutate: false,
      validate: false,
      query: false,
      discard: false,
      createNext: true
    })
    expect(workspaceActions('UNKNOWN' as never)).toMatchObject({
      read: false,
      mutate: false,
      publish: false,
      recover: false
    })
    expect(workspaceActions('DISCARDED')).toEqual({
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
    })
  })

  it('matches the workspace resource path contract without silently normalizing input', () => {
    expect(workspaceResourcePathError('models/Order.tm')).toBe('')
    expect(workspaceResourcePathError('query/Order.qm')).toBe('')
    expect(workspaceResourcePathError('scripts/order.fsscript')).toBe('')
    expect(workspaceResourcePathError('../Order.tm')).toContain('..')
    expect(workspaceResourcePathError('models\\Order.tm')).toContain('反斜杠')
    expect(workspaceResourcePathError('/models/Order.tm')).toContain('相对于')
    expect(workspaceResourcePathError('models/order.json')).toContain('.fsscript')
  })

  it('only treats exact validation evidence as current', () => {
    const workspace: AuthoringWorkspaceInfo = {
      workspaceId: 'ws-1',
      targetNamespace: 'default',
      sourceBundle: 'demo',
      sourceKind: 'runtime-managed',
      baseBundleRevision: 'base-1',
      baseNamespaceSourceRevision: 'source-1',
      candidateRevision: 'candidate-1',
      state: 'VALIDATED',
      createdAt: '2026-08-01T00:00:00Z',
      updatedAt: '2026-08-01T00:00:00Z',
      diagnostics: [],
      lastValidation: {
        valid: true,
        candidateRevision: 'candidate-1',
        baseBundleRevision: 'base-1',
        baseNamespaceSourceRevision: 'source-1',
        validatedAt: '2026-08-01T00:00:00Z',
        totalFiles: 2,
        validFiles: 2,
        invalidFiles: 0,
        cascadingErrors: 0,
        issues: []
      }
    }
    expect(isCurrentValidation(workspace)).toBe(true)
    expect(isCurrentValidation({
      ...workspace,
      state: 'DRAFT',
      lastValidation: { ...workspace.lastValidation!, valid: false, invalidFiles: 1 }
    })).toBe(true)
    expect(isCurrentValidation({ ...workspace, candidateRevision: 'candidate-2' })).toBe(false)
    expect(canPublishWorkspace(workspace)).toBe(true)
    expect(canPublishWorkspace({ ...workspace, state: 'DRAFT' })).toBe(false)
    expect(canPublishWorkspace({
      ...workspace,
      lastValidation: { ...workspace.lastValidation!, valid: false }
    })).toBe(false)
  })

  it('builds exact publish and pinned recovery requests without caller-owned target fields', () => {
    const workspace: AuthoringWorkspaceInfo = {
      workspaceId: 'ws-1',
      targetNamespace: 'default',
      sourceBundle: 'demo',
      sourceKind: 'runtime-managed',
      baseBundleRevision: 'base-1',
      baseNamespaceSourceRevision: 'source-1',
      candidateRevision: 'candidate-1',
      state: 'RECOVERY_REQUIRED',
      createdAt: '2026-08-01T00:00:00Z',
      updatedAt: '2026-08-01T00:00:00Z',
      diagnostics: [],
      lastPublication: {
        attemptId: 'attempt-1',
        status: 'RECOVERY_REQUIRED',
        candidateRevision: 'candidate-1',
        baseBundleRevision: 'base-1',
        baseNamespaceSourceRevision: 'source-1',
        startedAt: '2026-08-01T00:00:00Z',
        diagnostics: []
      }
    }
    expect(publishWorkspaceRequest(workspace)).toEqual({
      expectedCandidateRevision: 'candidate-1',
      expectedBaseBundleRevision: 'base-1',
      expectedBaseNamespaceSourceRevision: 'source-1'
    })
    expect(recoverWorkspaceRequest(workspace)).toEqual({
      expectedCandidateRevision: 'candidate-1',
      publicationAttemptId: 'attempt-1'
    })
    expect(recoverWorkspaceRequest({
      ...workspace,
      lastPublication: { ...workspace.lastPublication!, candidateRevision: 'candidate-other' }
    })).toBeNull()
  })

  it('labels filename-derived model names as suggestions and shortens opaque revisions', () => {
    expect(suggestedModelName('query/OrderQuery.qm')).toBe('OrderQuery')
    expect(suggestedModelName('models/Order.tm')).toBe('')
    expect(shortRevision('sha256:1234567890abcdef')).toBe('1234567890…cdef')
  })

  it('renders only returned execution facts and creates a contextual CSV filename', () => {
    expect(candidateExecutionFacts({ provider: 'JDBC', status: 'EXECUTED', durationMs: 9 })).toEqual({
      provider: 'JDBC',
      status: 'EXECUTED',
      duration: '9 ms'
    })
    expect(candidateExecutionFacts({ provider: { inferred: true }, durationMs: '9' })).toEqual({
      provider: '—',
      status: '—',
      duration: '—'
    })
    expect(candidateQueryCsvFilename('Order Query', 'ws/default:001')).toBe(
      'candidate-Order-Query-ws-default-001.csv'
    )
  })
})
