import { readFile } from 'node:fs/promises'
import { expect, test, type Page, type Route } from '@playwright/test'

interface MockState {
  capabilities: Record<string, string>
  datasources: Array<Record<string, unknown>>
  namespaceBindings: Record<string, string>
  bundles: Array<Record<string, unknown>>
  namespaceHeaders: string[]
  refreshScopes: string[]
  requests: Array<{
    path: string
    namespace: string
    body: Record<string, unknown>
  }>
  lifecycleMethods: string[]
  delayNextDefaultModels: boolean
  authoringWorkspaces: Array<Record<string, any>>
  authoringResources: Record<string, Array<Record<string, any>>>
  conflictNextWorkspaceSave: boolean
  invalidNextWorkspaceValidation: boolean
  authoringRevisionSequence: number
  failNextWorkspacePublish: boolean
  returnPublishingNextWorkspacePublish: boolean
  completePublishingOnNextGet: boolean
  lifecycleBlocked: boolean
  failNextLifecycle: boolean
}

const acceptedToken = 'e2e-runtime-token'
const mockStates = new WeakMap<Page, MockState>()

function envelope(data: unknown) {
  return {
    success: true,
    engine: 'java',
    runtimeApiVersion: 'foggy-runtime-api/v1',
    data
  }
}

function releasePackageFixture() {
  return {
    formatVersion: 'foggy-authoring-release/v1',
    packageId: 'sha256:release-package-001',
    sourceRuntimeApiVersion: 'foggy-runtime-api/v1',
    sourceNamespace: 'development',
    sourceBundle: 'runtime-console-demo',
    candidateRevision: 'sha256:candidate-001',
    baseBundleRevision: 'sha256:development-base',
    baseNamespaceSourceRevision: 'source:development:g4',
    exportedAt: '2026-08-01T07:00:00Z',
    validation: {
      valid: true,
      candidateRevision: 'sha256:candidate-001',
      baseBundleRevision: 'sha256:development-base',
      baseNamespaceSourceRevision: 'source:development:g4',
      validatedAt: '2026-08-01T06:55:00Z',
      totalFiles: 3,
      validFiles: 3,
      invalidFiles: 0,
      cascadingErrors: 0,
      issues: []
    },
    dependencies: [{
      bundle: 'shared-models',
      sourceType: 'jar',
      sourceIdentity: 'sha256:dependency-001',
      artifactRevision: null
    }],
    resources: [
      { path: 'models/Order.tm', type: 'TM', size: 34, sha256: 'sha256:tm-001', content: 'export const Order = tableModel({})' },
      { path: 'query/OrderQuery.qm', type: 'QM', size: 51, sha256: 'sha256:qm-001', content: 'export const OrderQuery = queryModel({ source: Order })' },
      { path: 'scripts/order.fsscript', type: 'FSSCRIPT', size: 24, sha256: 'sha256:script-001', content: 'export const tax = 0.13' }
    ]
  }
}

function lifecycleFixture(blocked: boolean) {
  const baseObjects = [
    {
      store: 'WORKSPACE',
      type: 'WORKSPACE_REVISION',
      identity: 'workspace:ws-alpha:revision:sha256:alpha',
      status: 'OBSOLETE',
      bytes: 1024,
      referenceClass: 'PROVABLY_UNREACHABLE_CANDIDATE',
      references: [],
      blockedReason: null
    },
    {
      store: 'PUBLISHED',
      type: 'PUBLISHED_ARTIFACT',
      identity: 'artifact:attempt-alpha',
      status: 'VERIFIED',
      bytes: 4096,
      referenceClass: 'MUST_RETAIN',
      references: ['bundle:sales:managed-sales:current'],
      blockedReason: null
    },
    {
      store: 'LIVE_REGISTRY',
      type: 'BUNDLE_RECORD',
      identity: 'bundle:sales:managed-sales',
      status: 'ENABLED',
      bytes: 0,
      referenceClass: 'MUST_RETAIN',
      references: ['artifact:attempt-alpha'],
      blockedReason: null
    }
  ]
  const objects = blocked
    ? [...baseObjects, {
        store: 'PUBLISHED',
        type: 'PUBLICATION_METADATA_RECOVERY_PENDING',
        identity: 'attempt:attempt-beta:temporary',
        status: 'INTERRUPTED',
        bytes: 128,
        referenceClass: 'UNKNOWN_PRESERVE',
        references: [],
        blockedReason: 'PUBLICATION_METADATA_RECOVERY_PENDING'
      }]
    : baseObjects
  return {
    capturedAt: blocked ? '2026-08-01T08:05:00Z' : '2026-08-01T08:00:00Z',
    health: blocked ? 'BLOCKED' : 'HEALTHY',
    roots: [
      {
        store: 'WORKSPACE',
        health: 'HEALTHY',
        objectCount: 1,
        bytes: 1024,
        blockedReasons: []
      },
      {
        store: 'PUBLISHED',
        health: blocked ? 'BLOCKED' : 'HEALTHY',
        objectCount: blocked ? 3 : 2,
        bytes: blocked ? 4224 : 4096,
        blockedReasons: blocked ? ['PUBLICATION_METADATA_RECOVERY_PENDING'] : []
      }
    ],
    summary: {
      totalObjects: objects.length,
      totalBytes: blocked ? 5248 : 5120,
      mustRetain: 2,
      provablyUnreachableCandidates: 1,
      unknownPreserve: blocked ? 1 : 0,
      blockedObjects: blocked ? 1 : 0
    },
    objects,
    blockedReasons: blocked ? ['PUBLICATION_METADATA_RECOVERY_PENDING'] : []
  }
}

async function jsonBody(route: Route): Promise<Record<string, unknown>> {
  try {
    return route.request().postDataJSON() as Record<string, unknown>
  } catch {
    return {}
  }
}

