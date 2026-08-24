import { describe, expect, it } from 'vitest'
import { assetsInFolder, canDesign, shortRevision, type Asset } from './domain'

const asset = (folderId: string | null): Asset => ({
  assetId: 'analytics-1', title: '收入脉冲', description: '', folderId,
  ownerSubjectRef: 'alice', kind: 'REPORT', bundleRef: 'sales', artifactRef: 'pulse',
  resourcePath: 'reports/pulse.report.json', bundleRevision: `sha256:${'a'.repeat(64)}`,
  validatedBundleRevision: null, status: 'DRAFT', visibility: 'PRIVATE',
  viewerSubjectRefs: [], updatedAt: '2026-08-24T00:00:00Z'
})

describe('analytics console domain', () => {
  it('keeps authoring limited to admin and designer', () => {
    expect(canDesign({ subjectRef: 'a', displayName: 'A', roles: ['VIEWER'] })).toBe(false)
    expect(canDesign({ subjectRef: 'd', displayName: 'D', roles: ['DESIGNER'] })).toBe(true)
  })

  it('filters folders and renders bounded revisions', () => {
    expect(assetsInFolder([asset('north'), asset('south')], 'north')).toHaveLength(1)
    expect(shortRevision(`sha256:${'b'.repeat(64)}`)).toBe('bbbbbbbbbbbb')
  })
})
