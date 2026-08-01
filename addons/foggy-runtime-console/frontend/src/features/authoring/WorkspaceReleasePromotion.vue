<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { BundleItem } from '@/features/namespace/types'
import { shortRevision } from './authoringWorkspace'
import type { AuthoringReleasePackage, AuthoringWorkspaceInfo } from './types'

const props = defineProps<{
  namespace: string
  bundles: BundleItem[]
  capabilities: Record<string, string>
  workspace: AuthoringWorkspaceInfo | null
  canExport: boolean
  canPromote: boolean
  canRollback: boolean
  canRecoverRollback: boolean
  canRecoverPublication: boolean
  busy: string
}>()

const emit = defineEmits<{
  exportRelease: []
  importRelease: [releasePackage: AuthoringReleasePackage, targetBundle: string]
  promote: []
  rollback: []
  recoverRollback: []
  recoverPublication: []
  refresh: []
}>()

const selectedFile = ref('')
const packagePreview = ref<AuthoringReleasePackage | null>(null)
const packageError = ref('')
const targetBundle = ref('')

const exportCapability = computed(() => props.capabilities['authoring.releasePackage.export'] || 'unknown')
const importCapability = computed(() => props.capabilities['authoring.releasePackage.import'] || 'unknown')
const applyCapability = computed(() => props.capabilities['authoring.production.apply'] || 'unknown')
const rollbackCapability = computed(() => props.capabilities['authoring.production.rollback'] || 'unknown')
const importSupported = computed(() => importCapability.value === 'supported')
const eligibleBundles = computed(() => props.bundles.filter(item => item.workspaceEligible === true))
const imported = computed(() => props.workspace?.releaseImport || null)
const rollback = computed(() => props.workspace?.lastPublication?.rollback || null)
const canImport = computed(() => importSupported.value
  && Boolean(packagePreview.value)
  && Boolean(targetBundle.value)
  && !props.busy)

watch(eligibleBundles, items => {
  if (!items.some(item => item.name === targetBundle.value)) {
    targetBundle.value = items[0]?.name || ''
  }
}, { immediate: true })

function packageFrom(value: unknown): AuthoringReleasePackage {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('文件根节点必须是 release package JSON object。')
  }
  const candidate = value as Partial<AuthoringReleasePackage>
  if (candidate.formatVersion !== 'foggy-authoring-release/v1'
    || typeof candidate.packageId !== 'string'
    || typeof candidate.sourceNamespace !== 'string'
    || typeof candidate.sourceBundle !== 'string'
    || typeof candidate.candidateRevision !== 'string'
    || !Array.isArray(candidate.resources)
    || !candidate.validation) {
    throw new Error('文件不是完整的 foggy-authoring-release/v1 package。')
  }
  return candidate as AuthoringReleasePackage
}

async function readPackage(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  selectedFile.value = file?.name || ''
  packagePreview.value = null
  packageError.value = ''
  if (!file) return
  try {
    packagePreview.value = packageFrom(JSON.parse(await file.text()))
  } catch (error) {
    packageError.value = error instanceof Error ? error.message : '无法读取 release package。'
  }
}

function importSelected(): void {
  if (!packagePreview.value || !canImport.value) return
  emit('importRelease', packagePreview.value, targetBundle.value)
}
</script>