async function mockRuntime(page: Page): Promise<MockState> {
  const state: MockState = {
    capabilities: {
      'runtime.accessCheck': 'available',
      'query.execute': 'available',
      'datasources.manage': 'available',
      'authoring.releasePackage.export': 'supported',
      'authoring.releasePackage.import': 'disabled',
      'authoring.production.apply': 'disabled',
      'authoring.production.rollback': 'disabled',
      'authoring.artifacts.lifecycleInventory': 'supported'
    },
    datasources: [{
      name: 'analytics',
      type: 'mysql',
      jdbcUrl: 'jdbc:mysql://db.internal:3306/analytics',
      enabled: true,
      source: 'runtime',
      status: 'READY',
      canUpdate: true,
      canRemove: true,
      canTest: true
    }],
    namespaceBindings: { default: 'analytics', finance: 'analytics' },
    bundles: [
      {
        name: 'runtime-console-demo',
        namespace: 'default',
        path: '/runtime/models/demo',
        watch: true,
        enabled: true,
        source: 'runtime-registry',
        status: 'active',
        canUpdate: true,
        canRemove: true,
        sourceType: 'runtime-managed',
        editable: true,
        workspaceEligible: true,
        namespaceBindings: ['default'],
        sourceIdentity: 'source:runtime-console-demo'
      },
      {
        name: 'finance-models',
        namespace: 'finance',
        path: '/runtime/models/finance',
        watch: false,
        enabled: true,
        source: 'runtime-registry',
        status: 'ready',
        canUpdate: true,
        canRemove: true,
        sourceType: 'runtime-managed',
        editable: true,
        workspaceEligible: true,
        namespaceBindings: ['finance'],
        sourceIdentity: 'source:finance-models'
      }
    ],
    namespaceHeaders: [],
    refreshScopes: [],
    requests: [],
    lifecycleMethods: [],
    delayNextDefaultModels: false,
    authoringWorkspaces: [{
      workspaceId: 'ws-default-001',
      targetNamespace: 'default',
      sourceBundle: 'runtime-console-demo',
      sourceKind: 'runtime-managed',
      baseBundleRevision: 'sha256:base-bundle-001',
      baseNamespaceSourceRevision: 'source:default:g1',
      candidateRevision: 'sha256:candidate-001',
      state: 'DRAFT',
      createdAt: '2026-08-01T01:00:00Z',
      updatedAt: '2026-08-01T01:00:00Z',
      lastValidation: null,
      diagnostics: []
    }],
    authoringResources: {
      'ws-default-001': [
        {
          path: 'models/Order.tm',
          type: 'TM',
          size: 34,
          sha256: 'sha256:tm-001',
          content: 'export const Order = tableModel({})'
        },
        {
          path: 'query/OrderQuery.qm',
          type: 'QM',
          size: 51,
          sha256: 'sha256:qm-001',
          content: 'export const OrderQuery = queryModel({ source: Order })'
        },
        {
          path: 'scripts/order.fsscript',
          type: 'FSSCRIPT',
          size: 24,
          sha256: 'sha256:script-001',
          content: 'export const tax = 0.13'
        }
      ]
    },
    conflictNextWorkspaceSave: false,
    invalidNextWorkspaceValidation: false,
    authoringRevisionSequence: 1,
    failNextWorkspacePublish: false,
    returnPublishingNextWorkspacePublish: false,
    completePublishingOnNextGet: false,
    lifecycleBlocked: false,
    failNextLifecycle: false
  }

  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^.*\/api\/v1\//, '')
    const token = request.headers()['x-foggy-runtime-code']

    if (path === 'access/check') {
      if (token !== acceptedToken) {
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({
            success: false,
            error: { code: 'RUNTIME_AUTH_REQUIRED', message: 'Runtime Token 无效。' }
          })
        })
        return
      }
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(envelope({
          authenticated: true,
          authScope: 'management-all',
          runtimeApiVersion: 'foggy-runtime-api/v1'
        }))
      })
      return
    }

    if (token !== acceptedToken) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          error: { code: 'RUNTIME_AUTH_REQUIRED', message: '需要 Runtime Token。' }
        })
      })
      return
    }
    const requestNamespace = request.headers()['x-ns'] || ''
    const requestBody = request.method() === 'GET' ? {} : await jsonBody(route)
    state.namespaceHeaders.push(requestNamespace)
    state.requests.push({
      path,
      namespace: requestNamespace,
      body: requestBody
    })

    let data: unknown = {}
    if (path === 'capabilities') {
      data = {
        engine: 'java',
        runtimeApiVersion: 'foggy-runtime-api/v1',
        schemaVersion: 'v1',
        enabled: true,
        securityMode: 'auth-code',
        capabilities: {
          ...state.capabilities
        },
        warnings: []
      }
    } else if (path === 'authoring/artifacts/lifecycle') {
      state.lifecycleMethods.push(request.method())
      if (state.failNextLifecycle) {
        state.failNextLifecycle = false
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: false,
            error: {
              code: 'RUNTIME_ARTIFACT_LIFECYCLE_UNAVAILABLE',
              phase: 'runtime.artifacts.lifecycle.inventory',
              message: 'Lifecycle inventory is temporarily unavailable.',
              safeToAutoRepair: false
            }
          })
        })
        return
      }
      data = lifecycleFixture(state.lifecycleBlocked)
    } else if (/^authoring\/workspaces\/[^/]+\/release-package$/.test(path)) {
      data = releasePackageFixture()
    } else if (path === 'authoring/releases/import' && request.method() === 'POST') {
      const release = requestBody.releasePackage as Record<string, any>
      const workspaceId = `ws-imported-${state.authoringWorkspaces.length + 1}`
      const imported = {
        workspaceId,
        targetNamespace: String(requestBody.namespace || requestNamespace),
        sourceBundle: String(requestBody.targetBundle || ''),
        sourceKind: 'runtime-managed',
        baseBundleRevision: 'sha256:production-base-001',
        baseNamespaceSourceRevision: 'source:production:g1',
        candidateRevision: String(release.candidateRevision || ''),
        state: 'DRAFT',
        createdAt: '2026-08-01T07:10:00Z',
        updatedAt: '2026-08-01T07:10:00Z',
        lastValidation: null,
        lastPublication: null,
        releaseImport: {
          packageId: release.packageId,
          formatVersion: release.formatVersion,
          sourceRuntimeApiVersion: release.sourceRuntimeApiVersion,
          sourceNamespace: release.sourceNamespace,
          sourceBundle: release.sourceBundle,
          exportedCandidateRevision: release.candidateRevision,
          importedAt: '2026-08-01T07:10:00Z'
        },
        diagnostics: []
      }
      state.authoringWorkspaces.push(imported)
      state.authoringResources[workspaceId] = (release.resources || []).map((item: Record<string, any>) => ({ ...item }))
      data = imported
    } else if (/^authoring\/workspaces\/[^/]+\/promote$/.test(path)) {
      const workspaceId = decodeURIComponent(path.split('/')[2] || '')
      const workspace = state.authoringWorkspaces.find(item => item.workspaceId === workspaceId)!
      workspace.state = 'PUBLISHED'
      workspace.updatedAt = '2026-08-01T07:20:00Z'
      workspace.lastPublication = {
        attemptId: 'promotion-attempt-001',
        status: 'PUBLISHED',
        candidateRevision: workspace.candidateRevision,
        baseBundleRevision: workspace.baseBundleRevision,
        appliedBundleRevision: workspace.candidateRevision,
        baseNamespaceSourceRevision: workspace.baseNamespaceSourceRevision,
        publishedNamespaceSourceRevision: 'source:production:g2',
        beforeCatalogGeneration: 'catalog:production:g1',
        afterCatalogGeneration: 'catalog:production:g2',
        recoveredCatalogGeneration: null,
        startedAt: '2026-08-01T07:19:00Z',
        completedAt: '2026-08-01T07:20:00Z',
        diagnostics: ['Exact imported package is live.'],
        rollback: null
      }
      data = workspace
    } else if (/^authoring\/workspaces\/[^/]+\/rollback\/recover$/.test(path)) {
      const workspaceId = decodeURIComponent(path.split('/')[2] || '')
      const workspace = state.authoringWorkspaces.find(item => item.workspaceId === workspaceId)!
      workspace.state = 'PUBLISHED'
      workspace.lastPublication.rollback = {
        status: 'FORWARD_RECOVERED',
        startedAt: '2026-08-01T07:21:00Z',
        completedAt: '2026-08-01T07:22:00Z',
        forwardRecoveredNamespaceSourceRevision: 'source:production:g2',
        forwardRecoveredCatalogGeneration: 'catalog:production:g2',
        diagnostics: ['Pinned candidate was forward recovered.']
      }
      data = workspace
    } else if (/^authoring\/workspaces\/[^/]+\/rollback$/.test(path)) {
      const workspaceId = decodeURIComponent(path.split('/')[2] || '')
      const workspace = state.authoringWorkspaces.find(item => item.workspaceId === workspaceId)!
      workspace.state = 'ROLLED_BACK'
      workspace.updatedAt = '2026-08-01T07:25:00Z'
      workspace.lastPublication.rollback = {
        status: 'ROLLED_BACK',
        startedAt: '2026-08-01T07:24:00Z',
        completedAt: '2026-08-01T07:25:00Z',
        rolledBackNamespaceSourceRevision: workspace.baseNamespaceSourceRevision,
        rolledBackCatalogGeneration: 'catalog:production:g1-restored',
        diagnostics: ['Direct previous production base was restored.']
      }
      data = workspace
    } else if (path === 'authoring/workspaces' && request.method() === 'GET') {
      const namespace = url.searchParams.get('namespace') || ''
      data = {
        workspaces: state.authoringWorkspaces.filter(item => item.targetNamespace === namespace && item.state !== 'DISCARDED'),
        warnings: []
      }
    } else if (path === 'authoring/workspaces' && request.method() === 'POST') {
      const workspaceId = `ws-created-${state.authoringWorkspaces.length + 1}`
      const created = {
        workspaceId,
        targetNamespace: String(requestBody.namespace || requestNamespace),
        sourceBundle: String(requestBody.sourceBundle || ''),
        sourceKind: 'runtime-managed',
        baseBundleRevision: 'sha256:base-created',
        baseNamespaceSourceRevision: 'source:created:g1',
        candidateRevision: 'sha256:candidate-created',
        state: 'DRAFT',
        createdAt: '2026-08-01T02:00:00Z',
        updatedAt: '2026-08-01T02:00:00Z',
        lastValidation: null,
        diagnostics: []
      }
      state.authoringWorkspaces.push(created)
      state.authoringResources[workspaceId] = []
      data = created
    } else if (/^authoring\/workspaces\/[^/]+$/.test(path) && request.method() === 'GET') {
      const workspaceId = decodeURIComponent(path.split('/')[2] || '')
      const workspace = state.authoringWorkspaces.find(item => item.workspaceId === workspaceId)
      if (workspace?.state === 'PUBLISHING' && state.completePublishingOnNextGet) {
        state.completePublishingOnNextGet = false
        workspace.state = 'PUBLISHED'
        workspace.updatedAt = '2026-08-01T06:02:00Z'
        Object.assign(workspace.lastPublication, {
          status: 'PUBLISHED',
          appliedBundleRevision: workspace.candidateRevision,
          publishedNamespaceSourceRevision: 'source:default:g2',
          afterCatalogGeneration: 'catalog:g2',
          completedAt: '2026-08-01T06:02:00Z',
          diagnostics: ['Immutable candidate artifact is live.']
        })
      }
      data = workspace
    } else if (/^authoring\/workspaces\/[^/]+$/.test(path) && request.method() === 'DELETE') {
      const workspaceId = decodeURIComponent(path.split('/')[2] || '')
      const workspace = state.authoringWorkspaces.find(item => item.workspaceId === workspaceId)!
      workspace.state = 'DISCARDED'
      workspace.updatedAt = '2026-08-01T03:00:00Z'
      data = workspace
    } else if (/^authoring\/workspaces\/[^/]+\/resources$/.test(path) && request.method() === 'GET') {
      const workspaceId = decodeURIComponent(path.split('/')[2] || '')
      const workspace = state.authoringWorkspaces.find(item => item.workspaceId === workspaceId)!
      data = {
        workspaceId,
        candidateRevision: workspace.candidateRevision,
        resources: (state.authoringResources[workspaceId] || []).map(({ content: _content, ...resource }) => resource)
      }
    } else if (/^authoring\/workspaces\/[^/]+\/resources\/content$/.test(path)) {
      const workspaceId = decodeURIComponent(path.split('/')[2] || '')
      const resourcePath = url.searchParams.get('path') || ''
      data = state.authoringResources[workspaceId]?.find(item => item.path === resourcePath)
    } else if (/^authoring\/workspaces\/[^/]+\/resources\/save$/.test(path)) {
      const workspaceId = decodeURIComponent(path.split('/')[2] || '')
      const workspace = state.authoringWorkspaces.find(item => item.workspaceId === workspaceId)!
      if (state.conflictNextWorkspaceSave) {
        state.conflictNextWorkspaceSave = false
        workspace.candidateRevision = 'sha256:candidate-server-conflict'
        workspace.state = 'DRAFT'
        const conflictResource = state.authoringResources[workspaceId]?.find(item => item.path === 'query/OrderQuery.qm')
        if (conflictResource) conflictResource.content = 'export const OrderQuery = queryModel({ source: ServerOrder })'
        await route.fulfill({
          status: 409,
          contentType: 'application/json',
          body: JSON.stringify({
            success: false,
            error: {
              code: 'WORKSPACE_REVISION_CONFLICT',
              phase: 'workspaces.resources.save',
              message: 'Workspace candidate revision is no longer current.',
              suggestedNextAction: 'Refresh workspace metadata and retry with the current revision.',
              safeToAutoRepair: true
            }
          })
        })
        return
      }
      const files = Array.isArray(requestBody.files) ? requestBody.files as Array<Record<string, unknown>> : []
      for (const file of files) {
        const resourcePath = String(file.path || '')
        const content = String(file.content || '')
        const existing = state.authoringResources[workspaceId]?.find(item => item.path === resourcePath)
        const type = resourcePath.endsWith('.tm') ? 'TM' : resourcePath.endsWith('.qm') ? 'QM' : 'FSSCRIPT'
        const resource = { path: resourcePath, type, size: content.length, sha256: `sha256:saved-${state.authoringRevisionSequence}`, content }
        if (existing) Object.assign(existing, resource)
        else state.authoringResources[workspaceId].push(resource)
      }
      workspace.candidateRevision = `sha256:candidate-00${++state.authoringRevisionSequence}`
      workspace.state = 'DRAFT'
      workspace.lastValidation = null
      workspace.updatedAt = '2026-08-01T04:00:00Z'
      data = workspace
    } else if (/^authoring\/workspaces\/[^/]+\/resources\/delete$/.test(path)) {
      const workspaceId = decodeURIComponent(path.split('/')[2] || '')
      const workspace = state.authoringWorkspaces.find(item => item.workspaceId === workspaceId)!
      const paths = Array.isArray(requestBody.paths) ? requestBody.paths : []
      state.authoringResources[workspaceId] = (state.authoringResources[workspaceId] || [])
        .filter(item => !paths.includes(item.path))
      workspace.candidateRevision = `sha256:candidate-00${++state.authoringRevisionSequence}`
      workspace.state = 'DRAFT'
      workspace.lastValidation = null
      data = workspace
    } else if (/^authoring\/workspaces\/[^/]+\/diff$/.test(path)) {
      const workspaceId = decodeURIComponent(path.split('/')[2] || '')
      const workspace = state.authoringWorkspaces.find(item => item.workspaceId === workspaceId)!
      const qm = state.authoringResources[workspaceId]?.find(item => item.path === 'query/OrderQuery.qm')
      data = {
        workspaceId,
        baseBundleRevision: workspace.baseBundleRevision,
        candidateRevision: workspace.candidateRevision,
        changes: [{
          path: 'query/OrderQuery.qm',
          type: 'QM',
          changeType: 'MODIFIED',
          baseSha256: 'sha256:qm-001',
          candidateSha256: qm?.sha256,
          baseContent: 'export const OrderQuery = queryModel({ source: Order })',
          candidateContent: qm?.content
        }]
      }
    } else if (/^authoring\/workspaces\/[^/]+\/validate$/.test(path)) {
      const workspaceId = decodeURIComponent(path.split('/')[2] || '')
      const workspace = state.authoringWorkspaces.find(item => item.workspaceId === workspaceId)!
      if (state.invalidNextWorkspaceValidation) {
        state.invalidNextWorkspaceValidation = false
        workspace.state = 'DRAFT'
        workspace.lastValidation = {
          valid: false,
          candidateRevision: workspace.candidateRevision,
          baseBundleRevision: workspace.baseBundleRevision,
          baseNamespaceSourceRevision: workspace.baseNamespaceSourceRevision,
          validatedAt: '2026-08-01T04:30:00Z',
          totalFiles: state.authoringResources[workspaceId]?.length || 0,
          validFiles: Math.max(0, (state.authoringResources[workspaceId]?.length || 0) - 1),
          invalidFiles: 1,
          cascadingErrors: 0,
          issues: [{
            path: 'query/OrderQuery.qm',
            type: 'QM',
            code: 'WORKSPACE_VALIDATION_FAILED',
            message: 'Candidate QM syntax is invalid.',
            category: 'PRIMARY'
          }]
        }
        await route.fulfill({
          status: 422,
          contentType: 'application/json',
          body: JSON.stringify({
            success: false,
            error: {
              code: 'WORKSPACE_VALIDATION_FAILED',
              phase: 'workspaces.validate',
              message: 'Workspace model validation failed.',
              suggestedNextAction: 'Inspect workspace diagnostics and retry.',
              safeToAutoRepair: true
            }
          })
        })
        return
      }
      workspace.state = 'VALIDATED'
      workspace.lastValidation = {
        valid: true,
        candidateRevision: workspace.candidateRevision,
        baseBundleRevision: workspace.baseBundleRevision,
        baseNamespaceSourceRevision: workspace.baseNamespaceSourceRevision,
        validatedAt: '2026-08-01T05:00:00Z',
        totalFiles: state.authoringResources[workspaceId]?.length || 0,
        validFiles: state.authoringResources[workspaceId]?.length || 0,
        invalidFiles: 0,
        cascadingErrors: 0,
        issues: []
      }
      data = workspace
    } else if (/^authoring\/workspaces\/[^/]+\/publish$/.test(path)) {
      const workspaceId = decodeURIComponent(path.split('/')[2] || '')
      const workspace = state.authoringWorkspaces.find(item => item.workspaceId === workspaceId)!
      const evidence = {
        attemptId: 'publication-attempt-001',
        status: 'PUBLISHING',
        candidateRevision: workspace.candidateRevision,
        baseBundleRevision: workspace.baseBundleRevision,
        appliedBundleRevision: null,
        baseNamespaceSourceRevision: workspace.baseNamespaceSourceRevision,
        publishedNamespaceSourceRevision: null,
        beforeCatalogGeneration: 'catalog:g1',
        afterCatalogGeneration: null,
        recoveredCatalogGeneration: null,
        startedAt: '2026-08-01T06:00:00Z',
        completedAt: null,
        diagnostics: []
      }
      workspace.lastPublication = evidence
      workspace.updatedAt = '2026-08-01T06:00:00Z'
      if (state.failNextWorkspacePublish) {
        state.failNextWorkspacePublish = false
        workspace.state = 'RECOVERY_REQUIRED'
        workspace.lastPublication = {
          ...evidence,
          status: 'RECOVERY_REQUIRED',
          diagnostics: ['Catalog refresh did not converge; exact recovery is required.']
        }
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({
            success: false,
            error: {
              code: 'WORKSPACE_RECOVERY_REQUIRED',
              phase: 'workspaces.publish.recovery',
              message: 'Publication requires explicit recovery.',
              suggestedNextAction: 'Refresh workspace metadata and recover the exact publication attempt.',
              safeToAutoRepair: false
            }
          })
        })
        return
      }
      if (state.returnPublishingNextWorkspacePublish) {
        state.returnPublishingNextWorkspacePublish = false
        state.completePublishingOnNextGet = true
        workspace.state = 'PUBLISHING'
      } else {
        workspace.state = 'PUBLISHED'
        workspace.lastPublication = {
          ...evidence,
          status: 'PUBLISHED',
          appliedBundleRevision: workspace.candidateRevision,
          publishedNamespaceSourceRevision: 'source:default:g2',
          afterCatalogGeneration: 'catalog:g2',
          completedAt: '2026-08-01T06:02:00Z',
          diagnostics: ['Immutable candidate artifact is live.']
        }
      }
      data = workspace
    } else if (/^authoring\/workspaces\/[^/]+\/publish\/recover$/.test(path)) {
      const workspaceId = decodeURIComponent(path.split('/')[2] || '')
      const workspace = state.authoringWorkspaces.find(item => item.workspaceId === workspaceId)!
      workspace.state = 'STALE'
      workspace.updatedAt = '2026-08-01T06:05:00Z'
      workspace.lastPublication = {
        ...workspace.lastPublication,
        status: 'RECOVERED',
        recoveredCatalogGeneration: 'catalog:g1-recovered',
        completedAt: '2026-08-01T06:05:00Z',
        diagnostics: ['Prior live source and catalog were restored.']
      }
      data = workspace
    } else if (/^authoring\/workspaces\/[^/]+\/query\/[^/]+\/(validate|execute)$/.test(path)) {
      const workspaceId = decodeURIComponent(path.split('/')[2] || '')
      const workspace = state.authoringWorkspaces.find(item => item.workspaceId === workspaceId)!
      const phase = path.endsWith('/validate') ? 'VALIDATE' : 'EXECUTE'
      data = {
        workspaceId,
        sourceBundle: workspace.sourceBundle,
        namespace: workspace.targetNamespace,
        baseBundleRevision: workspace.baseBundleRevision,
        baseNamespaceSourceRevision: workspace.baseNamespaceSourceRevision,
        candidateRevision: workspace.candidateRevision,
        catalogIdentity: { namespace: workspace.targetNamespace, generation: 'candidate-g1' },
        phase,
        response: {
          items: phase === 'EXECUTE'
            ? [{ customer: 'Candidate Alice', amount: 188, note: '=HYPERLINK("bad")' }]
            : [],
          total: phase === 'EXECUTE' ? 1 : 0,
          hasNext: false,
          execution: { provider: 'JDBC', status: 'EXECUTED', durationMs: 9 },
          warnings: []
        },
        diagnostics: []
      }
    } else if (path === 'datasources/diagnostics') {
      data = {
        datasources: state.datasources,
        registryEnabled: true,
        registryExists: true,
        managedDatasourceCount: state.datasources.length,
        namespaceBindings: state.namespaceBindings
      }
    } else if (path === 'datasources' && request.method() === 'GET') {
      data = { datasources: state.datasources, warnings: [] }
    } else if (path === 'datasources' && request.method() === 'POST') {
      const body = requestBody
      state.datasources.push({
        ...body,
        enabled: true,
        source: 'runtime',
        status: 'READY',
        canUpdate: true,
        canRemove: true,
        canTest: true
      })
      data = { name: body.name, created: true }
    } else if (/^namespaces\/[^/]+\/datasource$/.test(path) && request.method() === 'GET') {
      const namespace = decodeURIComponent(path.split('/')[1] || '')
      data = { namespace, dataSource: state.namespaceBindings[namespace] }
    } else if (/^namespaces\/[^/]+\/datasource$/.test(path) && request.method() === 'PUT') {
      const body = requestBody
      const namespace = decodeURIComponent(path.split('/')[1] || '')
      state.namespaceBindings[namespace] = String(body.dataSource || '')
      data = { namespace, dataSource: state.namespaceBindings[namespace] }
    } else if (path === 'models') {
      if (requestNamespace === 'default' && state.delayNextDefaultModels) {
        state.delayNextDefaultModels = false
        await new Promise(resolve => setTimeout(resolve, 350))
      }
      const modelName = requestNamespace === 'finance'
        ? 'FinanceModel'
        : requestNamespace
          ? 'OrderModel'
          : 'EmptySpaceModel'
      data = {
        format: 'json',
        content: '{}',
        data: {
          models: [modelName],
          count: 1,
          items: [{
            model: modelName,
            caption: requestNamespace === 'finance' ? '财务分析' : '订单分析',
            description: requestNamespace === 'finance' ? '用于财务汇总分析。' : '用于订单趋势与履约分析。',
            namespace: requestNamespace,
            sourceKnown: true,
            bundleName: requestNamespace === 'finance' ? 'finance-models' : 'runtime-console-demo',
            sourceNamespace: requestNamespace,
            resourceIdentity: `qm:${modelName}`,
            physicalTables: [requestNamespace === 'finance' ? 'finance.invoices' : 'public.orders'],
            fieldCount: 12,
            primaryTimeField: 'createdAt'
          }]
        }
      }
    } else if (/^models\/[^/]+\/describe$/.test(path)) {
      data = {
        format: 'json',
        content: '{}',
        data: {
          version: 'v3',
          models: {
            OrderModel: {
              name: '订单分析',
              factTable: 'orders',
              purpose: '订单经营分析',
              scenarios: ['趋势分析', '履约监控']
            }
          },
          fields: {
            customer: {
              name: '客户',
              fieldName: 'customer',
              type: 'TEXT',
              measure: false,
              filterable: true,
              sourceColumn: 'customer_name',
              models: {
                OrderModel: {
                  description: '客户显示名称',
                  usage: '用于筛选与分组'
                }
              }
            },
            amount: {
              name: '订单金额',
              fieldName: 'amount',
              type: 'DECIMAL',
              measure: true,
              aggregation: 'SUM',
              sourceColumn: 'pay_amount',
              models: {
                OrderModel: { description: '订单实付金额' }
              }
            },
            margin: {
              name: '订单毛利',
              fieldName: 'margin',
              type: 'DECIMAL',
              calculated: true,
              description: '收入减去成本'
            }
          },
          physicalTables: [{ table: 'public.orders', role: 'fact' }],
          examples: [{ columns: ['customer', 'amount'] }],
          modelSource: {
            known: true,
            bundleName: requestNamespace === 'finance' ? 'finance-models' : 'runtime-console-demo',
            namespace: requestNamespace,
            resourceIdentity: 'qm:OrderModel'
          }
        }
      }
    } else if (path === 'models/refresh') {
      const body = requestBody
      const models = Array.isArray(body.models) ? body.models : []
      state.refreshScopes.push(models.length ? 'selected' : 'all')
      data = {
        catalogState: 'PUBLISHED',
        beforeCatalogGeneration: 'g-1',
        afterCatalogGeneration: 'g-2',
        refreshedCount: models.length || 1,
        failedCount: 0,
        durationMs: 42,
        warnings: []
      }
    } else if (path === 'models/validate') {
      data = {
        valid: true,
        catalogState: 'CANDIDATE_VALID',
        validFiles: 2,
        invalidFiles: 0,
        durationMs: 18,
        warnings: []
      }
    } else if (path === 'resources/export') {
      data = {
        namespace: requestNamespace,
        bundle: requestBody.bundle,
        rootPath: '/runtime/models/demo',
        resources: [{
          path: 'models/orders.qm',
          type: 'QM',
          size: 128,
          sha256: 'mock-sha256',
          writable: true
        }],
        warnings: []
      }
    } else if (path === 'resources/save') {
      data = {
        namespace: requestNamespace,
        bundle: requestBody.bundle,
        rootPath: '/runtime/models/demo',
        savedCount: Array.isArray(requestBody.files) ? requestBody.files.length : 0,
        savedResources: [{
          path: 'models/new-orders.qm',
          type: 'QM',
          size: 96,
          sha256: 'saved-mock-sha256',
          writable: true
        }],
        warnings: ['保存完成；模型校验与刷新尚未执行。']
      }
    } else if (/^query\/[^/]+\/execute$/.test(path)) {
      data = {
        items: requestNamespace === 'finance'
          ? [{ ledger: 'Revenue', amount: 512 }]
          : requestNamespace
            ? [
                { customer: 'Alice', amount: 128.5 },
                { customer: 'Bob', amount: 96 }
              ]
            : [{ scope: 'empty', amount: 0 }],
        total: requestNamespace === 'default' ? 2 : 1,
        hasNext: false,
        pagination: { start: 0, limit: 100 },
        execution: { provider: 'JDBC', durationMs: 12 },
        warnings: []
      }
    } else if (/^query\/[^/]+\/validate$/.test(path)) {
      data = {
        items: [],
        total: 0,
        hasNext: false,
        pagination: { start: 0, limit: 100 },
        warnings: [],
        execution: { status: 'PLAN_READY', durationMs: 4 }
      }
    } else if (path === 'tables/list') {
      data = {
        dataSource: requestNamespace === 'finance' ? 'analytics' : 'analytics',
        tables: requestNamespace === 'finance'
          ? [{ schema: 'finance', name: 'invoices', type: 'TABLE' }]
          : requestNamespace
            ? [{ schema: 'public', name: 'orders', type: 'TABLE' }]
            : [{ schema: 'system', name: 'health', type: 'VIEW' }],
        warnings: []
      }
    } else if (path === 'tables/inspect') {
      const primaryColumn = requestNamespace === 'finance' ? 'invoice_id' : 'order_id'
      data = {
        dataSource: requestBody.dataSource || 'analytics',
        schema: requestBody.schema,
        table: requestBody.table,
        tableType: 'TABLE',
        columns: [{
          name: primaryColumn,
          jdbcType: 'BIGINT',
          jdbcTypeCode: -5,
          nullable: false,
          ordinalPosition: 1
        }],
        primaryKey: {
          name: requestNamespace === 'finance' ? 'pk_invoices' : 'pk_orders',
          columns: [primaryColumn]
        },
        indexes: [],
        foreignKeys: []
      }
    } else if (path === 'sql/query') {
      data = {
        rows: [{ namespace: requestNamespace || 'empty', runtime_ok: 1 }],
        rowCount: 1,
        truncated: false,
        warnings: [],
        columns: ['namespace', 'runtime_ok']
      }
    } else if (/^compose\/(validate|preview|execute)$/.test(path)) {
      data = {
        valid: true,
        scriptKind: 'COMPOSE',
        mode: path.split('/')[1],
        value: [{ namespace: requestNamespace || 'empty', composed: true }],
        sql: 'SELECT 1',
        params: [],
        warnings: [],
        diagnostics: { namespace: requestNamespace || 'empty' }
      }
    } else if (path === 'fsscript/execute') {
      data = {
        valid: true,
        scriptKind: 'FSSCRIPT',
        mode: 'execute',
        value: [{ namespace: requestNamespace || 'empty', executed: true }],
        warnings: []
      }
    } else if (path === 'bundles' && request.method() === 'GET') {
      data = { bundles: state.bundles, warnings: [] }
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(data))
    })
  })
  return state
}

