export type AuthoringWorkspaceState =
  | 'DRAFT'
  | 'VALIDATED'
  | 'STALE'
  | 'PUBLISHING'
  | 'RECOVERY_REQUIRED'
  | 'PUBLISHED'
  | 'ROLLING_BACK'
  | 'ROLLBACK_REQUIRED'
  | 'ROLLED_BACK'
  | 'DISCARDED'

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

export interface PublicationEvidence {
  attemptId: string
  status: string
  candidateRevision: string
  baseBundleRevision: string
  appliedBundleRevision?: string | null
  baseNamespaceSourceRevision: string
  publishedNamespaceSourceRevision?: string | null
  beforeCatalogGeneration?: string | null
  afterCatalogGeneration?: string | null
  recoveredCatalogGeneration?: string | null
  startedAt: string
  completedAt?: string | null
  diagnostics: string[]
  rollback?: RollbackEvidence | null
}

export interface RollbackEvidence {
  status: string
  startedAt: string
  rolledBackNamespaceSourceRevision?: string | null
  rolledBackCatalogGeneration?: string | null
  completedAt?: string | null
  forwardRecoveredNamespaceSourceRevision?: string | null
  forwardRecoveredCatalogGeneration?: string | null
  diagnostics: string[]
}

export interface ReleaseImportEvidence {
  packageId: string
  formatVersion: string
  sourceRuntimeApiVersion: string
  sourceNamespace: string
  sourceBundle: string
  exportedCandidateRevision: string
  importedAt: string
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
  lastPublication?: PublicationEvidence | null
  releaseImport?: ReleaseImportEvidence | null
  diagnostics: string[]
}

export interface AuthoringReleaseDependency {
  bundle: string
  sourceType: string
  sourceIdentity: string
  artifactRevision?: string | null
}

export interface AuthoringReleaseResource {
  path: string
  type: 'TM' | 'QM' | 'FSSCRIPT'
  size: number
  sha256: string
  content: string
}

export interface AuthoringReleasePackage {
  formatVersion: string
  packageId: string
  sourceRuntimeApiVersion: string
  sourceNamespace: string
  sourceBundle: string
  candidateRevision: string
  baseBundleRevision: string
  baseNamespaceSourceRevision: string
  exportedAt: string
  validation: ValidationEvidence
  dependencies: AuthoringReleaseDependency[]
  resources: AuthoringReleaseResource[]
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
