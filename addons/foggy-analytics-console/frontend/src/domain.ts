export type Role = 'ADMIN' | 'DESIGNER' | 'VIEWER'
export type AssetKind = 'REPORT' | 'DASHBOARD'
export type AssetStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'
export type Visibility = 'PRIVATE' | 'CONSOLE'

export interface Session {
  subjectRef: string
  displayName: string
  roles: Role[]
}

export interface Folder {
  folderId: string
  name: string
  parentFolderId: string | null
  ownerSubjectRef: string
}

export interface Asset {
  assetId: string
  title: string
  description: string
  folderId: string | null
  ownerSubjectRef: string
  kind: AssetKind
  bundleRef: string
  artifactRef: string
  resourcePath: string
  bundleRevision: string
  validatedBundleRevision: string | null
  status: AssetStatus
  visibility: Visibility
  viewerSubjectRefs: string[]
  updatedAt: string
}

export interface AssetDetail {
  asset: Asset
  definitionContent: string | null
}

export interface Widget {
  widgetRef: string
  visual: { kind: string; hints: Record<string, string> }
  state: string
  columns: Array<{ name: string; type: string; nullable: boolean }>
  rows: Array<Record<string, unknown>>
  truncated: boolean
  diagnostics: string[]
}

export interface RenderResult {
  artifact: { kind: string; ref: string }
  resolvedBundleRevision: string
  state: string
  widgets: Widget[]
  diagnostics: string[]
}

export const canDesign = (session: Session | null) =>
  Boolean(session?.roles.some(role => role === 'ADMIN' || role === 'DESIGNER'))

export const shortRevision = (revision: string) =>
  revision.startsWith('sha256:') ? revision.slice(7, 19) : revision.slice(0, 12)

export const assetsInFolder = (assets: Asset[], folderId: string | null) =>
  assets.filter(asset => folderId === null || asset.folderId === folderId)
