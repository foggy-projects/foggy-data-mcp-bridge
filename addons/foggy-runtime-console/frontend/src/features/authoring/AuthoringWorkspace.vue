<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import RuntimeResultTable from '@/components/RuntimeResultTable.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { normalizeResultRows, parseJsonObject, prettyJson } from '@/utils/json'
import type { BundleItem } from '@/features/namespace/types'
import {
  isCurrentValidation,
  shortRevision,
  suggestedModelName,
  workspaceActions,
  workspaceResourcePathError
} from './authoringWorkspace'
import type {
  AuthoringDiffResponse,
  AuthoringQueryResponse,
  AuthoringResource,
  AuthoringResourcesResponse,
  AuthoringWorkspaceInfo,
  AuthoringWorkspaceListResponse
} from './types'

const props = defineProps<{
  namespace: string
  bundles: BundleItem[]
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
  ? workspaceActions(selected.value.state)
  : workspaceActions('DISCARDED'))
const dirty = computed(() => Boolean(selected.value)
  && (editorContent.value !== originalContent.value
    || (creatingResource.value && Boolean(editorPath.value.trim()))))
const pathError = computed(() => workspaceResourcePathError(editorPath.value))
const currentValidation = computed(() => selected.value ? isCurrentValidation(selected.value) : false)
const validation = computed(() => selected.value?.lastValidation || null)
const validationRows = computed(() => normalizeResultRows(validation.value?.issues || []))
const queryRows = computed(() => normalizeResultRows(queryResult.value?.response?.items || []))
const selectedQmSuggestion = computed(() => selectedResource.value?.type === 'QM'
  ? suggestedModelName(selectedResource.value.path)
  : '')