<template>
  <section class="release-console" aria-labelledby="release-console-title">
    <header class="release-console-head">
      <div>
        <span>RELEASE TRANSFER / PRODUCTION CONTROL</span>
        <h3 id="release-console-title">不可变交付包</h3>
        <p>文件由操作者显式传递；Console 不连接其他 Runtime，也不把 package 写入 localStorage、URL 或日志。</p>
      </div>
      <div class="release-capabilities" aria-label="Release capabilities">
        <span :class="{ enabled: exportCapability === 'supported' }">EXPORT <strong>{{ exportCapability }}</strong></span>
        <span :class="{ enabled: importCapability === 'supported' }">IMPORT <strong>{{ importCapability }}</strong></span>
        <span :class="{ enabled: applyCapability === 'supported' }">APPLY <strong>{{ applyCapability }}</strong></span>
        <span :class="{ enabled: rollbackCapability === 'supported' }">ROLLBACK <strong>{{ rollbackCapability }}</strong></span>
      </div>
    </header>

    <div class="release-transfer-grid">
      <article class="release-export">
        <span class="release-step">01 / DEVELOPMENT EXPORT</span>
        <h4>固定当前 exact candidate</h4>
        <p v-if="workspace">{{ workspace.sourceBundle }} · {{ shortRevision(workspace.candidateRevision) }}</p>
        <p v-else>先打开一个已验证 workspace，才能导出 deterministic JSON package。</p>
        <button
          class="console-button"
          type="button"
          :disabled="!canExport || Boolean(busy)"
          :title="exportCapability !== 'supported' ? 'Runtime 未声明 release package export' : '导出当前 exact validated revision'"
          @click="emit('exportRelease')"
        >{{ busy === 'release-export' ? '正在导出…' : '下载 release package' }}</button>
      </article>

      <article class="release-import">
        <span class="release-step">02 / PRODUCTION IMPORT</span>
        <div class="release-import-fields">
          <label class="console-field file-field">
            <span class="console-label">本地 package JSON</span>
            <input
              type="file"
              accept=".json,application/json"
              :disabled="!importSupported || Boolean(busy)"
              aria-label="选择 release package JSON"
              @change="readPackage"
            >
            <small>{{ selectedFile || (importSupported ? '仅在当前页面内存中预览' : '目标 Runtime 未启用 production promotion') }}</small>
          </label>
          <label class="console-field">
            <span class="console-label">Target Namespace / current X-NS</span>
            <input class="console-input" :value="namespace || '空 Namespace'" disabled>
          </label>
          <label class="console-field">
            <span class="console-label">Eligible target Bundle</span>
            <select v-model="targetBundle" class="console-select" :disabled="!importSupported || Boolean(busy)">
              <option value="">选择 Bundle</option>
              <option v-for="bundle in eligibleBundles" :key="bundle.name" :value="bundle.name">{{ bundle.name }}</option>
            </select>
          </label>
        </div>
        <div v-if="packageError" class="release-file-error" role="alert">{{ packageError }}</div>
        <dl v-if="packagePreview" class="release-preview" aria-label="Release package preview">
          <div><dt>FORMAT</dt><dd>{{ packagePreview.formatVersion }}</dd></div>
          <div><dt>PACKAGE</dt><dd :title="packagePreview.packageId">{{ shortRevision(packagePreview.packageId) }}</dd></div>
          <div><dt>SOURCE</dt><dd>{{ packagePreview.sourceNamespace }} / {{ packagePreview.sourceBundle }}</dd></div>
          <div><dt>CANDIDATE</dt><dd :title="packagePreview.candidateRevision">{{ shortRevision(packagePreview.candidateRevision) }}</dd></div>
          <div><dt>RESOURCES</dt><dd>{{ packagePreview.resources.length }}</dd></div>
          <div><dt>DEV VALIDATION</dt><dd>{{ packagePreview.validation.valid ? 'VALID · PROVENANCE ONLY' : 'INVALID' }}</dd></div>
        </dl>
        <div class="release-trust-note">
          <strong>完整性 ≠ 发布者身份</strong>
          <p>v1 没有签名/KMS。导入后不会自动 apply，必须在当前生产 Runtime 重新 validate/query。</p>
        </div>
        <button class="console-button primary" type="button" :disabled="!canImport" @click="importSelected">
          {{ busy === 'release-import' ? '正在导入…' : '确认 target 并导入只读 candidate' }}
        </button>
      </article>
    </div>

    <article v-if="workspace?.releaseImport" class="production-evidence">
      <header>
        <div>
          <span class="release-step">03 / IMPORTED RELEASE PROVENANCE</span>
          <h4>生产 candidate · immutable</h4>
        </div>
        <span :class="['status-chip', workspace.state === 'ROLLBACK_REQUIRED' ? 'warning' : '']">{{ workspace.state }}</span>
      </header>
      <dl class="production-facts">
        <div><dt>PACKAGE</dt><dd :title="imported?.packageId">{{ shortRevision(imported?.packageId) }}</dd></div>
        <div><dt>DEV SOURCE</dt><dd>{{ imported?.sourceNamespace }} / {{ imported?.sourceBundle }}</dd></div>
        <div><dt>PRODUCTION BASE</dt><dd :title="workspace.baseBundleRevision">{{ shortRevision(workspace.baseBundleRevision) }}</dd></div>
        <div><dt>PRODUCTION SOURCE</dt><dd :title="workspace.baseNamespaceSourceRevision">{{ shortRevision(workspace.baseNamespaceSourceRevision) }}</dd></div>
        <div><dt>APPLY ATTEMPT</dt><dd :title="workspace.lastPublication?.attemptId">{{ workspace.lastPublication?.attemptId || '—' }}</dd></div>
        <div><dt>ROLLBACK</dt><dd>{{ rollback?.status || 'NOT STARTED' }}</dd></div>
      </dl>
      <div class="production-action">
        <div v-if="workspace.state === 'DRAFT' || workspace.state === 'VALIDATED'">
          <strong>{{ canPromote ? '生产重验已完成' : '先用下方现有 flow 完成生产 validate/query' }}</strong>
          <p>Apply 只接受当前 package、candidate、production base Bundle 与 base Namespace source。</p>
        </div>
        <div v-else-if="workspace.state === 'PUBLISHED'">
          <strong>Package candidate 已应用到当前生产 Runtime</strong>
          <p>Rollback 只回到本 apply attempt 的直接前一 base，不提供 history selector。</p>
        </div>
        <div v-else-if="workspace.state === 'PUBLISHING'">
          <strong>Production apply attempt 正在收敛</strong>
          <p>Console 不轮询或重复 apply；请显式刷新服务端 authoritative metadata。</p>
        </div>
        <div v-else-if="workspace.state === 'RECOVERY_REQUIRED'">
          <strong>Production apply 需要恢复 failed publication attempt</strong>
          <p>Recovery 只回到该 attempt 记录的直接前一 live base，不是历史 rollback。</p>
        </div>
        <div v-else-if="workspace.state === 'ROLLING_BACK'">
          <strong>Rollback intent 已持久化，等待服务端收敛</strong>
          <p>Console 不轮询、不重试 mutation；只允许显式刷新 authoritative workspace metadata。</p>
        </div>
        <div v-else-if="workspace.state === 'ROLLBACK_REQUIRED'">
          <strong>Rollback 无法证明收敛，需要 pinned forward recovery</strong>
          <p>Recovery 只恢复同 package/candidate/attempt，不会覆盖第三方 drift。</p>
        </div>
        <div v-else-if="workspace.state === 'ROLLED_BACK'">
          <strong>直接前一 production base 已恢复</strong>
          <p>Candidate artifact 保留；该 workspace 已终结，不表示存在任意历史 rollback。</p>
        </div>
        <div class="production-buttons">
          <button v-if="workspace.state === 'DRAFT' || workspace.state === 'VALIDATED'" class="console-button primary" type="button" :disabled="!canPromote || Boolean(busy)" @click="emit('promote')">
            {{ busy === 'promote' ? '正在 apply…' : '确认并 apply exact package' }}
          </button>
          <button v-if="workspace.state === 'PUBLISHED'" class="console-button danger" type="button" :disabled="!canRollback || Boolean(busy)" @click="emit('rollback')">
            {{ busy === 'rollback' ? '正在 rollback…' : 'Rollback 到直接前一 base' }}
          </button>
          <button v-if="workspace.state === 'PUBLISHING'" class="console-button" type="button" :disabled="Boolean(busy)" @click="emit('refresh')">刷新 apply 状态</button>
          <template v-if="workspace.state === 'RECOVERY_REQUIRED'">
            <button class="console-button" type="button" :disabled="Boolean(busy)" @click="emit('refresh')">刷新 metadata</button>
            <button class="console-button danger" type="button" :disabled="!canRecoverPublication || Boolean(busy)" @click="emit('recoverPublication')">恢复 failed apply</button>
          </template>
          <button v-if="workspace.state === 'ROLLING_BACK'" class="console-button" type="button" :disabled="Boolean(busy)" @click="emit('refresh')">刷新 rollback 状态</button>
          <template v-if="workspace.state === 'ROLLBACK_REQUIRED'">
            <button class="console-button" type="button" :disabled="Boolean(busy)" @click="emit('refresh')">刷新 metadata</button>
            <button class="console-button danger" type="button" :disabled="!canRecoverRollback || Boolean(busy)" @click="emit('recoverRollback')">
              {{ busy === 'rollback-recover' ? '正在恢复…' : '恢复 pinned candidate' }}
            </button>
          </template>
        </div>
      </div>
    </article>
  </section>
