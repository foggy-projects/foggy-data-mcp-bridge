<script setup lang="ts">
import { computed } from 'vue'
import type { BundleItem } from '@/features/namespace/types'
import { shortRevision, type WorkspaceActions } from './authoringWorkspace'
import type { AuthoringWorkspaceInfo } from './types'

const props = defineProps<{
  workspace: AuthoringWorkspaceInfo
  actions: WorkspaceActions
  canPublish: boolean
  dirty: boolean
  busy: string
  nextBundle?: BundleItem
}>()

const emit = defineEmits<{
  publish: []
  recover: []
  refresh: []
  createNext: []
}>()

const publication = computed(() => props.workspace.lastPublication || null)
const canRecover = computed(() => props.actions.recover
  && Boolean(publication.value?.attemptId)
  && publication.value?.candidateRevision === props.workspace.candidateRevision)
</script>

<template>
  <section class="workspace-publication" aria-labelledby="publication-title">
    <header>
      <div>
        <span>04 / CONTROLLED PUBLICATION</span>
        <h4 id="publication-title">开发 Runtime 发布与失败恢复</h4>
        <p>发布 exact validated candidate；Runtime 独立负责 immutable artifact、source switch 与 Namespace catalog refresh。</p>
      </div>
      <span :class="['status-chip', workspace.state === 'RECOVERY_REQUIRED' ? 'warning' : '']">
        {{ publication?.status || 'NOT STARTED' }}
      </span>
    </header>

    <dl class="publication-facts">
      <div><dt>ATTEMPT</dt><dd :title="publication?.attemptId">{{ publication?.attemptId || '—' }}</dd></div>
      <div><dt>APPLIED BUNDLE</dt><dd :title="publication?.appliedBundleRevision || ''">{{ shortRevision(publication?.appliedBundleRevision || '') }}</dd></div>
      <div><dt>PUBLISHED SOURCE</dt><dd :title="publication?.publishedNamespaceSourceRevision || ''">{{ shortRevision(publication?.publishedNamespaceSourceRevision || '') }}</dd></div>
      <div><dt>CATALOG BEFORE → AFTER</dt><dd>{{ shortRevision(publication?.beforeCatalogGeneration || '') }} → {{ shortRevision(publication?.afterCatalogGeneration || publication?.recoveredCatalogGeneration || '') }}</dd></div>
    </dl>

    <div v-for="diagnostic in publication?.diagnostics || []" :key="diagnostic" class="publication-diagnostic">
      {{ diagnostic }}
    </div>

    <div v-if="workspace.state === 'PUBLISHING'" class="publication-callout">
      <div><strong>发布事务正在收敛</strong><p>资源修改、validate、query、discard 与重复发布均已关闭。Console 不轮询或自动重试。</p></div>
      <button class="console-button" type="button" :disabled="Boolean(busy)" @click="emit('refresh')">
        {{ busy === 'publication-refresh' ? '刷新中…' : '刷新 publication 状态' }}
      </button>
    </div>

    <div v-else-if="workspace.state === 'RECOVERY_REQUIRED'" class="publication-callout recovery">
      <div><strong>需要恢复 failed publication attempt</strong><p>恢复只回到该 attempt 记录的旧 live source，不是成功发布后的历史 rollback。</p></div>
      <div class="publication-actions">
        <button class="console-button" type="button" :disabled="Boolean(busy)" @click="emit('refresh')">刷新 metadata</button>
        <button class="console-button danger" type="button" :disabled="!canRecover || Boolean(busy)" @click="emit('recover')">
          {{ busy === 'recover' ? '恢复中…' : '恢复失败发布' }}
        </button>
      </div>
    </div>

    <div v-else-if="workspace.state === 'PUBLISHED'" class="publication-callout published">
      <div><strong>该 workspace 已 immutable 发布并终结</strong><p>资源、diff 与 evidence 保持只读。继续修改必须从当前 Bundle 创建新的 workspace。</p></div>
      <button class="console-button primary" type="button" :disabled="!actions.createNext || !nextBundle || Boolean(busy)" :title="nextBundle ? '创建独立的下一 workspace' : '服务端不再声明该 Bundle workspaceEligible'" @click="emit('createNext')">
        创建下一 workspace
      </button>
    </div>

    <div v-else-if="workspace.state === 'STALE' && publication?.status === 'RECOVERED'" class="publication-callout recovered">
      <div><strong>失败发布已恢复</strong><p>服务端已恢复旧 live source/catalog；当前草稿保留为 STALE。请新建 workspace 并显式迁移，不要把它理解为历史 rollback。</p></div>
    </div>

    <div v-else class="publication-callout">
      <div>
        <strong>{{ canPublish ? 'Exact candidate 已具备发布条件' : '先完成当前 revision 的全量校验' }}</strong>
        <p>发布仅面向当前开发 Runtime，不能自动回滚；release package 与生产 promotion 不在本闭环内。</p>
      </div>
      <button class="console-button primary" type="button" :disabled="!canPublish || dirty || Boolean(busy)" @click="emit('publish')">
        {{ busy === 'publish' ? '发布中…' : '确认并发布 exact revision' }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.workspace-publication { border-bottom: 1px solid var(--console-line-strong); }
.workspace-publication > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; padding: 16px; border-bottom: 1px solid var(--console-line); background: var(--console-panel-2); }
.workspace-publication h4 { margin: 6px 0; font-size: 15px; }
.workspace-publication header span { color: var(--console-dim); font: 700 9px/1 var(--console-mono); }
.workspace-publication p { margin: 0; color: var(--console-muted); font-size: 11px; line-height: 1.55; }
.publication-facts { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1px; margin: 0; background: var(--console-line); }
.publication-facts div { min-width: 0; padding: 11px; background: var(--console-panel); }
.publication-facts dt { color: var(--console-dim); font: 8px/1 var(--console-mono); }
.publication-facts dd { margin: 7px 0 0; overflow: hidden; font: 10px/1.3 var(--console-mono); text-overflow: ellipsis; white-space: nowrap; }
.publication-diagnostic { padding: 8px 14px; border-top: 1px solid var(--console-line); color: var(--console-muted); font: 10px/1.5 var(--console-mono); }
.publication-callout { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 14px 16px; background: repeating-linear-gradient(135deg, transparent 0 11px, var(--console-hatch-line) 11px 12px); }
.publication-callout strong { display: block; margin-bottom: 4px; font-size: 12px; }
.publication-callout.recovery { border-left: 3px solid var(--console-warning, #b9770e); }
.publication-callout.published, .publication-callout.recovered { border-left: 3px solid var(--console-success, #16835b); }
.publication-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }

@media (max-width: 1080px) {
  .publication-facts { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 760px) {
  .workspace-publication > header, .publication-callout { align-items: stretch; flex-direction: column; }
  .publication-facts { grid-template-columns: 1fr 1fr; }
  .publication-actions { justify-content: stretch; }
  .publication-actions .console-button { flex: 1; }
}
</style>