const stateExplanation = computed(() => {
  if (!selected.value) return ''
  if (selected.value.state === 'STALE') return '源 revision 已漂移。可读取、比较和迁移草稿，但不能 validate/query；请新建 workspace。'
  if (selected.value.state === 'DISCARDED') return '该 workspace 已终结，只保留 metadata，不再允许资源、验证或查询操作。'
  if (selected.value.state === 'VALIDATED') return '当前 exact candidate revision 已完成全量校验，可以执行 candidate query。'
  return '草稿尚未完成当前 revision 的全量校验。保存与校验是两个独立动作。'
})

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
        <p>编辑持久草稿，按 exact revision 执行 diff、validate 和 candidate query。这里没有 publish，live Bundle 与 catalog 不会改变。</p>
      </div>
      <div class="authoring-safety" aria-label="创作边界">
        <span><strong>{{ namespace || '空 Namespace' }}</strong> TARGET NS</span>
        <span><strong>{{ eligibleBundles.length }}</strong> ELIGIBLE BUNDLES</span>
        <span><strong>NO</strong> AUTO-SAVE</span>
        <span><strong>NO</strong> PUBLISH</span>
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
    </div>

    <div class="authoring-layout">
      <aside class="workspace-index" aria-label="Authoring workspace 列表">
        <div class="studio-section-head">
          <span>01 / WORKSPACES</span>
          <button class="console-button compact ghost" type="button" :disabled="Boolean(busy)" @click="loadWorkspaceList()">刷新</button>
        </div>

        <div v-if="busy === 'list'" class="studio-empty">正在读取 workspace…</div>
        <button
          v-for="item in workspaces"
          :key="item.workspaceId"
          type="button"
          class="workspace-ticket"
          :class="{ active: selected?.workspaceId === item.workspaceId }"
          :aria-pressed="selected?.workspaceId === item.workspaceId"
          @click="openWorkspace(item.workspaceId)"
        >
          <span>{{ item.state }}</span>
          <strong>{{ item.sourceBundle }}</strong>
          <code>{{ shortRevision(item.candidateRevision) }}</code>
          <small>{{ item.workspaceId }}</small>
        </button>
        <div v-if="!busy && !workspaces.length" class="studio-empty">当前 Namespace 没有 active workspace。</div>

        <div class="eligible-source-list">
          <div class="studio-section-head"><span>CREATE FROM SOURCE</span></div>
          <article v-for="bundle in bundles" :key="`${bundle.name}:${bundle.sourceIdentity || bundle.path}`" class="source-ticket">
            <div>
              <strong>{{ bundle.name }}</strong>
              <small>{{ bundle.sourceType || bundle.source || 'unknown' }}</small>
            </div>
            <span :class="['status-chip', bundle.workspaceEligible ? '' : 'warning']">
              {{ bundle.workspaceEligible ? 'ELIGIBLE' : 'READ ONLY' }}
            </span>
            <button
              class="console-button compact"
              type="button"
              :disabled="bundle.workspaceEligible !== true || Boolean(busy)"
              :title="bundle.workspaceEligible ? '创建隔离草稿' : '服务端未声明 workspaceEligible'"
              @click="createWorkspace(bundle)"
            >创建</button>
          </article>
          <div v-if="!bundles.length" class="studio-empty">当前 Namespace 没有 Bundle 来源。</div>
        </div>
      </aside>

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

          <div v-else class="resource-workbench">
            <aside class="resource-index" aria-label="Workspace 资源">
              <div class="studio-section-head">
                <span>02 / RESOURCES · {{ resources.length }}</span>
                <button class="console-button compact" type="button" :disabled="!actions.mutate || Boolean(busy)" @click="startNewResource">新建</button>
              </div>
              <button
                v-for="resource in resources"
                :key="resource.path"
                type="button"
                class="resource-line"
                :class="{ active: selectedResource?.path === resource.path && !creatingResource }"
                @click="selectResource(resource)"
              >
                <span>{{ resource.type }}</span>
                <strong>{{ resource.path }}</strong>
                <small>{{ resource.size }} B</small>
              </button>
              <div v-if="!resources.length" class="studio-empty">此 revision 没有资源。</div>
            </aside>

            <section class="resource-editor" aria-label="Workspace 资源编辑器">
              <div class="studio-section-head">
                <span>03 / {{ creatingResource ? 'NEW RESOURCE' : selectedResource?.type || 'EDITOR' }}</span>
                <div class="editor-state">
                  <span v-if="dirty" class="status-chip warning">UNSAVED</span>
                  <span v-else-if="selectedResource" class="status-chip">PINNED</span>
                </div>
              </div>
              <div v-if="creatingResource || selectedResource" class="editor-body">
                <label class="console-field">
                  <span class="console-label">Workspace 相对路径</span>
                  <input v-model="editorPath" class="console-input" aria-label="Workspace 资源路径" :disabled="!creatingResource" autocomplete="off">
                  <small v-if="pathError" class="field-error">{{ pathError }}</small>
                </label>
                <label class="console-field editor-content-field">
                  <span class="console-label">UTF-8 草稿内容</span>
                  <textarea
                    v-model="editorContent"
                    class="console-textarea authoring-code"
                    aria-label="Workspace 资源内容"
                    spellcheck="false"
                    :disabled="!actions.mutate"
                  />
                </label>
                <div v-if="conflictServerContent !== null" class="conflict-compare">
                  <div>
                    <span>LOCAL UNSAVED</span>
                    <pre>{{ editorContent }}</pre>
                  </div>
                  <div>
                    <span>SERVER / {{ shortRevision(selected.candidateRevision) }}</span>
                    <pre>{{ conflictServerContent }}</pre>
                  </div>
                  <p>本地草稿仍保留。请人工比较后，再决定是否基于当前服务端 revision 显式保存。</p>
                </div>
                <div class="editor-actions">
                  <button class="console-button primary" type="button" :disabled="!actions.mutate || !dirty || Boolean(pathError) || Boolean(busy)" @click="saveResource">
                    {{ busy === 'save' ? '保存中…' : '保存为新 revision' }}
                  </button>
                  <button v-if="selectedResource && !creatingResource" class="console-button danger" type="button" :disabled="!actions.mutate || Boolean(busy)" @click="deleteResource">删除草稿资源</button>
                </div>
              </div>
              <div v-else class="workspace-zero-state compact">
                <span>RESOURCE EDITOR</span>
                <h3>选择一个资源或新建文件</h3>
                <p>保存只推进 candidate revision，不会 validate、refresh 或 publish。</p>
              </div>
            </section>
          </div>

          <section v-if="selected.state !== 'DISCARDED'" class="candidate-inspector">
            <nav aria-label="Candidate 检查工具">
              <button type="button" :class="{ active: inspector === 'diff' }" @click="inspector = 'diff'">DIFF</button>
              <button type="button" :class="{ active: inspector === 'validate' }" @click="inspector = 'validate'">VALIDATE</button>
              <button type="button" :class="{ active: inspector === 'query' }" @click="inspector = 'query'">CANDIDATE QUERY</button>
            </nav>

            <div v-if="inspector === 'diff'" class="inspector-body">
              <div class="inspector-command">
                <div><span>IMMUTABLE BASE → PINNED CANDIDATE</span><p>按资源查看新增、修改与删除；这里不提供 merge/rebase。</p></div>
                <button class="console-button" type="button" :disabled="!actions.diff || Boolean(busy)" @click="loadDiff">{{ busy === 'diff' ? '读取中…' : '读取 exact diff' }}</button>
              </div>
              <div v-if="diffResult" class="diff-list">
                <article v-for="change in diffResult.changes" :key="change.path">
                  <header><span>{{ change.changeType }}</span><strong>{{ change.path }}</strong><code>{{ change.type }}</code></header>
                  <div class="diff-content">
                    <div><span>BASE · {{ shortRevision(change.baseSha256 || '') }}</span><pre>{{ change.baseContent ?? '∅' }}</pre></div>
                    <div><span>CANDIDATE · {{ shortRevision(change.candidateSha256 || '') }}</span><pre>{{ change.candidateContent ?? '∅' }}</pre></div>
                  </div>
                </article>
                <div v-if="!diffResult.changes.length" class="studio-empty">当前 candidate 与 immutable base 一致。</div>
              </div>
            </div>

            <div v-else-if="inspector === 'validate'" class="inspector-body">
              <div class="inspector-command">
                <div><span>FULL DETACHED VALIDATION</span><p>校验当前 exact candidate revision；失败 evidence 会在 workspace metadata 中保留。</p></div>
                <button class="console-button primary" type="button" :disabled="!actions.validate || dirty || Boolean(busy)" @click="validateWorkspace">{{ busy === 'validate' ? '校验中…' : '校验当前 revision' }}</button>
              </div>
              <div v-if="dirty" class="notice">请先显式保存当前资源，再校验 server-owned candidate revision。</div>
              <div v-if="validation" class="validation-evidence" :class="{ stale: !currentValidation }">
                <div><span>RESULT</span><strong>{{ validation.valid ? 'VALID' : 'INVALID' }}</strong></div>
                <div><span>TOTAL</span><strong>{{ validation.totalFiles }}</strong></div>
                <div><span>VALID</span><strong>{{ validation.validFiles }}</strong></div>
                <div><span>INVALID</span><strong>{{ validation.invalidFiles }}</strong></div>
                <div><span>CASCADE</span><strong>{{ validation.cascadingErrors }}</strong></div>
                <div><span>EVIDENCE</span><strong>{{ currentValidation ? 'CURRENT' : 'HISTORICAL' }}</strong></div>
              </div>
              <RuntimeResultTable v-if="validationRows.length" :rows="validationRows" />
              <div v-if="validation && !validationRows.length" class="studio-empty">Runtime 未返回 validation issue。</div>
            </div>

            <div v-else class="inspector-body query-inspector">
              <div class="inspector-command">
                <div><span>GOVERNED CANDIDATE QUERY</span><p>只查询当前已完整验证的 workspace revision；使用当前 Runtime 依赖、数据源与业务 Authorization。</p></div>
                <span :class="['status-chip', actions.query ? '' : 'warning']">{{ actions.query ? 'REVISION VALIDATED' : 'VALIDATE REQUIRED' }}</span>
              </div>
              <div class="query-form-grid">
                <label class="console-field">
                  <span class="console-label">QM canonical model name</span>
                  <input v-model="queryModel" class="console-input" aria-label="Candidate QM 模型" placeholder="OrderQueryModel" autocomplete="off">
                  <button v-if="selectedQmSuggestion" class="model-suggestion" type="button" @click="useQmSuggestion">使用文件名建议：{{ selectedQmSuggestion }}</button>
                </label>
                <label class="console-field query-payload-field">
                  <span class="console-label">Candidate Query DSL JSON</span>
                  <textarea v-model="queryPayload" class="console-textarea" aria-label="Candidate Query DSL JSON" spellcheck="false" />
                </label>
              </div>
              <div class="editor-actions">
                <button class="console-button" type="button" :disabled="!actions.query || dirty || Boolean(busy)" @click="runCandidateQuery('validate')">Validate query</button>
                <button class="console-button primary" type="button" :disabled="!actions.query || dirty || Boolean(busy)" @click="runCandidateQuery('execute')">Execute candidate</button>
              </div>
              <div v-if="queryResult" class="query-identity">
                <span>WORKSPACE <strong>{{ queryResult.workspaceId }}</strong></span>
                <span>REVISION <strong>{{ shortRevision(queryResult.candidateRevision) }}</strong></span>
                <span>PHASE <strong>{{ queryResult.phase }}</strong></span>
                <span>CATALOG <strong>{{ JSON.stringify(queryResult.catalogIdentity || {}) }}</strong></span>
              </div>
              <RuntimeResultTable v-if="queryRows.length" :rows="queryRows" />
              <div v-for="warning in queryResult?.response?.warnings || []" :key="warning" class="notice">{{ warning }}</div>
            </div>
          </section>

          <footer class="workspace-terminal-actions">
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
.inspector-command p,
.workspace-terminal-actions p,
.authoring-error p { margin: 0; color: var(--console-muted); font-size: 12px; line-height: 1.65; }