async function login(page: Page): Promise<void> {
  await page.goto('/console/')
  await page.getByLabel('Runtime API Token').fill(acceptedToken)
  await page.getByRole('button', { name: '校验并进入 Console' }).click()
  await expect(page.getByRole('heading', { name: '运行概览' })).toBeVisible()
}

async function switchNamespace(page: Page, namespace: string): Promise<void> {
  const input = page.getByLabel('当前数据与模型空间')
  await input.fill(namespace)
  await input.press('Enter')
  await input.blur()
  await expect(input).toHaveValue(namespace)
}

test.beforeEach(async ({ page }) => {
  mockStates.set(page, await mockRuntime(page))
})

test('invalid login, valid login, reload revalidation and logout', async ({ page }) => {
  const errors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error' && !message.text().includes('401 (Unauthorized)')) {
      errors.push(message.text())
    }
  })

  await page.goto('/console/')
  await page.getByLabel('Runtime API Token').fill('invalid-candidate')
  await page.getByRole('button', { name: '校验并进入 Console' }).click()
  await expect(page.getByRole('alert')).toContainText('Runtime Token 无效')
  await expect(page.getByLabel('Runtime API Token')).toHaveValue('')

  await page.getByLabel('Runtime API Token').fill(acceptedToken)
  await page.getByRole('button', { name: '校验并进入 Console' }).click()
  await expect(page.getByRole('heading', { name: '运行概览' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '运行概览' })).toBeVisible()

  await page.getByRole('button', { name: '退出 Console' }).click()
  await expect(page.getByRole('heading', { name: '连接 Runtime' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('foggy.runtime-console.token'))).toBeNull()
  expect(errors).toEqual([])
})

