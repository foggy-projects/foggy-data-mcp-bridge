export type AuthoringWorkspaceState = 'DRAFT' | 'VALIDATED' | 'STALE' | 'DISCARDED'

export interface ValidationIssue {
  path?: string
  type?: string
  code?: string
  message?: string
  category?: string
}

export interface ValidationEvidence {
  valid: boolean
  candidateRevision: string
  baseBundleRevision: string
  baseNamespaceSourceRevision: string
  validatedAt: string
  totalFiles: number
  validFiles: number
  invalidFiles: number
  cascadingErrors: number
  issues: ValidationIssue[]
}

export interface AuthoringWorkspaceInfo {
  workspaceId: string
  targetNamespace: string
  sourceBundle: string
  sourceKind: string
  baseBundleRevision: string
  baseNamespaceSourceRevision: string
  candidateRevision: string
  state: AuthoringWorkspaceState
  createdAt: string
  updatedAt: string
  lastValidation?: ValidationEvidence | null
  diagnostics: string[]
}

export interface AuthoringWorkspaceListResponse {
  workspaces: AuthoringWorkspaceInfo[]
  warnings: string[]
}

export interface AuthoringResource {
  path: string
  type: 'TM' | 'QM' | 'FSSCRIPT'
  size: number
  sha256: string
  content?: string | null
}

export interface AuthoringResourcesResponse {
  workspaceId: string
  candidateRevision: string
  resources: AuthoringResource[]
}

export interface AuthoringResourceChange {
  path: string
  type: string
  changeType: 'ADDED' | 'MODIFIED' | 'DELETED' | string
  baseSha256?: string | null
  candidateSha256?: string | null
  baseContent?: string | null
  candidateContent?: string | null
}

export interface AuthoringDiffResponse {
  workspaceId: string
  baseBundleRevision: string
  candidateRevision: string
  changes: AuthoringResourceChange[]
}

export interface AuthoringQueryResponse {
  workspaceId: string
  sourceBundle: string
  namespace: string
  baseBundleRevision: string
  baseNamespaceSourceRevision: string
  candidateRevision: string
  catalogIdentity?: Record<string, unknown>
  phase: string
  response?: {
    items?: Record<string, unknown>[]
    warnings?: string[]
    total?: number
    hasNext?: boolean
    pagination?: Record<string, unknown>
    execution?: Record<string, unknown>
  }
  diagnostics: string[]
}
