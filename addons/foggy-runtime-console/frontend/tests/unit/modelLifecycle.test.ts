import { describe, expect, it } from 'vitest'
import {
  lifecycleDiagnosticRows,
  summarizeLifecycle
} from '@/features/models/modelLifecycle'

describe('model lifecycle result normalization', () => {
  it('summarizes refresh generations and counts', () => {
    expect(summarizeLifecycle({
      catalogState: 'PUBLISHED',
      beforeCatalogGeneration: 'g-1',
      afterCatalogGeneration: 'g-2',
      refreshedCount: 3,
      failedCount: 0,
      durationMs: 42
    })).toEqual({
      catalogState: 'PUBLISHED',
      beforeGeneration: 'g-1',
      afterGeneration: 'g-2',
      successCount: 3,
      failedCount: 0,
      durationMs: 42,
      hasIssues: false
    })
  })

  it('uses validation counts and keeps warnings as diagnostics', () => {
    const result = {
      valid: false,
      validFiles: 2,
      invalidFiles: 1,
      warnings: ['candidate warning']
    }
    expect(summarizeLifecycle(result)).toMatchObject({
      catalogState: 'CANDIDATE_INVALID',
      successCount: 2,
      failedCount: 1,
      hasIssues: true
    })
    expect(lifecycleDiagnosticRows(result)).toEqual([{ index: 0, value: 'candidate warning' }])
  })

  it('does not invent diagnostic rows for a clean result', () => {
    expect(lifecycleDiagnosticRows({ valid: true, warnings: [] })).toEqual([])
  })
})
