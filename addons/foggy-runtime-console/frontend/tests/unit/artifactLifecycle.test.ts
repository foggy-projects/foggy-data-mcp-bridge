import { describe, expect, it } from 'vitest'
import {
  LatestRequestGate,
  exactByteTitle,
  filterLifecycleObjects,
  formatLifecycleBytes,
  lifecycleCapabilitySupported,
  type ArtifactLifecycleObject
} from '@/features/lifecycle/artifactLifecycle'

const objects: ArtifactLifecycleObject[] = [
  {
    store: 'PUBLISHED',
    type: 'PUBLISHED_ARTIFACT',
    identity: 'artifact:alpha',
    status: 'VERIFIED',
    bytes: 1536,
    referenceClass: 'MUST_RETAIN',
    references: ['bundle:sales:orders'],
    blockedReason: null
  },
  {
    store: 'WORKSPACE',
    type: 'WORKSPACE_REVISION',
    identity: 'workspace:beta:revision:sha256:02',
    status: 'OBSOLETE',
    bytes: 42,
    referenceClass: 'PROVABLY_UNREACHABLE_CANDIDATE',
    references: [],
    blockedReason: null
  },
  {
    store: 'PUBLISHED',
    type: 'PUBLICATION_METADATA_RECOVERY_PENDING',
    identity: 'attempt:gamma:temporary',
    status: 'INTERRUPTED',
    bytes: 80,
    referenceClass: 'UNKNOWN_PRESERVE',
    references: [],
    blockedReason: 'PUBLICATION_METADATA_RECOVERY_PENDING'
  }
]

describe('artifact lifecycle presentation policy', () => {
  it('requires the accepted capability value', () => {
    expect(lifecycleCapabilitySupported({
      'authoring.artifacts.lifecycleInventory': 'supported'
    })).toBe(true)
    expect(lifecycleCapabilitySupported({
      'authoring.artifacts.lifecycleInventory': 'disabled'
    })).toBe(false)
    expect(lifecycleCapabilitySupported(undefined)).toBe(false)
  })

  it('filters the server-ordered ledger without changing source facts', () => {
    expect(filterLifecycleObjects(objects, {
      store: 'PUBLISHED',
      referenceClass: 'ALL',
      blocked: 'ALL',
      query: ''
    }).map(item => item.identity)).toEqual([
      'artifact:alpha',
      'attempt:gamma:temporary'
    ])
    expect(filterLifecycleObjects(objects, {
      store: 'ALL',
      referenceClass: 'UNKNOWN_PRESERVE',
      blocked: 'BLOCKED',
      query: 'recovery_pending'
    })).toEqual([objects[2]])
    expect(objects).toHaveLength(3)
  })

  it('formats readable sizes while preserving an exact byte title', () => {
    expect(formatLifecycleBytes(0)).toBe('0 B')
    expect(formatLifecycleBytes(1536)).toBe('1.50 KiB')
    expect(formatLifecycleBytes(-1)).toBe('N/A')
    expect(exactByteTitle(1536)).toBe('1,536 bytes')
  })

  it('rejects stale response generations', () => {
    const gate = new LatestRequestGate()
    const first = gate.begin()
    const second = gate.begin()
    expect(gate.isLatest(first)).toBe(false)
    expect(gate.isLatest(second)).toBe(true)
  })
})