.authoring-safety { display: grid; grid-template-columns: 1fr 1fr; gap: 1px; background: var(--console-line); }
.authoring-safety span { display: flex; justify-content: space-between; flex-direction: column; padding: 18px; background: var(--console-panel-2); color: var(--console-dim); font: 9px/1.2 var(--console-mono); }
.authoring-safety strong { margin-bottom: 13px; color: var(--console-text); font-size: 15px; }

.authoring-error { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 14px 18px; border-bottom: 1px solid var(--console-line-strong); background: repeating-linear-gradient(135deg, var(--console-panel-2) 0 8px, var(--console-bg) 8px 9px); }
.authoring-error span, .authoring-error small { display: block; color: var(--console-dim); font: 10px/1.5 var(--console-mono); }
.authoring-error strong { display: block; margin: 4px 0; }

.authoring-layout { display: grid; grid-template-columns: 260px minmax(0, 1fr); min-height: 620px; }
.workspace-index { border-right: 1px solid var(--console-line-strong); background: var(--console-panel-2); }
.studio-section-head { min-height: 43px; display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 12px; border-bottom: 1px solid var(--console-line); color: var(--console-dim); font: 700 9px/1 var(--console-mono); letter-spacing: .08em; }
.workspace-ticket { width: 100%; display: grid; grid-template-columns: auto 1fr; gap: 7px 10px; padding: 13px; border: 0; border-bottom: 1px solid var(--console-line); background: var(--console-panel); color: var(--console-text); text-align: left; cursor: pointer; }
.workspace-ticket:hover, .workspace-ticket:focus-visible { background: var(--console-bg); }
.workspace-ticket.active { background: var(--console-paper); color: var(--console-inverse); }
.workspace-ticket span, .workspace-ticket code, .workspace-ticket small { font: 9px/1.3 var(--console-mono); opacity: .75; }
.workspace-ticket strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; }
.workspace-ticket code, .workspace-ticket small { grid-column: 1 / -1; overflow: hidden; text-overflow: ellipsis; }
.eligible-source-list { margin-top: 18px; border-top: 1px solid var(--console-line-strong); }
.source-ticket { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 9px; padding: 12px; border-bottom: 1px solid var(--console-line); }
.source-ticket div { min-width: 0; }
.source-ticket strong, .source-ticket small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.source-ticket strong { font-size: 12px; }
.source-ticket small { margin-top: 5px; color: var(--console-dim); font: 9px/1 var(--console-mono); }
.source-ticket .console-button { grid-column: 1 / -1; width: 100%; }
.studio-empty { padding: 18px; color: var(--console-dim); font: 11px/1.6 var(--console-mono); }

