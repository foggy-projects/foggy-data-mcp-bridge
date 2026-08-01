<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { normalizeResultRows, parseJsonObject, prettyJson } from '@/utils/json'
import type { BundleItem } from '@/features/namespace/types'
import {
  canExportRelease,
  canPromoteRelease,
  canPublishWorkspace,
  canRollbackRelease,
  isCurrentValidation,
  releasePackageFilename,
  shortRevision,
  suggestedModelName,
  workspaceActions,
  workspaceResourcePathError
} from './authoringWorkspace'
import type {
  AuthoringDiffResponse,
  AuthoringQueryResponse,
  AuthoringReleasePackage,
  AuthoringResource,
  AuthoringResourcesResponse,
  AuthoringWorkspaceInfo,
  AuthoringWorkspaceListResponse
} from './types'
import CandidateInspector from './CandidateInspector.vue'
import ResourceEditor from './ResourceEditor.vue'
import WorkspaceCatalog from './WorkspaceCatalog.vue'
import WorkspacePublication from './WorkspacePublication.vue'
import WorkspaceReleasePromotion from './WorkspaceReleasePromotion.vue'
import {
  recoverWorkspaceRequest,
  rollbackWorkspaceRequest,
  useWorkspacePublication
} from './useWorkspacePublication'

const props = defineProps<{
  namespace: string
  bundles: BundleItem[]
  capabilities: Record<string, string>
}>()

const route = useRoute()
const router = useRouter()
const workspaces = ref<AuthoringWorkspaceInfo[]>([])
const workspaceWarnings = ref<string[]>([])
const selected = ref<AuthoringWorkspaceInfo | null>(null)
const resources = ref<AuthoringResource[]>([])
const selectedResource = ref<AuthoringResource | null>(null)
const editorPath = ref('')
const editorContent = ref('')
const originalContent = ref('')
const creatingResource = ref(false)
const busy = ref('')
const publication = useWorkspacePublication(busy)
const loadError = ref('')
const operationError = ref<RuntimeRequestError | null>(null)
const conflictServerContent = ref<string | null>(null)
const diffResult = ref<AuthoringDiffResponse | null>(null)
const inspector = ref<'diff' | 'validate' | 'query'>('diff')
const queryModel = ref('')
const queryMode = ref<'validate' | 'execute'>('execute')
const queryPayload = ref(prettyJson({
  columns: [],
  slice: [],
  groupBy: [],
  orderBy: [],
  page: { start: 0, limit: 100 }
}))
const queryResult = ref<AuthoringQueryResponse | null>(null)
let namespaceLoadVersion = 0
let workspaceOpenVersion = 0
let resourceListVersion = 0
let resourceLoadVersion = 0

const eligibleBundles = computed(() => props.bundles.filter(bundle => bundle.workspaceEligible === true))
const workspaceIdFromRoute = computed(() => typeof route.query.workspaceId === 'string'
  ? route.query.workspaceId
  : '')
const actions = computed(() => selected.value
  ? workspaceActions(selected.value.state, Boolean(selected.value.releaseImport))
  : workspaceActions('DISCARDED'))
const dirty = computed(() => Boolean(selected.value)
  && (editorContent.value !== originalContent.value
    || (creatingResource.value && Boolean(editorPath.value.trim()))))
const pathError = computed(() => workspaceResourcePathError(editorPath.value))
const currentValidation = computed(() => selected.value ? isCurrentValidation(selected.value) : false)
const productionMode = computed(() => props.capabilities['authoring.production.apply'] === 'supported')
const canPublish = computed(() => Boolean(selected.value
  && !productionMode.value
  && canPublishWorkspace(selected.value)))
const canExport = computed(() => Boolean(selected.value
  && canExportRelease(selected.value, props.capabilities)))
const canPromote = computed(() => Boolean(selected.value
  && canPromoteRelease(selected.value, props.capabilities)))
const canRollback = computed(() => Boolean(selected.value
  && canRollbackRelease(selected.value, props.capabilities)))
const canRecoverRollback = computed(() => Boolean(selected.value
  && actions.value.recoverRollback
  && props.capabilities['authoring.production.rollback'] === 'supported'
  && rollbackWorkspaceRequest(selected.value)))
const canRecoverPublication = computed(() => Boolean(selected.value
  && selected.value.releaseImport
  && actions.value.recover
  && recoverWorkspaceRequest(selected.value)))
const nextWorkspaceBundle = computed(() => selected.value
  ? eligibleBundles.value.find(bundle => bundle.name === selected.value?.sourceBundle)
  : undefined)
const validation = computed(() => selected.value?.lastValidation || null)
const validationRows = computed(() => normalizeResultRows(validation.value?.issues || []))
const queryRows = computed(() => normalizeResultRows(queryResult.value?.response?.items || []))
const selectedQmSuggestion = computed(() => selectedResource.value?.type === 'QM'
  ? suggestedModelName(selectedResource.value.path)
  : '')
