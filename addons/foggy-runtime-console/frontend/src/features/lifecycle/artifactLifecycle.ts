export type LifecycleHealth = 'NOT_INITIALIZED' | 'PARTIAL' | 'HEALTHY' | 'BLOCKED'
export type LifecycleStore = 'WORKSPACE' | 'PUBLISHED' | 'LIVE_REGISTRY'
export type ReferenceClass =
  | 'MUST_RETAIN'
  | 'PROVABLY_UNREACHABLE_CANDIDATE'
  | 'UNKNOWN_PRESERVE'

export interface ArtifactLifecycleRoot {
  store: LifecycleStore
  health: LifecycleHealth
  objectCount: number
  bytes: number
  blockedReasons: string[]
}

export interface ArtifactLifecycleSummary {
  totalObjects: number
  totalBytes: number
  mustRetain: number
  provablyUnreachableCandidates: number
  unknownPreserve: number
  blockedObjects: number
}

export interface ArtifactLifecycleObject {
  store: LifecycleStore
  type: string
  identity: string
  status: string
  bytes: number
  referenceClass: ReferenceClass
  references: string[]
  blockedReason?: string | null
}

export interface ArtifactLifecycleInventory {
  capturedAt: string
  health: LifecycleHealth
  roots: ArtifactLifecycleRoot[]
  summary: ArtifactLifecycleSummary
  objects: ArtifactLifecycleObject[]
  blockedReasons: string[]
}

export interface LifecycleFilters {
  store: 'ALL' | LifecycleStore
  referenceClass: 'ALL' | ReferenceClass
  blocked: 'ALL' | 'BLOCKED' | 'CLEAR'
  query: string
}

export function lifecycleCapabilitySupported(capabilities: Record<string, string> | undefined): boolean {
  return capabilities?.['authoring.artifacts.lifecycleInventory'] === 'supported'
}

export function filterLifecycleObjects(
  objects: ArtifactLifecycleObject[],
  filters: LifecycleFilters
): ArtifactLifecycleObject[] {
  const query = filters.query.trim().toLocaleLowerCase()
  return objects.filter(object => {
    if (filters.store !== 'ALL' && object.store !== filters.store) return false
    if (filters.referenceClass !== 'ALL' && object.referenceClass !== filters.referenceClass) return false
    if (filters.blocked === 'BLOCKED' && !object.blockedReason) return false
    if (filters.blocked === 'CLEAR' && object.blockedReason) return false
    if (!query) return true
    return [
      object.store,
      object.type,
      object.identity,
      object.status,
      object.referenceClass,
      object.blockedReason || '',
      ...object.references
    ].some(value => value.toLocaleLowerCase().includes(query))
  })
}

export function formatLifecycleBytes(value: number): string {
  if (!Number.isFinite(value) || value < 0) return 'N/A'
  if (value < 1024) return `${value} B`
  const units = ['KiB', 'MiB', 'GiB', 'TiB']
  let amount = value
  let unit = -1
  do {
    amount /= 1024
    unit += 1
  } while (amount >= 1024 && unit < units.length - 1)
  const fractionDigits = amount >= 100 ? 0 : amount >= 10 ? 1 : 2
  return `${amount.toFixed(fractionDigits)} ${units[unit]}`
}

export function exactByteTitle(value: number): string {
  return Number.isFinite(value) && value >= 0
    ? `${value.toLocaleString('en-US')} bytes`
    : 'Unknown byte count'
}

export function formatCapturedAt(value: string): string {
  const timestamp = Date.parse(value)
  if (!Number.isFinite(timestamp)) return value || '未提供'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(timestamp)
}

export class LatestRequestGate {
  private sequence = 0

  begin(): number {
    this.sequence += 1
    return this.sequence
  }

  isLatest(sequence: number): boolean {
    return sequence === this.sequence
  }
}
