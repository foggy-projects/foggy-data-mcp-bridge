import { describe, expect, it } from 'vitest'
import {
  buildExportPayload,
  buildSavePayload,
  normalizeResourcePaths,
  resourcePathError
} from '@/features/bundles/bundleResources'

describe('Bundle resource operation payloads', () => {
  it('normalizes and deduplicates export paths', () => {
    expect(normalizeResourcePaths(` models/a.qm
models\\b.tm
models/a.qm `))
      .toEqual(['models/a.qm', 'models/b.tm'])
    expect(buildExportPayload('finance', 'bundle-a', '', false)).toEqual({
      namespace: 'finance',
      bundle: 'bundle-a',
      paths: [],
      includeContent: false
    })
  })

  it('matches Runtime resource path safety and file-type constraints', () => {
    expect(resourcePathError('models/orders.qm')).toBe('')
    expect(resourcePathError('model-list.yaml')).toBe('')
    expect(resourcePathError('../outside.qm')).toContain('目录穿越')
    expect(resourcePathError('/absolute/orders.qm')).toContain('相对于 Bundle')
    expect(resourcePathError('C:\\models\\orders.qm')).toContain('相对于 Bundle')
    expect(resourcePathError('models/readme.md')).toContain('仅支持')
  })

  it('builds save payloads without pretending validate or refresh already run', () => {
    expect(buildSavePayload('default', 'bundle-a', [{
      id: 1,
      path: ' models/orders.qm ',
      content: 'query Order {}',
      baseSha256: ' '
    }])).toEqual({
      namespace: 'default',
      bundle: 'bundle-a',
      files: [{
        path: 'models/orders.qm',
        content: 'query Order {}',
        baseSha256: null
      }],
      validate: false,
      refresh: false
    })
  })
})