const stateExplanation = computed(() => {
  if (!selected.value) return ''
  if (selected.value.state === 'STALE') return '源 revision 已漂移。可读取、比较和迁移草稿，但不能 validate/query；请新建 workspace。'
  if (selected.value.state === 'PUBLISHING') return 'Runtime 正在处理 durable publication attempt。仅可读取 evidence 或显式刷新 metadata。'
  if (selected.value.state === 'RECOVERY_REQUIRED') return '发布未能安全收敛。仅可按服务端记录的 exact attempt/candidate 执行失败恢复。'
  if (selected.value.state === 'ROLLING_BACK') return 'Rollback intent 已持久化。Console 关闭其他 mutation，只允许显式刷新 authoritative metadata。'
  if (selected.value.state === 'ROLLBACK_REQUIRED') return 'Rollback 未能证明收敛。仅可对同 package/candidate/apply attempt 执行 pinned forward recovery。'
  if (selected.value.state === 'ROLLED_BACK') return '该 apply attempt 的直接前一 base 已恢复；workspace 已终结，candidate artifact 继续保留。'
  if (selected.value.state === 'PUBLISHED') return 'Exact candidate 已发布并固定为 immutable terminal workspace；继续修改需创建新 workspace。'
  if (selected.value.state === 'DISCARDED') return '该 workspace 已终结，只保留 metadata，不再允许资源、验证或查询操作。'
  if (selected.value.state === 'VALIDATED') return '当前 exact candidate revision 已完成全量校验，可以执行 candidate query。'
  return '草稿尚未完成当前 revision 的全量校验。保存与校验是两个独立动作。'
})