.workspace-stage { min-width: 0; }
.workspace-zero-state { min-height: 280px; display: flex; align-items: flex-start; justify-content: center; flex-direction: column; padding: 48px; background: repeating-linear-gradient(135deg, transparent 0 14px, var(--console-hatch-line) 14px 15px); }
.workspace-zero-state span { color: var(--console-dim); font: 700 10px/1 var(--console-mono); }
.workspace-zero-state h3 { max-width: 620px; margin: 12px 0; font-size: 28px; letter-spacing: -.04em; }
.workspace-zero-state p { max-width: 640px; }
.workspace-zero-state.compact { min-height: 340px; padding: 30px; }
.workspace-zero-state.compact h3 { font-size: 20px; }
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

.resource-workbench { display: grid; grid-template-columns: 250px minmax(0, 1fr); min-height: 440px; border-bottom: 1px solid var(--console-line-strong); }
.resource-index { border-right: 1px solid var(--console-line-strong); background: var(--console-panel-2); }
.resource-line { width: 100%; display: grid; grid-template-columns: 34px minmax(0, 1fr) auto; align-items: center; gap: 8px; padding: 11px 10px; border: 0; border-bottom: 1px solid var(--console-line); background: var(--console-panel); color: var(--console-text); text-align: left; cursor: pointer; }
.resource-line span, .resource-line small { color: var(--console-dim); font: 8px/1 var(--console-mono); }
.resource-line strong { overflow: hidden; font: 10px/1.3 var(--console-mono); text-overflow: ellipsis; }
.resource-line:hover, .resource-line:focus-visible { background: var(--console-bg); }
.resource-line.active { background: var(--console-paper); color: var(--console-inverse); }
.resource-line.active span, .resource-line.active small { color: inherit; opacity: .68; }
.resource-editor { min-width: 0; }
.editor-body { padding: 16px; }
.editor-state { display: flex; gap: 6px; }
.editor-content-field { margin-top: 13px; }
.authoring-code { min-height: 320px; resize: vertical; tab-size: 2; }
.field-error { color: var(--console-text); font: 10px/1.4 var(--console-mono); text-decoration: underline; }
.editor-actions { display: flex; flex-wrap: wrap; gap: 9px; margin-top: 12px; }
.conflict-compare { display: grid; grid-template-columns: 1fr 1fr; gap: 1px; margin-top: 14px; border: 1px solid var(--console-line-strong); background: var(--console-line-strong); }
.conflict-compare > div { min-width: 0; padding: 10px; background: var(--console-panel-2); }
.conflict-compare span, .diff-content span { color: var(--console-dim); font: 8px/1 var(--console-mono); }
.conflict-compare pre, .diff-content pre { min-height: 90px; max-height: 250px; margin: 8px 0 0; overflow: auto; white-space: pre-wrap; word-break: break-word; font: 10px/1.55 var(--console-mono); }
.conflict-compare p { grid-column: 1 / -1; margin: 0; padding: 10px; background: var(--console-panel); color: var(--console-muted); font: 10px/1.5 var(--console-mono); }

