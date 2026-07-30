import { normalizeResultRows } from '@/utils/json'
import type { LifecycleResult } from '@/features/namespace/types'

export interface LifecycleSummary {
  catalogState: string
  beforeGeneration: string
  afterGeneration: string
  successCount: number
  failedCount: number
  durationMs?: number
  hasIssues: boolean
}

export function summarizeLifecycle(result: LifecycleResult): LifecycleSummary {
  const failedCount = result.failedCount ?? result.invalidFiles ?? 0
  const successCount = result.refreshedCount ?? result.loadedCount ?? result.validFiles ?? 0
  return {
    catalogState: result.catalogState || (result.valid === false ? 'CANDIDATE_INVALID' : 'COMPLETED'),
    beforeGeneration: result.beforeCatalogGeneration || '',
    afterGeneration: result.afterCatalogGeneration || '',
    successCount,
    failedCount,
    durationMs: result.durationMs,
    hasIssues: failedCount > 0
      || Boolean(result.errors?.length)
      || Boolean(result.failures?.length)
      || Boolean(result.warnings?.length)
  }
}

export function lifecycleDiagnosticRows(result: LifecycleResult): Record<string, unknown>[] {
  const detail = result.errors?.length
    ? result.errors
    : result.failures?.length
      ? result.failures
      : result.warnings?.length
        ? result.warnings
        : []
  return normalizeResultRows(detail)
}