async function exportReleasePackage(): Promise<void> {
  if (!selected.value || !canExport.value || busy.value) return
  const workspace = selected.value
  try {
    await ElMessageBox.confirm(
      `导出 release package：${workspace.targetNamespace} / ${workspace.sourceBundle}\nCandidate: ${workspace.candidateRevision}\n\n文件只包含 exact TM/QM/FSScript 与安全 provenance；不含数据、权限或签名。`,
      '确认导出 exact release package',
      { type: 'warning', confirmButtonText: '下载 JSON package', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  busy.value = 'release-export'
  clearOperationError()
  try {
    const release = await runtimeApi.post<AuthoringReleasePackage>(
      `authoring/workspaces/${encodeURIComponent(workspace.workspaceId)}/release-package`,
      { expectedCandidateRevision: workspace.candidateRevision }
    )
    const url = URL.createObjectURL(new Blob([JSON.stringify(release, null, 2)], { type: 'application/json' }))
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = releasePackageFilename(release.sourceBundle, release.candidateRevision)
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
    ElMessage.success(`Release package 已下载：${shortRevision(release.packageId)}`)
  } catch (error) {
    operationError.value = error instanceof RuntimeRequestError ? error : null
    ElMessage.error(errorText(error, '导出 release package 失败。'))
  } finally {
    busy.value = ''
  }
}

async function importReleasePackage(release: AuthoringReleasePackage, targetBundle: string): Promise<void> {
  if (props.capabilities['authoring.releasePackage.import'] !== 'supported' || busy.value) return
  try {
    await ElMessageBox.confirm(
      `导入 package：${release.packageId}\nSource: ${release.sourceNamespace} / ${release.sourceBundle}\nTarget: ${props.namespace || '空 Namespace'} / ${targetBundle}\nCandidate: ${release.candidateRevision}\nResources: ${release.resources.length}\n\n开发 validation 只作 provenance；导入后不会自动 apply。`,
      '确认导入只读 production candidate',
      { type: 'warning', confirmButtonText: '导入到明确 target', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  busy.value = 'release-import'
  clearOperationError()
  try {
    const imported = await runtimeApi.post<AuthoringWorkspaceInfo>('authoring/releases/import', {
      namespace: props.namespace,
      targetBundle,
      releasePackage: release
    })
    workspaces.value.unshift(imported)
    await openWorkspace(imported.workspaceId)
    ElMessage.success('Release package 已导入为 immutable production candidate；请重新 validate/query。')
  } catch (error) {
    operationError.value = error instanceof RuntimeRequestError ? error : null
    ElMessage.error(errorText(error, '导入 release package 失败。'))
  } finally {
    busy.value = ''
  }
}

async function promoteRelease(): Promise<void> {
  if (!selected.value || !canPromote.value || busy.value) return
  const workspace = selected.value
  try {
    await ElMessageBox.confirm(
      `Apply package: ${workspace.releaseImport?.packageId}\nCandidate: ${workspace.candidateRevision}\nTarget: ${workspace.targetNamespace} / ${workspace.sourceBundle}\nBase Bundle: ${workspace.baseBundleRevision}\nBase Namespace Source: ${workspace.baseNamespaceSourceRevision}\n\n这会切换生产 Bundle source 并刷新整个 Namespace catalog。`,
      '确认生产 apply exact package',
      { type: 'warning', confirmButtonText: 'Apply exact package', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  clearOperationError()
  try {
    const promoted = await publication.promote(workspace)
    replaceWorkspaceMetadata(promoted)
    queryResult.value = null
    ElMessage.success(`Production apply 已完成：${promoted.state}`)
  } catch (error) {
    operationError.value = error instanceof RuntimeRequestError ? error : null
    ElMessage.error(errorText(error, 'Production apply 失败；Console 未自动重试或 fallback。'))
  }
}

async function rollbackRelease(): Promise<void> {
  if (!selected.value || !canRollback.value || busy.value) return
  const workspace = selected.value
  const request = rollbackWorkspaceRequest(workspace)
  if (!request) return
  try {
    await ElMessageBox.confirm(
      `Rollback package: ${request.releasePackageId}\nCandidate: ${request.expectedCandidateRevision}\nApply attempt: ${request.publicationAttemptId}\nDirect previous base: ${workspace.baseBundleRevision}\n\n只恢复该 attempt 的直接前一 base，不提供任意历史选择。`,
      '确认一步 pinned rollback',
      { type: 'warning', confirmButtonText: 'Rollback 到直接前一 base', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  clearOperationError()
  try {
    const rolledBack = await publication.rollback(workspace)
    replaceWorkspaceMetadata(rolledBack)
    queryResult.value = null
    ElMessage.success(`Rollback 已完成：${rolledBack.state}`)
  } catch (error) {
    operationError.value = error instanceof RuntimeRequestError ? error : null
    ElMessage.error(errorText(error, 'Rollback 失败；请显式刷新 authoritative metadata。'))
  }
}

async function recoverRollback(): Promise<void> {
  if (!selected.value || !canRecoverRollback.value || busy.value) return
  const workspace = selected.value
  const request = rollbackWorkspaceRequest(workspace)
  if (!request) return
  try {
    await ElMessageBox.confirm(
      `Forward recover package: ${request.releasePackageId}\nCandidate: ${request.expectedCandidateRevision}\nApply attempt: ${request.publicationAttemptId}\n\n只恢复同一 pinned candidate；不会覆盖第三方 drift。`,
      '确认 pinned rollback recovery',
      { type: 'warning', confirmButtonText: '恢复 pinned candidate', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  clearOperationError()
  try {
    const recovered = await publication.recoverRollback(workspace)
    replaceWorkspaceMetadata(recovered)
    ElMessage.success(`Rollback recovery 已完成：${recovered.state}`)
  } catch (error) {
    operationError.value = error instanceof RuntimeRequestError ? error : null
    ElMessage.error(errorText(error, 'Rollback recovery 失败；Console 未自动重试。'))
  }
}

function errorText(error: unknown, fallback: string): string {
  return error instanceof RuntimeRequestError ? error.message : fallback
}

function clearOperationError(): void {
  operationError.value = null
  conflictServerContent.value = null
}

function clearEditor(): void {
  selectedResource.value = null
  editorPath.value = ''
  editorContent.value = ''
  originalContent.value = ''
  creatingResource.value = false
  conflictServerContent.value = null
}

function resetWorkspaceView(): void {
  selected.value = null
  resources.value = []
  diffResult.value = null
  queryResult.value = null
  clearEditor()
  clearOperationError()
}

function routeWithWorkspace(workspaceId?: string): Record<string, string | undefined> {
  return {
    ns: props.namespace,
    workspaceId: workspaceId || undefined
  }
}

async function writeWorkspaceRoute(workspaceId?: string): Promise<void> {
  await router.replace({
    name: 'namespaces',
    params: { workspace: 'authoring' },
    query: routeWithWorkspace(workspaceId)
  })
}

async function confirmDiscardDirty(): Promise<boolean> {
  if (!dirty.value) return true
  return window.confirm('当前资源有未保存修改。离开将丢失这些浏览器内草稿，是否继续？')
}

onBeforeRouteUpdate(() => confirmDiscardDirty())
onBeforeRouteLeave(() => confirmDiscardDirty())

function beforeUnload(event: BeforeUnloadEvent): void {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

async function loadWorkspaceList(preferredId = workspaceIdFromRoute.value): Promise<void> {
  const version = ++namespaceLoadVersion
  busy.value = 'list'
  loadError.value = ''
  try {
    const result = await runtimeApi.get<AuthoringWorkspaceListResponse>('authoring/workspaces', {
      namespace: props.namespace
    })
    if (version !== namespaceLoadVersion) return
    workspaces.value = result.workspaces || []
    workspaceWarnings.value = result.warnings || []
    const desired = preferredId
      ? workspaces.value.find(item => item.workspaceId === preferredId)
      : null
    if (desired) {
      await openWorkspace(desired.workspaceId, false)
    } else {
      resetWorkspaceView()
      if (preferredId) await writeWorkspaceRoute()
    }
  } catch (error) {
    if (version !== namespaceLoadVersion) return
    loadError.value = errorText(error, '无法读取 authoring workspace。')
    resetWorkspaceView()
  } finally {
    if (version === namespaceLoadVersion) busy.value = ''
  }
}

function replaceWorkspaceMetadata(workspace: AuthoringWorkspaceInfo): void {
  selected.value = workspace
  const index = workspaces.value.findIndex(item => item.workspaceId === workspace.workspaceId)
  if (index >= 0) workspaces.value.splice(index, 1, workspace)
  else workspaces.value.unshift(workspace)
}

async function loadResources(workspace: AuthoringWorkspaceInfo): Promise<void> {
  const version = ++resourceListVersion
  const result = await runtimeApi.get<AuthoringResourcesResponse>(
    `authoring/workspaces/${encodeURIComponent(workspace.workspaceId)}/resources`,
    { candidateRevision: workspace.candidateRevision }
  )
  if (version !== resourceListVersion || selected.value?.workspaceId !== workspace.workspaceId) return
  resources.value = result.resources || []
}

async function openWorkspace(workspaceId: string, updateRoute = true): Promise<void> {
  if (selected.value?.workspaceId !== workspaceId && !(await confirmDiscardDirty())) return
  const version = ++workspaceOpenVersion
  busy.value = 'workspace'
  clearOperationError()
  try {
    const workspace = await runtimeApi.get<AuthoringWorkspaceInfo>(
      `authoring/workspaces/${encodeURIComponent(workspaceId)}`
    )
    if (version !== workspaceOpenVersion) return
    resetWorkspaceView()
    replaceWorkspaceMetadata(workspace)
    if (updateRoute) await writeWorkspaceRoute(workspace.workspaceId)
    if (workspace.state !== 'DISCARDED') await loadResources(workspace)
  } catch (error) {
    if (version !== workspaceOpenVersion) return
    operationError.value = error instanceof RuntimeRequestError ? error : null
    ElMessage.error(errorText(error, '无法打开 workspace。'))
  } finally {
    if (version === workspaceOpenVersion) busy.value = ''
  }
}

async function createWorkspace(bundle: BundleItem): Promise<void> {
  if (bundle.workspaceEligible !== true) return
  busy.value = 'create'
  clearOperationError()
  try {
    const workspace = await runtimeApi.post<AuthoringWorkspaceInfo>('authoring/workspaces', {
      namespace: props.namespace,
      sourceBundle: bundle.name
    })
    workspaces.value.unshift(workspace)
    ElMessage.success(`已从 ${bundle.name} 创建隔离草稿。`)
    await openWorkspace(workspace.workspaceId)
  } catch (error) {
    operationError.value = error instanceof RuntimeRequestError ? error : null
    ElMessage.error(errorText(error, '创建 workspace 失败。'))
  } finally {
    busy.value = ''
  }
}

async function publishWorkspace(): Promise<void> {
  if (!selected.value || !canPublish.value || dirty.value || busy.value) return
  const workspace = selected.value
  try {
    await ElMessageBox.confirm(
      `发布到开发 Runtime：${workspace.targetNamespace} / ${workspace.sourceBundle}\nCandidate: ${workspace.candidateRevision}\nBase Bundle: ${workspace.baseBundleRevision}\nBase Namespace Source: ${workspace.baseNamespaceSourceRevision}\n\nRuntime 将创建 immutable artifact、切换 Bundle source 并刷新整个 Namespace catalog。该动作不会自动回滚，也不代表生产 promotion。`,
      '确认发布 exact candidate revision',
      { type: 'warning', confirmButtonText: '发布 exact revision', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  clearOperationError()
  try {
    const published = await publication.publish(workspace)
    replaceWorkspaceMetadata(published)
    queryResult.value = null
    ElMessage.success(published.state === 'PUBLISHED'
      ? 'Exact candidate 已发布；workspace 进入 immutable terminal state。'
      : `Publication attempt 已受理；当前状态：${published.state}。`)
  } catch (error) {
    operationError.value = error instanceof RuntimeRequestError ? error : null
    ElMessage.error(errorText(error, '发布 workspace 失败。请读取服务端 publication 状态。'))
    await refreshPublicationMetadata(false)
  }
}

async function recoverPublication(): Promise<void> {
  if (!selected.value || !actions.value.recover || busy.value) return
  const workspace = selected.value
  const request = recoverWorkspaceRequest(workspace)
  if (!request) {
    ElMessage.error('服务端 publication attempt 缺失或与当前 candidate 不匹配；恢复已阻止。')
    return
  }
  try {
    await ElMessageBox.confirm(
      `恢复 failed publication attempt：${request.publicationAttemptId}\nTarget: ${workspace.targetNamespace} / ${workspace.sourceBundle}\nCandidate: ${request.expectedCandidateRevision}\n\n恢复只回到该 attempt 记录的旧 live source/catalog，不是成功发布后的历史 rollback。`,
      '确认恢复失败发布',
      { type: 'warning', confirmButtonText: '恢复 exact attempt', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  clearOperationError()
  try {
    const recovered = await publication.recover(workspace)
    replaceWorkspaceMetadata(recovered)
    queryResult.value = null
    ElMessage.success('Failed publication attempt 已恢复；workspace 当前为 STALE。')
  } catch (error) {
    operationError.value = error instanceof RuntimeRequestError ? error : null
    ElMessage.error(errorText(error, '恢复 failed publication attempt 失败。'))
  }
}

async function refreshPublicationMetadata(showSuccess = true): Promise<void> {
  if (!selected.value) return
  const workspaceId = selected.value.workspaceId
  try {
    const current = await publication.refresh(workspaceId)
    replaceWorkspaceMetadata(current)
    if (showSuccess) ElMessage.success(`Publication 状态已刷新：${current.state}`)
  } catch (error) {
    if (showSuccess) {
      operationError.value = error instanceof RuntimeRequestError ? error : operationError.value
      ElMessage.error(errorText(error, '刷新 publication 状态失败。'))
    }
  }
}

async function createNextWorkspace(): Promise<void> {
  if (!selected.value || selected.value.state !== 'PUBLISHED' || !nextWorkspaceBundle.value) return
  await createWorkspace(nextWorkspaceBundle.value)
}

async function selectResource(resource: AuthoringResource): Promise<void> {
  if (!selected.value || !(await confirmDiscardDirty())) return
  const workspace = selected.value
  const version = ++resourceLoadVersion
  busy.value = 'resource'
  clearOperationError()
  try {
    const content = await runtimeApi.get<AuthoringResource>(
      `authoring/workspaces/${encodeURIComponent(workspace.workspaceId)}/resources/content`,
      { path: resource.path, candidateRevision: workspace.candidateRevision }
    )
    if (version !== resourceLoadVersion || selected.value?.candidateRevision !== workspace.candidateRevision) return
    selectedResource.value = content
    editorPath.value = content.path
    editorContent.value = content.content || ''
    originalContent.value = editorContent.value
    creatingResource.value = false
    if (content.type === 'QM' && !queryModel.value) queryModel.value = suggestedModelName(content.path)
  } catch (error) {
    operationError.value = error instanceof RuntimeRequestError ? error : null
    ElMessage.error(errorText(error, '读取 workspace 资源失败。'))
  } finally {
    if (version === resourceLoadVersion) busy.value = ''
  }
}

async function startNewResource(): Promise<void> {
  if (!(await confirmDiscardDirty())) return
  clearOperationError()
  selectedResource.value = null
  editorPath.value = ''
  editorContent.value = ''
  originalContent.value = ''
  creatingResource.value = true
}

async function refreshMetadataPreservingDraft(inspectConflictContent: boolean): Promise<void> {
  if (!selected.value) return
  const workspaceId = selected.value.workspaceId
  try {
    const current = await runtimeApi.get<AuthoringWorkspaceInfo>(
      `authoring/workspaces/${encodeURIComponent(workspaceId)}`
    )
    replaceWorkspaceMetadata(current)
    if (inspectConflictContent && editorPath.value && current.state !== 'DISCARDED') {
      try {
        const server = await runtimeApi.get<AuthoringResource>(
          `authoring/workspaces/${encodeURIComponent(workspaceId)}/resources/content`,
          { path: editorPath.value, candidateRevision: current.candidateRevision }
        )
        conflictServerContent.value = server.content || ''
      } catch {
        conflictServerContent.value = '∅（服务端当前 revision 已无此资源）'
      }
    }
    const list = await runtimeApi.get<AuthoringResourcesResponse>(
      `authoring/workspaces/${encodeURIComponent(workspaceId)}/resources`,
      { candidateRevision: current.candidateRevision }
    )
    resources.value = list.resources || []
  } catch (error) {
    ElMessage.error(errorText(error, '无法读取当前服务端 revision。'))
  }
}

async function reloadConflictMetadata(): Promise<void> {
  await refreshMetadataPreservingDraft(true)
}

async function handleMutationFailure(error: unknown, fallback: string): Promise<void> {
  operationError.value = error instanceof RuntimeRequestError ? error : null
  ElMessage.error(errorText(error, fallback))
  if (operationError.value?.code === 'WORKSPACE_REVISION_CONFLICT') {
    await reloadConflictMetadata()
  }
}

async function saveResource(): Promise<void> {
  if (!selected.value || !actions.value.mutate) return
  if (pathError.value) {
    ElMessage.warning(pathError.value)
    return
  }
  const workspace = selected.value
  busy.value = 'save'
  clearOperationError()
  try {
    const updated = await runtimeApi.post<AuthoringWorkspaceInfo>(
      `authoring/workspaces/${encodeURIComponent(workspace.workspaceId)}/resources/save`,
      {
        expectedCandidateRevision: workspace.candidateRevision,
        files: [{ path: editorPath.value.trim(), content: editorContent.value }]
      }
    )
    replaceWorkspaceMetadata(updated)
    originalContent.value = editorContent.value
    creatingResource.value = false
    conflictServerContent.value = null
    await loadResources(updated)
    const metadata = resources.value.find(item => item.path === editorPath.value.trim())
    if (metadata) selectedResource.value = { ...metadata, content: editorContent.value }
    diffResult.value = null
    queryResult.value = null
    ElMessage.success(`草稿已保存，新 revision：${shortRevision(updated.candidateRevision)}`)
  } catch (error) {
    await handleMutationFailure(error, '保存 workspace 资源失败。')
  } finally {
    busy.value = ''
  }
}

async function deleteResource(): Promise<void> {
  if (!selected.value || !selectedResource.value || !actions.value.mutate) return
  const workspace = selected.value
  const path = selectedResource.value.path
  try {
    await ElMessageBox.confirm(
      `从隔离草稿删除 ${path}？该动作不会修改 live Bundle。`,
      '确认删除 workspace 资源',
      { type: 'warning', confirmButtonText: '删除草稿资源', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  busy.value = 'delete'
  clearOperationError()
  try {
    const updated = await runtimeApi.post<AuthoringWorkspaceInfo>(
      `authoring/workspaces/${encodeURIComponent(workspace.workspaceId)}/resources/delete`,
      { expectedCandidateRevision: workspace.candidateRevision, paths: [path] }
    )
    replaceWorkspaceMetadata(updated)
    clearEditor()
    await loadResources(updated)
    diffResult.value = null
    queryResult.value = null
    ElMessage.success('草稿资源已删除；live Bundle 未改变。')
  } catch (error) {
    await handleMutationFailure(error, '删除 workspace 资源失败。')
  } finally {
    busy.value = ''
  }
}

async function discardWorkspace(): Promise<void> {
  if (!selected.value || !actions.value.discard) return
  if (!(await confirmDiscardDirty())) return
  const workspace = selected.value
  try {
    await ElMessageBox.confirm(
      `终结 workspace ${workspace.workspaceId}？目标 ${workspace.targetNamespace} / ${workspace.sourceBundle}，revision ${shortRevision(workspace.candidateRevision)}。live Bundle 不会改变。`,
      '确认 discard workspace',
      { type: 'warning', confirmButtonText: '终结隔离草稿', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  busy.value = 'discard'
  clearOperationError()
  try {
    const discarded = await runtimeApi.delete<AuthoringWorkspaceInfo>(
      `authoring/workspaces/${encodeURIComponent(workspace.workspaceId)}`,
      { expectedCandidateRevision: workspace.candidateRevision }
    )
    replaceWorkspaceMetadata(discarded)
    resources.value = []
    clearEditor()
    diffResult.value = null
    queryResult.value = null
    ElMessage.success('workspace 已终结；live source 与 catalog 未改变。')
  } catch (error) {
    await handleMutationFailure(error, 'Discard workspace 失败。')
  } finally {
    busy.value = ''
  }
}

async function loadDiff(): Promise<void> {
  if (!selected.value || !actions.value.diff) return
  const workspace = selected.value
  inspector.value = 'diff'
  busy.value = 'diff'
  clearOperationError()
  try {
    const result = await runtimeApi.post<AuthoringDiffResponse>(
      `authoring/workspaces/${encodeURIComponent(workspace.workspaceId)}/diff`,
      { candidateRevision: workspace.candidateRevision, includeContent: true }
    )
    if (selected.value?.candidateRevision !== workspace.candidateRevision) return
    diffResult.value = result
  } catch (error) {
    operationError.value = error instanceof RuntimeRequestError ? error : null
    ElMessage.error(errorText(error, '读取 workspace diff 失败。'))
  } finally {
    busy.value = ''
  }
}

async function validateWorkspace(): Promise<void> {
  if (!selected.value || !actions.value.validate) return
  const workspace = selected.value
  inspector.value = 'validate'
  busy.value = 'validate'
  clearOperationError()
  try {
    const updated = await runtimeApi.post<AuthoringWorkspaceInfo>(
      `authoring/workspaces/${encodeURIComponent(workspace.workspaceId)}/validate`,
      { candidateRevision: workspace.candidateRevision }
    )
    if (selected.value?.candidateRevision !== workspace.candidateRevision) return
    replaceWorkspaceMetadata(updated)
    ElMessage.success('当前 exact candidate revision 校验通过。')
  } catch (error) {
    operationError.value = error instanceof RuntimeRequestError ? error : null
    await refreshMetadataPreservingDraft(false)
    ElMessage.error(errorText(error, 'Workspace 校验失败。'))
  } finally {
    busy.value = ''
  }
}

function useQmSuggestion(): void {
  if (selectedQmSuggestion.value) queryModel.value = selectedQmSuggestion.value
}

async function runCandidateQuery(nextMode = queryMode.value): Promise<void> {
  if (!selected.value || !actions.value.query) return
  if (!queryModel.value.trim()) {
    ElMessage.warning('请输入候选 QM 的 canonical model name。')
    return
  }
  let request: Record<string, unknown>
  try {
    request = parseJsonObject(queryPayload.value, 'Candidate Query DSL JSON')
  } catch (error) {
    ElMessage.error((error as Error).message)
    return
  }
  const workspace = selected.value
  queryMode.value = nextMode
  inspector.value = 'query'
  busy.value = 'query'
  queryResult.value = null
  clearOperationError()
  try {
    const result = await runtimeApi.post<AuthoringQueryResponse>(
      `authoring/workspaces/${encodeURIComponent(workspace.workspaceId)}/query/${encodeURIComponent(queryModel.value.trim())}/${nextMode}`,
      { candidateRevision: workspace.candidateRevision, request }
    )
    if (selected.value?.candidateRevision !== workspace.candidateRevision) return
    queryResult.value = result
    ElMessage.success(nextMode === 'validate'
      ? 'Candidate query validate 完成。'
      : `Candidate query 返回 ${result.response?.items?.length || 0} 行。`)
  } catch (error) {
    operationError.value = error instanceof RuntimeRequestError ? error : null
    ElMessage.error(errorText(error, 'Candidate query 失败。'))
  } finally {
    busy.value = ''
  }
}

watch(() => props.namespace, () => {
  namespaceLoadVersion++
  workspaceOpenVersion++
  resourceListVersion++
  resourceLoadVersion++
  workspaces.value = []
  workspaceWarnings.value = []
  resetWorkspaceView()
  void loadWorkspaceList()
}, { immediate: true })

watch(workspaceIdFromRoute, next => {
  if (!next || next === selected.value?.workspaceId || busy.value === 'list') return
  if (workspaces.value.some(item => item.workspaceId === next)) void openWorkspace(next, false)
})

watch(selectedQmSuggestion, next => {
  if (next && !queryModel.value) queryModel.value = next
})

onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <section class="authoring-studio" aria-labelledby="authoring-title">
    <header class="authoring-manifest">
      <div>
        <span class="console-panel-kicker">AUTHORING / ISOLATED CANDIDATE</span>
        <h2 id="authoring-title">模型创作工作区</h2>
        <p>编辑持久草稿，按 exact revision 执行 diff、validate、candidate query 与受控 publish。只有 Runtime publish 原语可以改变开发环境 live Bundle/catalog。</p>
      </div>
      <div class="authoring-safety" aria-label="创作边界">
        <span><strong>{{ namespace || '空 Namespace' }}</strong> TARGET NS</span>
        <span><strong>{{ eligibleBundles.length }}</strong> ELIGIBLE BUNDLES</span>
        <span><strong>NO</strong> AUTO-SAVE</span>
        <span><strong>EXACT</strong> PUBLISH</span>
      </div>
    </header>

    <div v-if="loadError" class="notice error-notice" role="alert">{{ loadError }}</div>
    <div v-for="warning in workspaceWarnings" :key="warning" class="notice">{{ warning }}</div>
    <div v-if="operationError" class="authoring-error" role="alert">
      <div>
        <span>{{ operationError.code }} · {{ operationError.phase || 'workspace' }}</span>
        <strong>{{ operationError.message }}</strong>
        <small v-if="operationError.path">RESOURCE / {{ operationError.path }}</small>
        <small v-if="operationError.safeToAutoRepair">可安全刷新 metadata；Console 不会自动重试 mutation。</small>
        <p>{{ operationError.suggestedNextAction || '检查当前 workspace 状态与 revision 后显式重试。' }}</p>
      </div>
      <button
        v-if="operationError.code === 'WORKSPACE_REVISION_CONFLICT'"
        class="console-button compact"
        type="button"
        @click="reloadConflictMetadata"
      >读取服务端 revision</button>
      <button
        v-else-if="operationError.code === 'WORKSPACE_RECOVERY_REQUIRED' || operationError.code === 'WORKSPACE_ROLLBACK_REQUIRED'"
        class="console-button compact"
        type="button"
        @click="refreshPublicationMetadata()"
      >显式刷新服务端状态</button>
    </div>

    <WorkspaceReleasePromotion
      :namespace="namespace"
      :bundles="bundles"
      :capabilities="capabilities"
      :workspace="selected"
      :can-export="canExport"
      :can-promote="canPromote"
      :can-rollback="canRollback"
      :can-recover-rollback="canRecoverRollback"
      :can-recover-publication="canRecoverPublication"
      :busy="busy"
      @export-release="exportReleasePackage"
      @import-release="importReleasePackage"
      @promote="promoteRelease"
      @rollback="rollbackRelease"
      @recover-rollback="recoverRollback"
      @recover-publication="recoverPublication"
      @refresh="refreshPublicationMetadata()"
    />

    <div class="authoring-layout">
      <WorkspaceCatalog
        :workspaces="workspaces"
        :selected-workspace-id="selected?.workspaceId"
        :bundles="bundles"
        :busy="busy"
        @refresh="loadWorkspaceList()"
        @open="openWorkspace"
        @create="createWorkspace"
      />

      <main class="workspace-stage">
        <div v-if="!selected" class="workspace-zero-state">
          <span>NO WORKSPACE SELECTED</span>
          <h3>从一个明确可编辑的 Bundle 开始</h3>
          <p>Runtime 会复制完整 TM/QM/FSScript 草稿快照。JAR、classpath 和 configured external 只作为依赖，不进入编辑。</p>
        </div>

        <template v-else>
          <header class="workspace-revision-bar">
            <div>
              <span>{{ selected.targetNamespace || 'EMPTY NS' }} / {{ selected.sourceBundle }}</span>
              <h3>{{ selected.workspaceId }}</h3>
              <p>{{ stateExplanation }}</p>
            </div>
            <div class="revision-stamp">
              <span :class="['status-chip', selected.state === 'STALE' || selected.state === 'DISCARDED' ? 'warning' : '']">{{ selected.state }}</span>
              <code :title="selected.candidateRevision">{{ shortRevision(selected.candidateRevision) }}</code>
              <small>CANDIDATE HEAD</small>
            </div>
          </header>

          <dl class="workspace-facts">
            <div><dt>BASE BUNDLE</dt><dd :title="selected.baseBundleRevision">{{ shortRevision(selected.baseBundleRevision) }}</dd></div>
            <div><dt>BASE NAMESPACE</dt><dd :title="selected.baseNamespaceSourceRevision">{{ shortRevision(selected.baseNamespaceSourceRevision) }}</dd></div>
            <div><dt>SOURCE KIND</dt><dd>{{ selected.sourceKind }}</dd></div>
            <div><dt>UPDATED</dt><dd>{{ selected.updatedAt }}</dd></div>
          </dl>

          <div v-if="selected.diagnostics?.length" class="workspace-diagnostics">
            <span v-for="item in selected.diagnostics" :key="item">{{ item }}</span>
          </div>

          <div v-if="selected.state === 'DISCARDED'" class="workspace-zero-state terminal">
            <span>TERMINAL STATE</span>
            <h3>Workspace 已终结</h3>
            <p>资源内容、diff、validate 和 query 操作已关闭。该 identity 仅用于识别历史 tombstone。</p>
          </div>

          <ResourceEditor
            v-else
            v-model:editor-path="editorPath"
            v-model:editor-content="editorContent"
            :resources="resources"
            :selected-resource="selectedResource"
            :creating-resource="creatingResource"
            :dirty="dirty"
            :path-error="pathError"
            :can-mutate="actions.mutate"
            :busy="busy"
            :candidate-revision="selected.candidateRevision"
            :conflict-server-content="conflictServerContent"
            @new="startNewResource"
            @select="selectResource"
            @save="saveResource"
            @delete="deleteResource"
          />

          <CandidateInspector
            v-if="selected.state !== 'DISCARDED'"
            v-model:inspector="inspector"
            v-model:query-model="queryModel"
            v-model:query-payload="queryPayload"
            :actions="actions"
            :busy="busy"
            :dirty="dirty"
            :diff-result="diffResult"
            :validation="validation"
            :validation-rows="validationRows"
            :current-validation="currentValidation"
            :selected-qm-suggestion="selectedQmSuggestion"
            :query-result="queryResult"
            :query-rows="queryRows"
            @diff="loadDiff"
            @validate="validateWorkspace"
            @query="runCandidateQuery"
            @use-suggestion="useQmSuggestion"
          />

          <WorkspacePublication
            v-if="selected.state !== 'DISCARDED' && !selected.releaseImport && !productionMode"
            :workspace="selected"
            :actions="actions"
            :can-publish="canPublish"
            :dirty="dirty"
            :busy="busy"
            :next-bundle="nextWorkspaceBundle"
            @publish="publishWorkspace"
            @recover="recoverPublication"
            @refresh="refreshPublicationMetadata()"
            @create-next="createNextWorkspace"
          />

          <footer v-if="actions.discard" class="workspace-terminal-actions">
            <div><span>TERMINAL ACTION</span><p>Discard 只终结隔离 workspace，不删除或修改 live Bundle。</p></div>
            <button class="console-button danger" type="button" :disabled="!actions.discard || Boolean(busy)" @click="discardWorkspace">Discard workspace</button>
          </footer>
        </template>
      </main>
    </div>
  </section>
</template>

<style scoped>
.authoring-studio {
  border: 1px solid var(--console-line-strong);
  background: var(--console-panel);
}

.authoring-manifest {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(360px, .9fr);
  border-bottom: 1px solid var(--console-line-strong);
  background:
    linear-gradient(var(--console-grid-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--console-grid-line) 1px, transparent 1px),
    var(--console-panel);
  background-size: 24px 24px;
}

.authoring-manifest > div:first-child { padding: 24px; }
.authoring-manifest h2 { margin: 8px 0; font-size: 24px; letter-spacing: -.03em; }
.authoring-manifest p,
.workspace-revision-bar p,
.workspace-zero-state p,
.workspace-terminal-actions p,
.authoring-error p { margin: 0; color: var(--console-muted); font-size: 12px; line-height: 1.65; }

.authoring-safety { display: grid; grid-template-columns: 1fr 1fr; gap: 1px; background: var(--console-line); }
.authoring-safety span { display: flex; justify-content: space-between; flex-direction: column; padding: 18px; background: var(--console-panel-2); color: var(--console-dim); font: 9px/1.2 var(--console-mono); }
.authoring-safety strong { margin-bottom: 13px; color: var(--console-text); font-size: 15px; }

.authoring-error { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 14px 18px; border-bottom: 1px solid var(--console-line-strong); background: repeating-linear-gradient(135deg, var(--console-panel-2) 0 8px, var(--console-bg) 8px 9px); }
.authoring-error span, .authoring-error small { display: block; color: var(--console-dim); font: 10px/1.5 var(--console-mono); }
.authoring-error strong { display: block; margin: 4px 0; }

.authoring-layout { display: grid; grid-template-columns: 260px minmax(0, 1fr); min-height: 620px; }

.workspace-stage { min-width: 0; }
.workspace-zero-state { min-height: 280px; display: flex; align-items: flex-start; justify-content: center; flex-direction: column; padding: 48px; background: repeating-linear-gradient(135deg, transparent 0 14px, var(--console-hatch-line) 14px 15px); }
.workspace-zero-state span { color: var(--console-dim); font: 700 10px/1 var(--console-mono); }
.workspace-zero-state h3 { max-width: 620px; margin: 12px 0; font-size: 28px; letter-spacing: -.04em; }
.workspace-zero-state p { max-width: 640px; }
.workspace-zero-state.terminal { min-height: 360px; border-bottom: 1px solid var(--console-line-strong); }

.workspace-revision-bar { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; padding: 20px; border-bottom: 1px solid var(--console-line-strong); }
.workspace-revision-bar > div:first-child { min-width: 0; }
.workspace-revision-bar span { color: var(--console-dim); font: 700 9px/1 var(--console-mono); }
.workspace-revision-bar h3 { margin: 8px 0; overflow: hidden; font: 16px/1.3 var(--console-mono); text-overflow: ellipsis; }
.revision-stamp { min-width: 170px; display: grid; justify-items: end; gap: 7px; }
.revision-stamp code { font: 700 12px/1 var(--console-mono); }
.revision-stamp small { color: var(--console-dim); font: 8px/1 var(--console-mono); }
.workspace-facts { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 0; border-bottom: 1px solid var(--console-line-strong); background: var(--console-line); gap: 1px; }
.workspace-facts div { min-width: 0; padding: 12px; background: var(--console-panel-2); }
.workspace-facts dt { color: var(--console-dim); font: 8px/1 var(--console-mono); }
.workspace-facts dd { margin: 7px 0 0; overflow: hidden; font: 10px/1.3 var(--console-mono); text-overflow: ellipsis; white-space: nowrap; }
.workspace-diagnostics { padding: 10px 14px; border-bottom: 1px solid var(--console-line); }
.workspace-diagnostics span { display: block; color: var(--console-muted); font: 10px/1.5 var(--console-mono); }

.workspace-terminal-actions { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 16px; background: repeating-linear-gradient(135deg, transparent 0 11px, var(--console-hatch-line) 11px 12px); }
.workspace-terminal-actions span { color: var(--console-dim); font: 700 9px/1 var(--console-mono); }

@media (max-width: 1080px) {
  .authoring-layout { grid-template-columns: 220px minmax(0, 1fr); }
  .workspace-facts { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 760px) {
  .authoring-manifest, .authoring-layout { grid-template-columns: 1fr; }
  .authoring-safety { border-top: 1px solid var(--console-line-strong); }
  .workspace-revision-bar, .workspace-terminal-actions, .authoring-error { align-items: stretch; flex-direction: column; }
  .revision-stamp { min-width: 0; justify-items: start; }
  .workspace-facts { grid-template-columns: 1fr 1fr; }
  .workspace-zero-state { min-height: 240px; padding: 28px 18px; }
  .workspace-zero-state h3 { font-size: 22px; }
}
</style>