test('navigation, datasource creation and query result rendering', async ({ page }, testInfo) => {
  await login(page)

  if (testInfo.project.name.includes('mobile')) {
    await page.getByRole('button', { name: '打开主导航' }).click()
    await page.getByRole('navigation', { name: '移动端 Runtime Console 主导航' })
      .getByRole('button', { name: /数据源/ })
      .click()
  } else {
    await page.getByRole('navigation', { name: 'Runtime Console 主导航' })
      .getByRole('button', { name: /数据源/ })
      .click()
  }
  await expect(page.getByRole('heading', { name: '数据源', exact: true })).toBeVisible()
  if (!testInfo.project.name.includes('mobile')) {
    const contextRail = page.getByRole('complementary', { name: '当前页面资源导航' })
    await expect(contextRail).toContainText('Datasource List')
  }

  await page.getByRole('button', { name: '新增数据源' }).click()
  const dialog = page.getByRole('dialog', { name: '新增数据源' })
  await dialog.locator('input').nth(0).fill('warehouse')
  await dialog.locator('input').nth(1).fill('jdbc:mysql://db.internal:3306/warehouse')
  await dialog.getByRole('button', { name: '保存数据源' }).click()
  await expect(page.getByRole('table').getByText('warehouse', { exact: true })).toBeVisible()

  if (testInfo.project.name.includes('mobile')) {
    await page.getByRole('button', { name: '打开主导航' }).click()
    await page.getByRole('navigation', { name: '移动端 Runtime Console 主导航' })
      .getByRole('button', { name: /数据与模型空间/ })
      .click()
  } else {
    const topNavigation = page.getByRole('navigation', { name: 'Runtime Console 主导航' })
    await expect(topNavigation.getByRole('button')).toHaveCount(7)
    await topNavigation.getByRole('button', { name: /数据与模型空间/ }).click()
  }
  await expect(page.getByRole('heading', { name: '数据与模型空间 · default' })).toBeVisible()
  if (testInfo.project.name.includes('mobile')) {
    await page.getByRole('button', { name: /资源列表 空间索引/ }).click()
    const resourceDrawer = page.getByRole('dialog', { name: '空间索引' })
    await expect(resourceDrawer.getByRole('button', { name: /default.*1 Bundle.*analytics.*CURRENT/ })).toBeVisible()
    await resourceDrawer.getByRole('button', { name: /default.*1 Bundle.*analytics.*CURRENT/ }).click()
  }
  await page.getByRole('button', { name: /^05 空间设置$/ }).click()
  await expect(page.getByLabel('默认数据源')).toHaveValue('analytics')
  await page.getByRole('button', { name: '保存默认绑定' }).click()
  await page.getByRole('dialog', { name: '确认空间默认数据源' })
    .getByRole('button', { name: '保存绑定' })
    .click()
  await expect(page.locator('.el-message').filter({ hasText: '空间默认数据源已更新' })).toBeVisible()
  await page.getByRole('button', { name: /^03 Bundle 来源/ }).click()
  await expect(page.getByRole('heading', { name: 'runtime-console-demo' })).toBeVisible()

  await page.goto('/console/#/query')
  await expect(page.getByRole('heading', { name: '查询 DSL' })).toBeVisible()
  await page.getByLabel('QM 模型').fill('OrderModel')
  await page.getByLabel('查询 DSL JSON').fill('{"columns":["customer","amount"]}')
  await page.getByRole('button', { name: '运行查询' }).click()
  await expect(page.getByText('Alice', { exact: true })).toBeVisible()
  await expect(page.getByText('Bob', { exact: true })).toBeVisible()

  await page.goto('/console/#/compose')
  await expect(page.getByRole('heading', { name: 'Compose / CTE' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: '执行工具类型' }).getByRole('button')).toHaveCount(2)
  await page.getByRole('navigation', { name: '执行工具类型' })
    .getByRole('button', { name: /FSScript/ })
    .click()
  await expect(page.getByRole('heading', { name: 'Fsscript', exact: true })).toBeVisible()
})

test('artifact lifecycle ledger stays global, read-only and operable across health states', async ({ page }, testInfo) => {
  const state = mockStates.get(page)!
  const browserErrors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error') browserErrors.push(message.text())
  })
  page.on('pageerror', error => browserErrors.push(error.message))
  await login(page)

  if (testInfo.project.name.includes('mobile')) {
    await page.getByRole('button', { name: '打开主导航' }).click()
    await page.getByRole('navigation', { name: '移动端 Runtime Console 主导航' })
      .getByRole('button', { name: /制品生命周期/ })
      .click()
  } else {
    await page.getByRole('navigation', { name: 'Runtime Console 主导航' })
      .getByRole('button', { name: /制品生命周期/ })
      .click()
  }

  await expect(page).toHaveURL(/#\/artifact-lifecycle$/)
  await expect(page.getByRole('heading', { name: '制品生命周期' })).toBeVisible()
  await expect(page.getByText('GLOBAL / READ ONLY')).toBeVisible()
  await expect(page.getByText('候选不是删除授权', { exact: true })).toBeVisible()
  await expect(page.getByText('健康', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('5.00 KiB total')).toHaveAttribute('title', '5,120 bytes')
  await expect(page.getByRole('heading', { name: 'WORKSPACE' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'PUBLISHED' })).toBeVisible()

  await page.getByLabel('按引用分类筛选').selectOption('PROVABLY_UNREACHABLE_CANDIDATE')
  await expect(page.getByText('workspace:ws-alpha:revision:sha256:alpha')).toBeVisible()
  await expect(page.getByText('artifact:attempt-alpha', { exact: true })).toBeHidden()
  await page.getByLabel('搜索生命周期对象').fill('ws-alpha')
  await expect(page.getByText('workspace:ws-alpha:revision:sha256:alpha')).toBeVisible()
  await page.getByRole('button', { name: '清除筛选' }).click()
  const retainedArtifactRow = page.getByTitle('artifact:attempt-alpha').locator('..').locator('..')
  await expect(retainedArtifactRow).toBeVisible()
  await retainedArtifactRow.getByText('查看证据').click()
  await expect(page.getByText('bundle:sales:managed-sales:current')).toBeVisible()

  state.lifecycleBlocked = true
  await page.getByRole('button', { name: '刷新证据' }).click()
  await expect(page.getByText('已阻断', { exact: true }).first()).toBeVisible()
  await page.locator('.blocked-register summary').click()
  await expect(page.locator('.blocked-register li'))
    .toHaveText('PUBLICATION_METADATA_RECOVERY_PENDING')
  await page.getByLabel('按阻断状态筛选').selectOption('BLOCKED')
  await expect(page.getByText('attempt:attempt-beta:temporary')).toBeVisible()
  await expect(page.getByText('artifact:attempt-alpha', { exact: true })).toBeHidden()

  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({
    path: testInfo.outputPath(testInfo.project.name.includes('mobile')
      ? 'artifact-lifecycle-mobile.png'
      : 'artifact-lifecycle-desktop.png'),
    fullPage: true
  })

  state.failNextLifecycle = true
  await page.getByRole('button', { name: '刷新证据' }).click()
  await expect(page.getByRole('alert')).toContainText('刷新失败，保留上一次快照')
  await expect(page.getByText('attempt:attempt-beta:temporary')).toBeVisible()

  const lifecycleRequests = state.requests.filter(item =>
    item.path === 'authoring/artifacts/lifecycle')
  expect(lifecycleRequests.length).toBeGreaterThanOrEqual(3)
  expect(state.lifecycleMethods.every(method => method === 'GET')).toBe(true)
  await expect(page.getByRole('button', { name: /cleanup|repair|delete|清理|修复|删除/i }))
    .toHaveCount(0)

  const inventoryRequestCount = lifecycleRequests.length
  state.capabilities['authoring.artifacts.lifecycleInventory'] = 'disabled'
  await page.getByRole('button', { name: '刷新证据' }).click()
  await expect(page.getByRole('heading', { name: '当前 Runtime 不支持生命周期清单' }))
    .toBeVisible()
  expect(state.requests.filter(item => item.path === 'authoring/artifacts/lifecycle'))
    .toHaveLength(inventoryRequestCount)
  expect(browserErrors).toEqual([])
})

