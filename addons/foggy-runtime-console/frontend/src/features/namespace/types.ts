export interface BundleItem {
  name: string
  namespace?: string
  path: string
  watch?: boolean
  enabled?: boolean
  source?: string
  managedByRuntimeApi?: boolean
  canUpdate?: boolean
  canRemove?: boolean
  status?: string
  message?: string
  sourceType?: string
  editable?: boolean
  workspaceEligible?: boolean
  namespaceBindings?: string[]
  sourceIdentity?: string
}

export interface ModelItem {
  model: string
  caption?: string
  description?: string
  namespace?: string
  bundleName?: string
  sourceNamespace?: string
  resourceIdentity?: string
  sourceKnown?: boolean
  physicalTables?: string[]
  primaryTimeField?: string
  fieldCount?: number
}

export interface DatasourceItem {
  name: string
  type: string
  jdbcUrl?: string
  enabled: boolean
}

export interface LifecycleResult {
  valid?: boolean
  catalogState?: string
  beforeCatalogGeneration?: string
  afterCatalogGeneration?: string
  refreshedCount?: number
  loadedCount?: number
  validFiles?: number
  failedCount?: number
  invalidFiles?: number
  durationMs?: number
  errors?: unknown[]
  warnings?: unknown[]
  failures?: unknown[]
}