.candidate-inspector > nav { display: grid; grid-template-columns: repeat(3, 1fr); border-bottom: 1px solid var(--console-line-strong); }
.candidate-inspector > nav button { min-height: 45px; border: 0; border-right: 1px solid var(--console-line); background: var(--console-panel-2); color: var(--console-muted); font: 700 10px/1 var(--console-mono); cursor: pointer; }
.candidate-inspector > nav button:last-child { border-right: 0; }
.candidate-inspector > nav button.active { background: var(--console-paper); color: var(--console-inverse); }
.inspector-body { padding: 16px; border-bottom: 1px solid var(--console-line-strong); }
.inspector-command { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 14px; }
.inspector-command span { color: var(--console-dim); font: 700 9px/1 var(--console-mono); }
.diff-list { display: grid; gap: 10px; }
.diff-list article { border: 1px solid var(--console-line-strong); }
.diff-list header { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 12px; padding: 9px; border-bottom: 1px solid var(--console-line); background: var(--console-panel-2); }
.diff-list header span, .diff-list header code { font: 9px/1 var(--console-mono); }
.diff-list header strong { overflow: hidden; font: 11px/1 var(--console-mono); text-overflow: ellipsis; }
.diff-content { display: grid; grid-template-columns: 1fr 1fr; gap: 1px; background: var(--console-line); }
.diff-content > div { min-width: 0; padding: 10px; background: var(--console-panel); }
.validation-evidence { display: grid; grid-template-columns: repeat(6, 1fr); gap: 1px; margin-bottom: 12px; background: var(--console-line); }
.validation-evidence div { padding: 12px; background: var(--console-panel-2); }
.validation-evidence span, .validation-evidence strong { display: block; font-family: var(--console-mono); }
.validation-evidence span { color: var(--console-dim); font-size: 8px; }
.validation-evidence strong { margin-top: 8px; font-size: 12px; }
.validation-evidence.stale { opacity: .58; }
.query-form-grid { display: grid; grid-template-columns: minmax(180px, .4fr) minmax(0, 1fr); gap: 12px; }
.query-payload-field textarea { min-height: 180px; }
.model-suggestion { margin-top: 6px; border: 0; background: transparent; color: var(--console-muted); font: 9px/1.4 var(--console-mono); text-decoration: underline; cursor: pointer; text-align: left; }
.query-identity { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1px; margin: 14px 0; background: var(--console-line); }
.query-identity span { min-width: 0; padding: 10px; background: var(--console-panel-2); color: var(--console-dim); font: 8px/1.5 var(--console-mono); }
.query-identity strong { display: block; overflow: hidden; margin-top: 5px; color: var(--console-text); text-overflow: ellipsis; }
.workspace-terminal-actions { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 16px; background: repeating-linear-gradient(135deg, transparent 0 11px, var(--console-hatch-line) 11px 12px); }
.workspace-terminal-actions span { color: var(--console-dim); font: 700 9px/1 var(--console-mono); }