</template>

<style scoped>
.release-console { border-bottom: 1px solid var(--console-line-strong); background: var(--console-panel); }
.release-console-head { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 20px; padding: 18px 20px; border-bottom: 1px solid var(--console-line-strong); background: repeating-linear-gradient(135deg, transparent 0 18px, var(--console-hatch-line) 18px 19px); }
.release-console-head > div:first-child > span, .release-step { color: var(--console-dim); font: 700 9px/1 var(--console-mono); }
.release-console h3, .release-console h4 { margin: 7px 0; }
.release-console h3 { font-size: 19px; letter-spacing: -.03em; }
.release-console h4 { font-size: 14px; }
.release-console p { margin: 0; color: var(--console-muted); font-size: 11px; line-height: 1.6; }
.release-capabilities { display: grid; grid-template-columns: 1fr 1fr; gap: 1px; align-self: start; background: var(--console-line); border: 1px solid var(--console-line); }
.release-capabilities span { min-width: 118px; padding: 8px 10px; background: var(--console-panel-2); color: var(--console-dim); font: 8px/1.2 var(--console-mono); }
.release-capabilities span.enabled { color: var(--console-success, #16835b); }
.release-capabilities strong { display: block; margin-top: 4px; color: inherit; }
.release-transfer-grid { display: grid; grid-template-columns: minmax(220px, .62fr) minmax(0, 1.38fr); border-bottom: 1px solid var(--console-line-strong); }
.release-export, .release-import { padding: 18px; }
.release-export { display: flex; align-items: flex-start; flex-direction: column; gap: 10px; border-right: 1px solid var(--console-line-strong); background: var(--console-panel-2); }
.release-export .console-button { margin-top: auto; }
.release-import-fields { display: grid; grid-template-columns: 1.3fr 1fr 1fr; gap: 10px; margin: 12px 0; }
.file-field input { max-width: 100%; color: var(--console-muted); font: 10px/1.4 var(--console-mono); }
.file-field small { color: var(--console-dim); font: 9px/1.4 var(--console-mono); }
.release-preview, .production-facts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1px; margin: 10px 0; background: var(--console-line); }
.release-preview div, .production-facts div { min-width: 0; padding: 9px; background: var(--console-panel-2); }
.release-preview dt, .production-facts dt { color: var(--console-dim); font: 8px/1 var(--console-mono); }
.release-preview dd, .production-facts dd { margin: 6px 0 0; overflow: hidden; font: 9px/1.3 var(--console-mono); text-overflow: ellipsis; white-space: nowrap; }
.release-file-error { margin: 8px 0; padding: 9px; border-left: 3px solid var(--console-danger, #bd3b32); color: var(--console-danger, #bd3b32); font: 10px/1.5 var(--console-mono); }
.release-trust-note { margin: 10px 0; padding: 10px; border-left: 3px solid var(--console-warning, #b9770e); background: var(--console-panel-2); }
.release-trust-note strong { display: block; margin-bottom: 3px; font-size: 11px; }
.production-evidence > header, .production-action { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 14px 18px; }
.production-evidence > header { border-bottom: 1px solid var(--console-line); }
.production-buttons { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }

@media (max-width: 1080px) {
  .release-import-fields, .release-preview, .production-facts { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 760px) {
  .release-console-head, .release-transfer-grid { grid-template-columns: 1fr; }
  .release-capabilities { width: 100%; }
  .release-capabilities span { min-width: 0; }
  .release-export { min-height: 180px; border-right: 0; border-bottom: 1px solid var(--console-line-strong); }
  .release-import-fields, .release-preview, .production-facts { grid-template-columns: 1fr; }
  .production-evidence > header, .production-action { align-items: stretch; flex-direction: column; }
  .production-buttons { justify-content: stretch; }
  .production-buttons .console-button { width: 100%; }
}
</style>