test('namespace workspace keeps route, request scope, cards, drawers and keyboard focus aligned', async ({ page }, testInfo) => {
  testInfo.setTimeout(90_000)
  const state = mockStates.get(page)!
  const browserErrors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error') browserErrors.push(message.text())
  })
  page.on('pageerror', error => browserErrors.push(error.message))
  await login(page)

  if (testInfo.project.name.includes('mobile')) {
    await page.getByRole('button', { name: '打开主导航' }).click()
    await page.getByRole('navigation', { name: '移动端 Runtime Console 主导航' })
      .getByRole('button', { name: /数据与模型空间/ })
      .click()
  } else {
    await page.getByRole('navigation', { name: 'Runtime Console 主导航' })
      .getByRole('button', { name: /数据与模型空间/ })
      .click()
  }

  await expect(page).toHaveURL(/#\/namespaces\?ns=default$/)
  await expect(page.getByRole('heading', { name: '数据与模型空间 · default' })).toBeVisible()
  await expect(page.getByText('输入其他空间', { exact: true })).toHaveCount(0)
  await expect(page.getByText('DEFAULT DATASOURCE').locator('..')).toContainText('analytics')
  await expect(page.getByText('BUNDLE SOURCES').locator('..')).toContainText('1')
  await expect(page.getByText('VISIBLE QM').locator('..')).toContainText('1')

  await page.getByRole('button', { name: /分析模型（QM）/ }).click()
  await expect(page).toHaveURL(/#\/namespaces\/models\?ns=default$/)
  const modelCard = page.getByRole('article').filter({ hasText: 'OrderModel' })
  await expect(modelCard).toContainText('订单分析')
  await expect(modelCard).toContainText('12 fields')
  await expect(modelCard).toContainText('runtime-console-demo')
  await expect(modelCard).toContainText('createdAt')
  await expect(modelCard).toContainText('SOURCE KNOWN')

  const detailButton = modelCard.getByRole('button', { name: '查看详情' })
  await detailButton.click()
  const detailDrawer = page.getByRole('dialog', { name: /模型详情/ })
  await expect(detailDrawer).toContainText('public.orders')
  await expect(detailDrawer).toContainText('当前 Runtime API 未提供 typed 模型依赖')
  await expect(detailDrawer).toContainText('"amount"')
  await expect(detailDrawer.getByLabel('模型详情摘要')).toContainText('3')
  await expect(detailDrawer.getByText('订单经营分析', { exact: true })).toBeVisible()
  await expect(detailDrawer.getByText('趋势分析', { exact: true })).toBeVisible()
  await expect(detailDrawer.getByText('fact', { exact: true })).toBeVisible()
  await detailDrawer.getByRole('button', { name: '度量', exact: true }).click()
  await expect(detailDrawer.getByText('订单金额', { exact: true })).toBeVisible()
  await expect(detailDrawer.getByText('客户', { exact: true })).toBeHidden()
  await detailDrawer.getByRole('button', { name: '全部', exact: true }).click()
  await detailDrawer.getByLabel('搜索字段').fill('毛利')
  await expect(detailDrawer.getByText('订单毛利', { exact: true })).toBeVisible()
  await expect(detailDrawer.getByText('订单金额', { exact: true })).toBeHidden()
  await expect(detailDrawer.getByText('Runtime 原始模型 JSON')).toBeVisible()
  if (testInfo.project.name.includes('mobile')) {
    const box = await detailDrawer.boundingBox()
    expect(box?.width).toBeLessThanOrEqual(420)
  }
  await page.screenshot({
    path: testInfo.outputPath(testInfo.project.name.includes('mobile')
      ? 'structured-model-detail-mobile.png'
      : 'structured-model-detail-desktop.png'),
    fullPage: true
  })
  await page.keyboard.press('Escape')
  await expect(detailDrawer).toBeHidden()
  await expect(detailButton).toBeFocused()

  const lifecycleCenter = page.locator('section.lifecycle-center')
  await expect(lifecycleCenter.getByRole('heading', { name: '模型生命周期操作中心' })).toBeVisible()
  await expect(lifecycleCenter.getByRole('button', { name: '刷新已选' })).toBeDisabled()
  await modelCard.getByLabel('选择 OrderModel').check()
  await lifecycleCenter.getByRole('button', { name: '刷新已选' }).click()
  await page.getByRole('dialog', { name: '确认模型刷新' }).getByRole('button', { name: '确认刷新' }).click()
  await expect.poll(() => state.refreshScopes).toContain('selected')
  await expect(lifecycleCenter).toContainText('PUBLISHED')
  await expect(lifecycleCenter).toContainText('g-1')
  await expect(lifecycleCenter).toContainText('g-2')
  await expect(lifecycleCenter).toContainText('42 ms')
  await lifecycleCenter.getByRole('button', { name: '刷新全部' }).click()
  await page.getByRole('dialog', { name: '确认模型刷新' })
    .getByRole('button', { name: '刷新全部并发布' })
    .click()
  await expect.poll(() => state.refreshScopes).toContain('all')

  await lifecycleCenter.getByLabel('模型路径').fill('/runtime/models/demo')
  await lifecycleCenter.getByRole('button', { name: '校验候选路径' }).click()
  await expect(lifecycleCenter.locator('.result-head')).toContainText('CANDIDATE_VALID')
  await expect(lifecycleCenter).toContainText('18 ms')
  await lifecycleCenter.getByText('3 次最近操作').click()
  const lifecycleHistory = lifecycleCenter.locator('.history-list')
  await expect(lifecycleHistory.getByText('候选校验', { exact: true })).toBeVisible()
  await expect(lifecycleHistory.getByText('刷新已选', { exact: true })).toBeVisible()
  await expect(lifecycleHistory.getByText('刷新全部', { exact: true })).toBeVisible()
  await page.waitForTimeout(3200)
  const lifecycleEvidenceStyle = await page.addStyleTag({
    content: '.console-header { visibility: hidden !important; } .skip-link { display: none !important; }'
  })
  await lifecycleCenter.screenshot({
    path: testInfo.outputPath(testInfo.project.name.includes('mobile')
      ? 'model-lifecycle-center-mobile.png'
      : 'model-lifecycle-center-desktop.png')
  })
  await lifecycleEvidenceStyle.evaluate(element => element.remove())

  await page.getByRole('button', { name: /Bundle 来源/ }).click()
  const bundleCard = page.getByRole('article').filter({ hasText: 'runtime-console-demo' })
  await expect(bundleCard).toContainText('1 visible QM')
  await bundleCard.getByRole('button', { name: '高级操作' }).click()
  const advancedDrawer = page.getByRole('dialog', { name: /Bundle 高级操作/ })
  const rawBundlePayload = advancedDrawer.getByLabel('Bundle 原始请求 JSON')
  await expect(rawBundlePayload).toHaveValue(/"bundle": "runtime-console-demo"/)
  await expect(rawBundlePayload).toHaveAttribute('readonly')
  await advancedDrawer.getByText('专家请求 JSON').click()
  const expertOverride = advancedDrawer.locator('label.operation-check')
    .filter({ hasText: '使用原始 JSON 覆盖向导' })
    .getByRole('checkbox')
  await expertOverride.check()
  await expect(rawBundlePayload).toBeEditable()
  await expertOverride.uncheck()
  await advancedDrawer.getByText('专家请求 JSON').click()
  await expect(advancedDrawer.getByLabel('Bundle 资源操作摘要')).toContainText('READ / WRITE')
  await advancedDrawer.getByRole('button', { name: /指定路径/ }).click()
  await advancedDrawer.getByLabel('资源相对路径').fill('../outside.qm')
  const exportRequestsBeforeInvalidPath = state.requests.filter(item => item.path === 'resources/export').length
  await advancedDrawer.getByRole('button', { name: '执行导出' }).click()
  await expect(advancedDrawer.getByRole('alert')).toContainText('目录穿越')
  expect(state.requests.filter(item => item.path === 'resources/export')).toHaveLength(exportRequestsBeforeInvalidPath)
  await advancedDrawer.getByLabel('资源相对路径').fill('models/orders.qm')
  await advancedDrawer.locator('label.operation-check').filter({ hasText: '包含文件内容' })
    .getByRole('checkbox')
    .check()
  await advancedDrawer.getByRole('button', { name: '执行导出' }).click()
  await expect(advancedDrawer.getByText('models/orders.qm')).toBeVisible()
  await expect.poll(() => state.requests.some(item =>
    item.path === 'resources/export'
      && item.namespace === 'default'
      && Array.isArray(item.body.paths)
      && item.body.paths[0] === 'models/orders.qm'
      && item.body.includeContent === true
  )).toBe(true)

  await advancedDrawer.getByRole('button', { name: /保存资源/ }).click()
  await advancedDrawer.getByLabel('资源文件 1 相对路径').fill('models/new-orders.qm')
  await advancedDrawer.getByLabel('资源文件 1 Base SHA-256').fill('mock-sha256')
  await advancedDrawer.getByLabel('资源文件 1 内容').fill('query NewOrders { columns: ["id"] }')
  await expect(advancedDrawer).toContainText('保存只写入资源文件，不会自动完成模型校验或刷新')
  await advancedDrawer.getByRole('button', { name: '确认并保存' }).click()
  const saveConfirm = page.getByRole('dialog', { name: '确认保存 Bundle 资源' })
  await saveConfirm.getByRole('button', { name: '确认保存' }).click()
  await expect(saveConfirm).toBeHidden()
  await expect(advancedDrawer.getByText('models/new-orders.qm')).toBeVisible()
  await expect(advancedDrawer).toContainText('模型校验与刷新尚未执行')
  await expect.poll(() => state.requests.some(item =>
    item.path === 'resources/save'
      && item.namespace === 'default'
      && item.body.validate === false
      && item.body.refresh === false
  )).toBe(true)
  await page.waitForTimeout(3200)
  await page.screenshot({
    path: testInfo.outputPath(testInfo.project.name.includes('mobile')
      ? 'bundle-resource-operations-mobile.png'
      : 'bundle-resource-operations-desktop.png'),
    fullPage: true
  })
  await page.keyboard.press('Escape')

  const namespaceInput = page.getByLabel('当前数据与模型空间')
  await namespaceInput.fill('finance')
  await namespaceInput.press('Enter')
  await namespaceInput.blur()
  await expect(page).toHaveURL(/#\/namespaces\/bundles\?ns=finance$/)
  await expect(page.getByRole('heading', { name: '数据与模型空间 · finance' })).toBeVisible()
  await page.getByRole('button', { name: /分析模型（QM）/ }).click()
  await page.reload()
  await expect(page).toHaveURL(/#\/namespaces\/models\?ns=finance$/)
  await expect(page.getByRole('heading', { name: '数据与模型空间 · finance' })).toBeVisible()
  await expect.poll(() => state.namespaceHeaders.at(-1)).toBe('finance')

  await page.screenshot({
    path: testInfo.outputPath(testInfo.project.name.includes('mobile')
      ? 'namespace-workspace-mobile.png'
      : 'namespace-workspace-desktop.png'),
    fullPage: true
  })

  await page.goto('/console/#/models')
  await expect(page).toHaveURL(/#\/namespaces\/models\?ns=finance$/)
  expect(browserErrors).toEqual([])
})

test('namespace context reloads every workbench and rejects stale responses', async ({ page }, testInfo) => {
  testInfo.setTimeout(90_000)
  const state = mockStates.get(page)!
  const browserErrors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error') browserErrors.push(message.text())
  })
  page.on('pageerror', error => browserErrors.push(error.message))
  await login(page)

  await page.goto('/console/#/query')
  const queryModel = page.getByLabel('QM 模型')
  await expect(queryModel).toHaveValue('OrderModel')
  await page.getByLabel('查询 DSL JSON').fill('{"columns":["amount"]}')
  await page.getByRole('button', { name: '运行查询' }).click()
  await expect(page.getByText('Alice', { exact: true })).toBeVisible()

  await switchNamespace(page, 'finance')
  await expect(queryModel).toHaveValue('FinanceModel')
  await expect(page.getByText('Alice', { exact: true })).toBeHidden()
  await expect(page.getByLabel('当前空间 finance')).toBeVisible()
  await page.getByRole('button', { name: '运行查询' }).click()
  await expect(page.getByText('Revenue', { exact: true })).toBeVisible()
  await expect(page.getByLabel('查询 Payload 摘要')).toContainText('1')
  await expect(page.getByLabel('查询执行诊断')).toContainText('12 ms')
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出 CSV' }).click()
  const queryDownload = await downloadPromise
  expect(queryDownload.suggestedFilename()).toBe('FinanceModel-finance.csv')
  const financeHistory = page.locator('.query-history-list button')
  await expect(financeHistory).toHaveCount(1)
  await expect(financeHistory).toContainText('FinanceModel')
  await page.waitForTimeout(3200)
  const queryEvidenceStyle = await page.addStyleTag({
    content: '.console-header { visibility: hidden !important; } .skip-link { display: none !important; }'
  })
  await page.screenshot({
    path: testInfo.outputPath(testInfo.project.name.includes('mobile')
      ? 'query-workbench-mobile.png'
      : 'query-workbench-desktop.png'),
    fullPage: true
  })
  await queryEvidenceStyle.evaluate(element => element.remove())
  await page.getByLabel('查询 DSL JSON').fill('{')
  await expect(page.getByRole('button', { name: '运行查询' })).toBeDisabled()
  await expect(page.locator('.payload-error')).toContainText('JSON')
  await financeHistory.click()
  await expect(page.getByLabel('查询 DSL JSON')).toHaveValue('{"columns":["amount"]}')
  await page.getByLabel('查询 DSL JSON').fill('{"columns":["amount"],"slice":[]}')
  await page.getByRole('button', { name: '格式化 JSON' }).click()
  await expect(page.getByLabel('查询 DSL JSON')).toHaveValue(/\n/)
  await page.getByRole('button', { name: '校验', exact: true }).click()
  await expect(page.getByLabel('查询命令上下文')).toContainText('VALIDATE')
  await expect(page.locator('.query-history-list button')).toHaveCount(2)
  await expect.poll(() => state.requests.some(item =>
    item.path === 'query/FinanceModel/validate' && item.namespace === 'finance'
  )).toBe(true)
  await expect.poll(() => state.requests.some(item =>
    item.path === 'query/FinanceModel/execute' && item.namespace === 'finance'
  )).toBe(true)

  state.delayNextDefaultModels = true
  await switchNamespace(page, 'default')
  await switchNamespace(page, 'finance')
  await expect(queryModel).toHaveValue('FinanceModel')
  await page.waitForTimeout(450)
  await expect(queryModel).toHaveValue('FinanceModel')

  await page.goto('/console/#/tables')
  const tableCatalog = page.locator('#console-main .table-list')
  const tableInspector = page.locator('.split-grid > section').nth(1)
  await expect(tableCatalog.getByText('invoices', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '检查' }).click()
  await expect(tableInspector.getByText('invoice_id', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: '生成 SELECT' }).click()
  const sqlEditor = page.getByLabel('只读 SQL')
  await expect(sqlEditor).toHaveValue('SELECT *\nFROM finance.invoices')
  await page.getByRole('button', { name: '运行 SQL' }).click()
  const sqlResult = page.locator('.sql-panel .workbench-result')
  await expect(sqlResult.getByText('finance', { exact: true })).toBeVisible()
  await expect.poll(() => state.requests.some(item =>
    item.path === 'sql/query'
      && item.namespace === 'finance'
      && item.body.dataSource === 'analytics'
      && item.body.sql === 'SELECT *\nFROM finance.invoices'
  )).toBe(true)

  await page.getByRole('button', { name: '生成 TM 草稿' }).click()
  const draftDrawer = page.getByRole('dialog', { name: 'TM 机械草稿' })
  const draftContent = page.getByLabel('TM 草稿内容')
  await expect(draftDrawer).toBeVisible()
  await expect(draftDrawer).toContainText('InvoicesModel')
  await expect(draftDrawer).toContainText('尚未校验、保存、注册或刷新')
  await expect(draftDrawer).toContainText('仅下载到浏览器，不写入 Runtime')
  await expect(draftContent).toHaveValue(/tableName: 'invoices'/)
  await expect(draftContent).toHaveValue(/idColumn: 'invoice_id'/)
  await expect(draftContent).toHaveValue(/type: 'LONG'/)
  await expect(draftContent).toHaveValue(/dimensions: \[\]/)
  await expect(draftContent).toHaveValue(/measures: \[\]/)
  const draftBox = await page.locator('.tm-draft-drawer').boundingBox()
  const viewport = page.viewportSize()
  expect(draftBox).not.toBeNull()
  expect(viewport).not.toBeNull()
  expect(draftBox!.width).toBeLessThanOrEqual(viewport!.width)
  expect(draftBox!.width).toBeGreaterThan(Math.min(360, viewport!.width * 0.75))
  const tmDownloadPromise = page.waitForEvent('download')
  await draftDrawer.getByRole('button', { name: '下载 .tm' }).click()
  const tmDownload = await tmDownloadPromise
  expect(tmDownload.suggestedFilename()).toBe('InvoicesModel.tm')
  await expect(page.locator('.el-message')).toHaveCount(0, { timeout: 6_000 })
  await page.screenshot({
    path: testInfo.outputPath(testInfo.project.name.includes('mobile')
      ? 'tables-tm-draft-mobile.png'
      : 'tables-tm-draft-desktop.png')
  })

  await page.getByLabel('当前数据与模型空间').evaluate((element) => {
    const input = element as HTMLInputElement
    input.value = 'default'
    input.dispatchEvent(new Event('change', { bubbles: true }))
  })
  await expect(page.getByLabel('当前数据与模型空间')).toHaveValue('default')
  await expect(draftDrawer).toBeHidden()

  await expect(tableCatalog.getByText('orders', { exact: true })).toBeVisible()
  await expect(tableInspector.getByText('invoice_id', { exact: true })).toBeHidden()
  await expect(sqlResult.getByText('finance', { exact: true })).toBeHidden()
  await expect(page.getByLabel('数据源')).toHaveValue('analytics')
  await expect(sqlEditor).toHaveValue('SELECT *\nFROM finance.invoices')

  await page.goto('/console/#/compose')
  const composeScript = page.getByLabel('Compose 脚本')
  await composeScript.fill('query RetainedCompose { columns: ["id"] }')
  await page.getByRole('button', { name: '预览', exact: true }).click()
  await expect(page.getByText('"namespace": "default"')).toBeVisible()
  await switchNamespace(page, 'finance')
  await expect(composeScript).toHaveValue('query RetainedCompose { columns: ["id"] }')
  await expect(page.getByText('运行校验、预览或执行后显示结果。')).toBeVisible()
  await page.getByRole('button', { name: '预览', exact: true }).click()
  await expect(page.getByText('"namespace": "finance"')).toBeVisible()
  await expect.poll(() => state.requests.some(item =>
    item.path === 'compose/preview'
      && item.namespace === 'finance'
      && item.body.namespace === 'finance'
  )).toBe(true)

  await page.goto('/console/#/fsscript')
  await page.getByText('我已核对脚本来源').locator('input').check()
  await page.getByRole('button', { name: '展开高级工作台' }).click()
  const fsscript = page.getByLabel('Fsscript', { exact: true })
  await fsscript.fill('return { retained: true }')
  await page.getByRole('button', { name: '确认并执行' }).click()
  await page.getByRole('dialog', { name: '最终确认 Fsscript 执行' })
    .getByRole('button', { name: '确认执行' })
    .click()
  const fsscriptResult = page.locator('.fsscript-workbench .workbench-result')
  await expect(fsscriptResult.getByText('finance', { exact: true })).toBeVisible()

  await switchNamespace(page, '')
  await expect(fsscript).toHaveValue('return { retained: true }')
  await expect(page.getByText('暂无执行结果。')).toBeVisible()
  await page.getByRole('button', { name: '确认并执行' }).click()
  await page.getByRole('dialog', { name: '最终确认 Fsscript 执行' })
    .getByRole('button', { name: '确认执行' })
    .click()
  await expect(fsscriptResult.getByText('empty', { exact: true })).toBeVisible()
  await expect.poll(() => state.requests.some(item =>
    item.path === 'fsscript/execute'
      && item.namespace === ''
      && item.body.namespace === ''
  )).toBe(true)

  await page.reload()
  await expect(page.getByLabel('当前数据与模型空间')).toHaveValue('')
  await expect(page.getByRole('heading', { name: 'Fsscript', exact: true })).toBeVisible()
  expect(browserErrors).toEqual([])
})

test('authoring workspace preserves conflicted drafts and completes the isolated candidate loop', async ({ page }, testInfo) => {
  testInfo.setTimeout(90_000)
  const state = mockStates.get(page)!
  const browserErrors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error'
      && !message.text().includes('409 (Conflict)')
      && !message.text().includes('422 (Unprocessable Entity)')) browserErrors.push(message.text())
  })
  page.on('pageerror', error => browserErrors.push(error.message))
  state.bundles.push({
    name: 'shared-readonly-models',
    namespace: 'default',
    path: '',
    enabled: true,
    source: 'live-loader',
    status: 'active',
    sourceType: 'jar',
    editable: false,
    workspaceEligible: false,
    managedByRuntimeApi: false,
    canUpdate: false,
    canRemove: false,
    namespaceBindings: ['default'],
    sourceIdentity: 'source:shared-readonly-models'
  })

  await login(page)
  await page.goto('/console/#/namespaces/authoring?ns=default')
  await expect(page.getByRole('heading', { name: '模型创作工作区' })).toBeVisible()
  await expect(page.getByText('NO', { exact: true })).toHaveCount(1)

  const readOnlySource = page.locator('.source-ticket').filter({ hasText: 'shared-readonly-models' })
  await expect(readOnlySource).toContainText('READ ONLY')
  await expect(readOnlySource.getByRole('button', { name: '创建' })).toBeDisabled()
  const eligibleSource = page.locator('.source-ticket').filter({ hasText: 'runtime-console-demo' })
  await expect(eligibleSource).toContainText('ELIGIBLE')
  await expect(eligibleSource.getByRole('button', { name: '创建' })).toBeEnabled()

  await eligibleSource.getByRole('button', { name: '创建' }).click()
  await expect(page.getByText('ws-created-2', { exact: true }).first()).toBeVisible()
  const createRequest = state.requests.find(item => item.path === 'authoring/workspaces' && item.body.sourceBundle)
  expect(createRequest).toMatchObject({
    namespace: 'default',
    body: { namespace: 'default', sourceBundle: 'runtime-console-demo' }
  })

  await page.getByRole('button', { name: /DRAFT runtime-console-demo.*ws-default-001/ }).click()
  await expect(page).toHaveURL(/#\/namespaces\/authoring\?ns=default&workspaceId=ws-default-001$/)
  await expect(page.getByText('candidate-001', { exact: true }).first()).toBeVisible()

  await page.getByRole('button', { name: '新建' }).click()
  const resourcePath = page.getByLabel('Workspace 资源路径')
  await resourcePath.fill('../invalid.tm')
  await page.getByLabel('Workspace 资源内容').fill('export const invalid = true')
  await expect(page.getByRole('button', { name: '保存为新 revision' })).toBeDisabled()
  await expect(page.getByText(/不能包含空目录、\. 或 \.\./)).toBeVisible()
  await resourcePath.fill('scripts/local-tax.fsscript')
  await page.getByLabel('Workspace 资源内容').fill('export const localTax = 0.13')
  await page.getByRole('button', { name: '保存为新 revision' }).click()
  await expect(page.getByRole('button', { name: /FSSCRIPT scripts\/local-tax\.fsscript/ })).toBeVisible()
  await page.getByRole('button', { name: '删除草稿资源' }).click()
  await page.getByRole('dialog', { name: '确认删除 workspace 资源' })
    .getByRole('button', { name: '删除草稿资源' })
    .click()
  await expect(page.getByRole('button', { name: /FSSCRIPT scripts\/local-tax\.fsscript/ })).toBeHidden()

  await page.getByRole('button', { name: /QM query\/OrderQuery\.qm/ }).click()
  const editor = page.getByLabel('Workspace 资源内容')
  await expect(editor).toHaveValue(/source: Order/)
  await editor.fill('export const OrderQuery = queryModel({ source: LocalDraftOrder })')
  await expect(page.getByText('UNSAVED', { exact: true })).toBeVisible()
  page.once('dialog', async dialog => {
    expect(dialog.type()).toBe('confirm')
    expect(dialog.message()).toContain('未保存修改')
    await dialog.dismiss()
  })
  await page.getByRole('button', { name: /^03 Bundle 来源/ }).click()
  await expect(page).toHaveURL(/#\/namespaces\/authoring\?ns=default&workspaceId=ws-default-001$/)
  await expect(editor).toHaveValue(/LocalDraftOrder/)

  state.conflictNextWorkspaceSave = true
  const savesBeforeConflict = state.requests.filter(item => item.path.endsWith('/resources/save')).length
  await page.getByRole('button', { name: '保存为新 revision' }).click()
  const conflict = page.locator('.authoring-error')
  await expect(conflict).toContainText('WORKSPACE_REVISION_CONFLICT')
  await expect(page.locator('.conflict-compare')).toContainText('LocalDraftOrder')
  await expect(page.locator('.conflict-compare')).toContainText('ServerOrder')
  await expect.poll(() => state.requests.filter(item => item.path.endsWith('/resources/save')).length)
    .toBe(savesBeforeConflict + 1)
  await expect(editor).toHaveValue(/LocalDraftOrder/)

  await page.getByRole('button', { name: '保存为新 revision' }).click()
  await expect(page.locator('.el-message').filter({ hasText: '草稿已保存' }).last()).toBeVisible()
  const workspaceSaves = state.requests.filter(item => item.path.endsWith('/resources/save'))
  expect(workspaceSaves).toHaveLength(savesBeforeConflict + 2)
  expect(workspaceSaves.at(-1)?.body.expectedCandidateRevision).toBe('sha256:candidate-server-conflict')
  expect(workspaceSaves.at(-1)?.body.files).toEqual([{
    path: 'query/OrderQuery.qm',
    content: 'export const OrderQuery = queryModel({ source: LocalDraftOrder })'
  }])

  const inspector = page.getByRole('navigation', { name: 'Candidate 检查工具' })
  await inspector.getByRole('button', { name: 'DIFF' }).click()
  await page.getByRole('button', { name: '读取 exact diff' }).click()
  await expect(page.locator('.diff-list')).toContainText('MODIFIED')
  await expect(page.locator('.diff-list')).toContainText('LocalDraftOrder')

  await inspector.getByRole('button', { name: 'VALIDATE' }).click()
  state.invalidNextWorkspaceValidation = true
  await page.getByRole('button', { name: '校验当前 revision' }).click()
  await expect(page.locator('.authoring-error')).toContainText('WORKSPACE_VALIDATION_FAILED')
  await expect(page.locator('.validation-evidence')).toContainText('INVALID')
  await expect(page.locator('.validation-evidence')).toContainText('CURRENT')
  await expect(page.getByText('Candidate QM syntax is invalid.', { exact: true })).toBeVisible()
  await expect(page.locator('.conflict-compare')).toHaveCount(0)
  await page.getByRole('button', { name: '校验当前 revision' }).click()
  await expect(page.locator('.validation-evidence')).toContainText('CURRENT')
  await expect(page.locator('.validation-evidence')).toContainText('VALID')

  const liveQueriesBefore = state.requests.filter(item => /^query\//.test(item.path)).length
  await inspector.getByRole('button', { name: 'CANDIDATE QUERY' }).click()
  await expect(page.getByLabel('Candidate QM 模型')).toHaveValue('OrderQuery')
  await page.getByLabel('Candidate Query DSL JSON').fill('{"columns":["customer","amount"]}')
  await page.getByRole('button', { name: 'Execute candidate' }).click()
  await expect(page.getByText('Candidate Alice', { exact: true })).toBeVisible()
  await expect(page.locator('.query-identity')).toContainText('candidate-g1')
  const executionFacts = page.getByLabel('Candidate query 执行事实')
  await expect(executionFacts).toContainText('JDBC')
  await expect(executionFacts).toContainText('EXECUTED')
  await expect(executionFacts).toContainText('9 ms')
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出 candidate CSV' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('candidate-OrderQuery-ws-default-001.csv')
  const downloadedPath = await download.path()
  expect(downloadedPath).not.toBeNull()
  const csv = await readFile(downloadedPath!, 'utf8')
  expect(csv).toContain('customer,amount,note')
  expect(csv).toContain('Candidate Alice,188')
  expect(csv).toContain(`"'=HYPERLINK(""bad"")"`)
  expect(state.requests.filter(item => /^query\//.test(item.path))).toHaveLength(liveQueriesBefore)
  const candidateRequest = state.requests.find(item => item.path.endsWith('/query/OrderQuery/execute'))
  expect(candidateRequest?.namespace).toBe('default')
  expect(candidateRequest?.body).toMatchObject({
    candidateRevision: 'sha256:candidate-004',
    request: { columns: ['customer', 'amount'] }
  })

  await expect(page.locator('.el-message')).toHaveCount(0, { timeout: 6_000 })
  const authoringEvidenceStyle = await page.addStyleTag({
    content: '.console-header { visibility: hidden !important; } .skip-link { display: none !important; }'
  })
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({
    path: testInfo.outputPath(testInfo.project.name.includes('mobile')
      ? 'authoring-workspace-mobile.png'
      : 'authoring-workspace-desktop.png'),
    fullPage: true
  })
  await authoringEvidenceStyle.evaluate(element => element.remove())

  await page.getByRole('button', { name: 'Discard workspace' }).click()
  const discardDialog = page.getByRole('dialog', { name: '确认 discard workspace' })
  await expect(discardDialog).toContainText('runtime-console-demo')
  await expect(discardDialog).toContainText('candidate-004')
  await discardDialog.getByRole('button', { name: '终结隔离草稿' }).click()
  await expect(page.getByText('TERMINAL STATE', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Discard workspace' })).toHaveCount(0)
  expect(state.requests.some(item => item.path === 'resources/save')).toBe(false)
  expect(state.requests.some(item => item.path === 'models/refresh')).toBe(false)
  expect(browserErrors).toEqual([])
})

test('authoring workspace publishes an exact revision, refreshes PUBLISHING, and starts the next workspace', async ({ page }, testInfo) => {
  const state = mockStates.get(page)!
  const workspace = state.authoringWorkspaces[0]
  workspace.state = 'VALIDATED'
  workspace.lastValidation = {
    valid: true,
    candidateRevision: workspace.candidateRevision,
    baseBundleRevision: workspace.baseBundleRevision,
    baseNamespaceSourceRevision: workspace.baseNamespaceSourceRevision,
    validatedAt: '2026-08-01T05:00:00Z',
    totalFiles: 3,
    validFiles: 3,
    invalidFiles: 0,
    cascadingErrors: 0,
    issues: []
  }
  state.returnPublishingNextWorkspacePublish = true

  await login(page)
  await page.goto('/console/#/namespaces/authoring?ns=default&workspaceId=ws-default-001')
  await expect(page.getByRole('heading', { name: '开发 Runtime 发布与失败恢复' })).toBeVisible()
  const publishButton = page.getByRole('button', { name: '确认并发布 exact revision' })
  await expect(publishButton).toBeEnabled()
  await publishButton.click()

  const publishDialog = page.getByRole('dialog', { name: '确认发布 exact candidate revision' })
  await expect(publishDialog).toContainText('default / runtime-console-demo')
  await expect(publishDialog).toContainText('sha256:candidate-001')
  await expect(publishDialog).toContainText('sha256:base-bundle-001')
  await expect(publishDialog).toContainText('source:default:g1')
  await expect(publishDialog).toContainText('不代表生产 promotion')
  await publishDialog.getByRole('button', { name: '发布 exact revision' }).click()

  await expect(page.locator('.workspace-revision-bar')).toContainText('PUBLISHING')
  await expect(page.getByRole('button', { name: '新建' })).toBeDisabled()
  await page.getByRole('navigation', { name: 'Candidate 检查工具' })
    .getByRole('button', { name: 'VALIDATE' })
    .click()
  await expect(page.getByRole('button', { name: '校验当前 revision' })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Discard workspace' })).toHaveCount(0)
  const publishRequests = state.requests.filter(item => item.path.endsWith('/publish'))
  expect(publishRequests).toHaveLength(1)
  expect(publishRequests[0]).toEqual({
    path: 'authoring/workspaces/ws-default-001/publish',
    namespace: 'default',
    body: {
      expectedCandidateRevision: 'sha256:candidate-001',
      expectedBaseBundleRevision: 'sha256:base-bundle-001',
      expectedBaseNamespaceSourceRevision: 'source:default:g1'
    }
  })

  await page.getByRole('button', { name: '刷新 publication 状态' }).click()
  await expect(page.locator('.workspace-revision-bar')).toContainText('PUBLISHED')
  await expect(page.getByText('publication-attempt-001', { exact: true })).toBeVisible()
  await expect(page.getByText('Immutable candidate artifact is live.', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '创建下一 workspace' })).toBeEnabled()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.locator('.el-message')).toHaveCount(0, { timeout: 6_000 })
  await page.screenshot({
    path: testInfo.outputPath(testInfo.project.name.includes('mobile')
      ? 'authoring-published-mobile.png'
      : 'authoring-published-desktop.png'),
    fullPage: true
  })
  await page.getByRole('button', { name: '创建下一 workspace' }).click()
  await expect(page.getByText('ws-created-2', { exact: true }).first()).toBeVisible()
  await expect(page.locator('.workspace-revision-bar')).toContainText('DRAFT')

  expect(state.requests.some(item => item.path === 'resources/save')).toBe(false)
  expect(state.requests.some(item => item.path === 'models/refresh')).toBe(false)
  expect(state.requests.filter(item => /^query\//.test(item.path))).toHaveLength(0)
})

test('authoring workspace fails closed and recovers only the pinned publication attempt', async ({ page }, testInfo) => {
  const state = mockStates.get(page)!
  const workspace = state.authoringWorkspaces[0]
  workspace.state = 'VALIDATED'
  workspace.lastValidation = {
    valid: true,
    candidateRevision: workspace.candidateRevision,
    baseBundleRevision: workspace.baseBundleRevision,
    baseNamespaceSourceRevision: workspace.baseNamespaceSourceRevision,
    validatedAt: '2026-08-01T05:00:00Z',
    totalFiles: 3,
    validFiles: 3,
    invalidFiles: 0,
    cascadingErrors: 0,
    issues: []
  }
  state.failNextWorkspacePublish = true

  await login(page)
  await page.goto('/console/#/namespaces/authoring?ns=default&workspaceId=ws-default-001')
  await page.getByRole('button', { name: '确认并发布 exact revision' }).click()
  await page.getByRole('dialog', { name: '确认发布 exact candidate revision' })
    .getByRole('button', { name: '发布 exact revision' })
    .click()

  await expect(page.locator('.authoring-error')).toContainText('WORKSPACE_RECOVERY_REQUIRED')
  await expect(page.locator('.workspace-revision-bar')).toContainText('RECOVERY_REQUIRED')
  await expect(page.getByText('Catalog refresh did not converge; exact recovery is required.', { exact: true })).toBeVisible()
  const recoverButton = page.getByRole('button', { name: '恢复失败发布' })
  await expect(recoverButton).toBeEnabled()
  const publishesBeforeRecovery = state.requests.filter(item => item.path.endsWith('/publish')).length
  await recoverButton.click()

  const recoveryDialog = page.getByRole('dialog', { name: '确认恢复失败发布' })
  await expect(recoveryDialog).toContainText('publication-attempt-001')
  await expect(recoveryDialog).toContainText('sha256:candidate-001')
  await expect(recoveryDialog).toContainText('不是成功发布后的历史 rollback')
  await recoveryDialog.getByRole('button', { name: '恢复 exact attempt' }).click()

  await expect(page.locator('.workspace-revision-bar')).toContainText('STALE')
  await expect(page.getByText('RECOVERED', { exact: true })).toBeVisible()
  await expect(page.getByText('Prior live source and catalog were restored.', { exact: true })).toBeVisible()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.locator('.el-message')).toHaveCount(0, { timeout: 6_000 })
  await page.screenshot({
    path: testInfo.outputPath(testInfo.project.name.includes('mobile')
      ? 'authoring-recovered-mobile.png'
      : 'authoring-recovered-desktop.png'),
    fullPage: true
  })
  const recoveryRequests = state.requests.filter(item => item.path.endsWith('/publish/recover'))
  expect(recoveryRequests).toHaveLength(1)
  expect(recoveryRequests[0]).toEqual({
    path: 'authoring/workspaces/ws-default-001/publish/recover',
    namespace: 'default',
    body: {
      expectedCandidateRevision: 'sha256:candidate-001',
      publicationAttemptId: 'publication-attempt-001'
    }
  })
  expect(state.requests.filter(item => item.path.endsWith('/publish'))).toHaveLength(publishesBeforeRecovery)
  expect(state.requests.some(item => item.path === 'resources/save')).toBe(false)
  expect(state.requests.some(item => item.path === 'models/refresh')).toBe(false)
})

test('authoring workspace transfers an immutable package, applies it, and controls rollback recovery', async ({ page }, testInfo) => {
  testInfo.setTimeout(90_000)
  const state = mockStates.get(page)!
  Object.assign(state.capabilities, {
    'authoring.releasePackage.export': 'supported',
    'authoring.releasePackage.import': 'supported',
    'authoring.production.apply': 'supported',
    'authoring.production.rollback': 'supported'
  })
  const development = state.authoringWorkspaces[0]
  development.baseBundleRevision = releasePackageFixture().baseBundleRevision
  development.baseNamespaceSourceRevision = releasePackageFixture().baseNamespaceSourceRevision
  development.state = 'VALIDATED'
  development.lastValidation = releasePackageFixture().validation

  await login(page)
  await page.goto('/console/#/namespaces/authoring?ns=default&workspaceId=ws-default-001')
  await expect(page.getByRole('heading', { name: '不可变交付包' })).toBeVisible()
  const releaseCapabilities = page.getByLabel('Release capabilities')
  await expect(releaseCapabilities).toContainText('EXPORT supported')
  await expect(releaseCapabilities).toContainText('IMPORT supported')
  await expect(releaseCapabilities).toContainText('APPLY supported')
  await expect(releaseCapabilities).toContainText('ROLLBACK supported')
  await expect(page.getByRole('heading', { name: '开发 Runtime 发布与失败恢复' })).toHaveCount(0)

  const exportButton = page.getByRole('button', { name: '下载 release package' })
  await expect(exportButton).toBeEnabled()
  const releaseDownload = page.waitForEvent('download')
  await exportButton.click()
  const exportDialog = page.getByRole('dialog', { name: '确认导出 exact release package' })
  await expect(exportDialog).toContainText('sha256:candidate-001')
  await expect(exportDialog).toContainText('不含数据、权限或签名')
  await exportDialog.getByRole('button', { name: '下载 JSON package' }).click()
  const downloaded = await releaseDownload
  expect(downloaded.suggestedFilename()).toBe('foggy-release-runtime-console-demo-candidate-00.json')
  const exportedPath = await downloaded.path()
  const exportedJson = JSON.parse(await readFile(exportedPath!, 'utf8'))
  expect(exportedJson).toMatchObject({
    formatVersion: 'foggy-authoring-release/v1',
    packageId: 'sha256:release-package-001',
    candidateRevision: 'sha256:candidate-001'
  })

  await page.getByLabel('选择 release package JSON').setInputFiles({
    name: 'sales-release.json',
    mimeType: 'application/json',
    buffer: Buffer.from(JSON.stringify(releasePackageFixture()))
  })
  const preview = page.getByLabel('Release package preview')
  await expect(preview).toContainText('foggy-authoring-release/v1')
  await expect(preview).toContainText('development / runtime-console-demo')
  await expect(preview).toContainText('VALID · PROVENANCE ONLY')
  await expect(page.getByLabel('Target Namespace / current X-NS')).toHaveValue('default')
  await expect(page.getByLabel('Eligible target Bundle')).toHaveValue('runtime-console-demo')

  await page.getByRole('button', { name: '确认 target 并导入只读 candidate' }).click()
  const importDialog = page.getByRole('dialog', { name: '确认导入只读 production candidate' })
  await expect(importDialog).toContainText('sha256:release-package-001')
  await expect(importDialog).toContainText('Target: default / runtime-console-demo')
  await expect(importDialog).toContainText('导入后不会自动 apply')
  await importDialog.getByRole('button', { name: '导入到明确 target' }).click()

  await expect(page.locator('.workspace-revision-bar')).toContainText('DRAFT')
  await expect(page.getByRole('heading', { name: '生产 candidate · immutable' })).toBeVisible()
  await expect(page.getByRole('button', { name: '新建' })).toBeDisabled()
  await page.getByRole('button', { name: /QM query\/OrderQuery\.qm/ }).click()
  await expect(page.getByLabel('Workspace 资源内容')).toBeDisabled()
  await expect(page.getByRole('button', { name: '保存为新 revision' })).toBeDisabled()
  expect(state.requests.find(item => item.path === 'authoring/releases/import')?.body).toMatchObject({
    namespace: 'default',
    targetBundle: 'runtime-console-demo',
    releasePackage: {
      packageId: 'sha256:release-package-001',
      candidateRevision: 'sha256:candidate-001'
    }
  })

  const inspector = page.getByRole('navigation', { name: 'Candidate 检查工具' })
  await inspector.getByRole('button', { name: 'VALIDATE' }).click()
  await page.getByRole('button', { name: '校验当前 revision' }).click()
  await expect(page.locator('.workspace-revision-bar')).toContainText('VALIDATED')
  await inspector.getByRole('button', { name: 'CANDIDATE QUERY' }).click()
  await page.getByLabel('Candidate QM 模型').fill('OrderQuery')
  await page.getByRole('button', { name: 'Execute candidate' }).click()
  await expect(page.getByText('Candidate Alice', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '确认并 apply exact package' }).click()
  const applyDialog = page.getByRole('dialog', { name: '确认生产 apply exact package' })
  await expect(applyDialog).toContainText('sha256:release-package-001')
  await expect(applyDialog).toContainText('sha256:production-base-001')
  await expect(applyDialog).toContainText('source:production:g1')
  await applyDialog.getByRole('button', { name: 'Apply exact package' }).click()
  await expect(page.locator('.workspace-revision-bar')).toContainText('PUBLISHED')
  const promoteRequest = state.requests.find(item => item.path.endsWith('/promote'))
  expect(promoteRequest?.body).toEqual({
    releasePackageId: 'sha256:release-package-001',
    expectedCandidateRevision: 'sha256:candidate-001',
    expectedBaseBundleRevision: 'sha256:production-base-001',
    expectedBaseNamespaceSourceRevision: 'source:production:g1'
  })
  expect(state.requests.some(item => item.path.endsWith('/publish'))).toBe(false)

  const imported = state.authoringWorkspaces.find(item => item.workspaceId.startsWith('ws-imported'))!
  imported.state = 'ROLLBACK_REQUIRED'
  imported.lastPublication.rollback = {
    status: 'ROLLBACK_REQUIRED',
    startedAt: '2026-08-01T07:21:00Z',
    diagnostics: ['Rollback convergence is unknown.']
  }
  await page.reload()
  await expect(page.locator('.workspace-revision-bar')).toContainText('ROLLBACK_REQUIRED')
  await expect(page.getByRole('button', { name: '新建' })).toBeDisabled()
  await page.getByRole('button', { name: '恢复 pinned candidate' }).click()
  const recoveryDialog = page.getByRole('dialog', { name: '确认 pinned rollback recovery' })
  await expect(recoveryDialog).toContainText('promotion-attempt-001')
  await expect(recoveryDialog).toContainText('不会覆盖第三方 drift')
  await recoveryDialog.getByRole('button', { name: '恢复 pinned candidate' }).click()
  await expect(page.locator('.workspace-revision-bar')).toContainText('PUBLISHED')
  expect(state.requests.find(item => item.path.endsWith('/rollback/recover'))?.body).toEqual({
    releasePackageId: 'sha256:release-package-001',
    expectedCandidateRevision: 'sha256:candidate-001',
    publicationAttemptId: 'promotion-attempt-001'
  })

  imported.lastPublication.rollback = null
  imported.state = 'PUBLISHED'
  await page.reload()
  await page.getByRole('button', { name: 'Rollback 到直接前一 base' }).click()
  const rollbackDialog = page.getByRole('dialog', { name: '确认一步 pinned rollback' })
  await expect(rollbackDialog).toContainText('Direct previous base: sha256:production-base-001')
  await rollbackDialog.getByRole('button', { name: 'Rollback 到直接前一 base' }).click()
  await expect(page.locator('.workspace-revision-bar')).toContainText('ROLLED_BACK')
  await expect(page.getByText('直接前一 production base 已恢复')).toBeVisible()
  expect(state.requests.find(item => /\/rollback$/.test(item.path))?.body).toEqual({
    releasePackageId: 'sha256:release-package-001',
    expectedCandidateRevision: 'sha256:candidate-001',
    publicationAttemptId: 'promotion-attempt-001'
  })

  const browserPersistence = await page.evaluate(() => {
    const values: string[] = []
    for (let index = 0; index < localStorage.length; index++) {
      const key = localStorage.key(index)
      values.push(key ? localStorage.getItem(key) || '' : '')
    }
    return { values: values.join('\n'), href: window.location.href }
  })
  expect(browserPersistence.values).not.toContain('release-package-001')
  expect(browserPersistence.href).not.toContain('release-package-001')
  expect(state.requests.some(item => item.path.endsWith('/resources/save'))).toBe(false)

  await page.screenshot({
    path: testInfo.outputPath(testInfo.project.name.includes('mobile')
      ? 'authoring-production-promotion-mobile.png'
      : 'authoring-production-promotion-desktop.png'),
    fullPage: true
  })
})

test('authoring workspace recovers a failed production apply only by its pinned attempt', async ({ page }) => {
  const state = mockStates.get(page)!
  Object.assign(state.capabilities, {
    'authoring.releasePackage.export': 'supported',
    'authoring.releasePackage.import': 'supported',
    'authoring.production.apply': 'supported',
    'authoring.production.rollback': 'supported'
  })
  const release = releasePackageFixture()
  state.authoringWorkspaces = [{
    workspaceId: 'ws-imported-recovery',
    targetNamespace: 'default',
    sourceBundle: 'runtime-console-demo',
    sourceKind: 'runtime-managed',
    baseBundleRevision: 'sha256:production-base-001',
    baseNamespaceSourceRevision: 'source:production:g1',
    candidateRevision: release.candidateRevision,
    state: 'RECOVERY_REQUIRED',
    createdAt: '2026-08-01T07:10:00Z',
    updatedAt: '2026-08-01T07:20:00Z',
    lastValidation: {
      ...release.validation,
      baseBundleRevision: 'sha256:production-base-001',
      baseNamespaceSourceRevision: 'source:production:g1'
    },
    lastPublication: {
      attemptId: 'promotion-attempt-recovery',
      status: 'RECOVERY_REQUIRED',
      candidateRevision: release.candidateRevision,
      baseBundleRevision: 'sha256:production-base-001',
      baseNamespaceSourceRevision: 'source:production:g1',
      startedAt: '2026-08-01T07:19:00Z',
      diagnostics: ['Production apply did not converge.']
    },
    releaseImport: {
      packageId: release.packageId,
      formatVersion: release.formatVersion,
      sourceRuntimeApiVersion: release.sourceRuntimeApiVersion,
      sourceNamespace: release.sourceNamespace,
      sourceBundle: release.sourceBundle,
      exportedCandidateRevision: release.candidateRevision,
      importedAt: '2026-08-01T07:10:00Z'
    },
    diagnostics: ['Explicit publication recovery is required.']
  }]
  state.authoringResources = {
    'ws-imported-recovery': release.resources.map(item => ({ ...item }))
  }

  await login(page)
  await page.goto('/console/#/namespaces/authoring?ns=default&workspaceId=ws-imported-recovery')
  await expect(page.locator('.workspace-revision-bar')).toContainText('RECOVERY_REQUIRED')
  await expect(page.getByText('Production apply 需要恢复 failed publication attempt')).toBeVisible()
  const recover = page.getByRole('button', { name: '恢复 failed apply' })
  await expect(recover).toBeEnabled()
  await recover.click()
  const dialog = page.getByRole('dialog', { name: '确认恢复失败发布' })
  await expect(dialog).toContainText('promotion-attempt-recovery')
  await expect(dialog).toContainText('sha256:candidate-001')
  await dialog.getByRole('button', { name: '恢复 exact attempt' }).click()

  await expect(page.locator('.workspace-revision-bar')).toContainText('STALE')
  expect(state.requests.find(item => item.path.endsWith('/publish/recover'))?.body).toEqual({
    expectedCandidateRevision: 'sha256:candidate-001',
    publicationAttemptId: 'promotion-attempt-recovery'
  })
  expect(state.requests.some(item => item.path.endsWith('/promote'))).toBe(false)
  expect(state.requests.some(item => item.path.endsWith('/resources/save'))).toBe(false)
})