@media (max-width: 1080px) {
  .authoring-layout { grid-template-columns: 220px minmax(0, 1fr); }
  .resource-workbench { grid-template-columns: 210px minmax(0, 1fr); }
  .workspace-facts { grid-template-columns: 1fr 1fr; }
  .validation-evidence { grid-template-columns: repeat(3, 1fr); }
  .query-identity { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 760px) {
  .authoring-manifest, .authoring-layout, .resource-workbench, .query-form-grid { grid-template-columns: 1fr; }
  .authoring-safety { border-top: 1px solid var(--console-line-strong); }
  .workspace-index, .resource-index { max-height: 340px; overflow: auto; border-right: 0; border-bottom: 1px solid var(--console-line-strong); }
  .workspace-revision-bar, .inspector-command, .workspace-terminal-actions, .authoring-error { align-items: stretch; flex-direction: column; }
  .revision-stamp { min-width: 0; justify-items: start; }
  .workspace-facts, .validation-evidence, .query-identity { grid-template-columns: 1fr 1fr; }
  .diff-content, .conflict-compare { grid-template-columns: 1fr; }
  .conflict-compare p { grid-column: auto; }
  .candidate-inspector > nav { overflow-x: auto; }
  .candidate-inspector > nav button { min-width: 130px; }
  .workspace-zero-state { min-height: 240px; padding: 28px 18px; }
  .workspace-zero-state h3 { font-size: 22px; }
  .authoring-code { min-height: 260px; }
}
</style>
