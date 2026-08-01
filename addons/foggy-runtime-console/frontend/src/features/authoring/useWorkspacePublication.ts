import type { Ref } from 'vue'
import { runtimeApi } from '@/api/client'
import type { AuthoringWorkspaceInfo } from './types'

export interface PublishWorkspaceRequest {
  expectedCandidateRevision: string
  expectedBaseBundleRevision: string
  expectedBaseNamespaceSourceRevision: string
}

export interface RecoverWorkspaceRequest {
  expectedCandidateRevision: string
  publicationAttemptId: string
}

export interface PromoteWorkspaceRequest {
  releasePackageId: string
  expectedCandidateRevision: string
  expectedBaseBundleRevision: string
  expectedBaseNamespaceSourceRevision: string
}

export interface RollbackWorkspaceRequest {
  releasePackageId: string
  expectedCandidateRevision: string
  publicationAttemptId: string
}

export function publishWorkspaceRequest(workspace: AuthoringWorkspaceInfo): PublishWorkspaceRequest {
  return {
    expectedCandidateRevision: workspace.candidateRevision,
    expectedBaseBundleRevision: workspace.baseBundleRevision,
    expectedBaseNamespaceSourceRevision: workspace.baseNamespaceSourceRevision
  }
}

export function recoverWorkspaceRequest(workspace: AuthoringWorkspaceInfo): RecoverWorkspaceRequest | null {
  const attemptId = workspace.lastPublication?.attemptId?.trim()
  if (!attemptId || workspace.lastPublication?.candidateRevision !== workspace.candidateRevision) return null
  return {
    expectedCandidateRevision: workspace.candidateRevision,
    publicationAttemptId: attemptId
  }
}

export function promoteWorkspaceRequest(workspace: AuthoringWorkspaceInfo): PromoteWorkspaceRequest | null {
  if (!workspace.releaseImport?.packageId) return null
  return {
    releasePackageId: workspace.releaseImport.packageId,
    expectedCandidateRevision: workspace.candidateRevision,
    expectedBaseBundleRevision: workspace.baseBundleRevision,
    expectedBaseNamespaceSourceRevision: workspace.baseNamespaceSourceRevision
  }
}

export function rollbackWorkspaceRequest(workspace: AuthoringWorkspaceInfo): RollbackWorkspaceRequest | null {
  const attemptId = workspace.lastPublication?.attemptId?.trim()
  const packageId = workspace.releaseImport?.packageId?.trim()
  if (!attemptId || !packageId || workspace.lastPublication?.candidateRevision !== workspace.candidateRevision) {
    return null
  }
  return {
    releasePackageId: packageId,
    expectedCandidateRevision: workspace.candidateRevision,
    publicationAttemptId: attemptId
  }
}

export function useWorkspacePublication(busy: Ref<string>) {
  async function publish(workspace: AuthoringWorkspaceInfo): Promise<AuthoringWorkspaceInfo> {
    busy.value = 'publish'
    try {
      return await runtimeApi.post<AuthoringWorkspaceInfo>(
        `authoring/workspaces/${encodeURIComponent(workspace.workspaceId)}/publish`,
        publishWorkspaceRequest(workspace)
      )
    } finally {
      busy.value = ''
    }
  }

  async function recover(workspace: AuthoringWorkspaceInfo): Promise<AuthoringWorkspaceInfo> {
    const request = recoverWorkspaceRequest(workspace)
    if (!request) throw new Error('Publication attempt 与当前 candidate revision 不匹配。')
    busy.value = 'recover'
    try {
      return await runtimeApi.post<AuthoringWorkspaceInfo>(
        `authoring/workspaces/${encodeURIComponent(workspace.workspaceId)}/publish/recover`,
        request
      )
    } finally {
      busy.value = ''
    }
  }

  async function refresh(workspaceId: string): Promise<AuthoringWorkspaceInfo> {
    busy.value = 'publication-refresh'
    try {
      return await runtimeApi.get<AuthoringWorkspaceInfo>(
        `authoring/workspaces/${encodeURIComponent(workspaceId)}`
      )
    } finally {
      busy.value = ''
    }
  }

  async function promote(workspace: AuthoringWorkspaceInfo): Promise<AuthoringWorkspaceInfo> {
    const request = promoteWorkspaceRequest(workspace)
    if (!request) throw new Error('Release package provenance 缺失。')
    busy.value = 'promote'
    try {
      return await runtimeApi.post<AuthoringWorkspaceInfo>(
        `authoring/workspaces/${encodeURIComponent(workspace.workspaceId)}/promote`,
        request
      )
    } finally {
      busy.value = ''
    }
  }

  async function rollback(workspace: AuthoringWorkspaceInfo): Promise<AuthoringWorkspaceInfo> {
    const request = rollbackWorkspaceRequest(workspace)
    if (!request) throw new Error('Rollback package/candidate/attempt identity 缺失。')
    busy.value = 'rollback'
    try {
      return await runtimeApi.post<AuthoringWorkspaceInfo>(
        `authoring/workspaces/${encodeURIComponent(workspace.workspaceId)}/rollback`,
        request
      )
    } finally {
      busy.value = ''
    }
  }

  async function recoverRollback(workspace: AuthoringWorkspaceInfo): Promise<AuthoringWorkspaceInfo> {
    const request = rollbackWorkspaceRequest(workspace)
    if (!request) throw new Error('Rollback recovery identity 缺失。')
    busy.value = 'rollback-recover'
    try {
      return await runtimeApi.post<AuthoringWorkspaceInfo>(
        `authoring/workspaces/${encodeURIComponent(workspace.workspaceId)}/rollback/recover`,
        request
      )
    } finally {
      busy.value = ''
    }
  }

  return { publish, recover, refresh, promote, rollback, recoverRollback }
}
