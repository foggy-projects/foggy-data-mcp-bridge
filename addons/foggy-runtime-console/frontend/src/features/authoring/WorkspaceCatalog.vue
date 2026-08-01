<script setup lang="ts">
import type { BundleItem } from '@/features/namespace/types'
import { shortRevision } from './authoringWorkspace'
import type { AuthoringWorkspaceInfo } from './types'

defineProps<{
  workspaces: AuthoringWorkspaceInfo[]
  selectedWorkspaceId?: string
  bundles: BundleItem[]
  busy: string
}>()

const emit = defineEmits<{
  refresh: []
  open: [workspaceId: string]
  create: [bundle: BundleItem]
}>()
</script>

<template>
  <aside class="workspace-index" aria-label="Authoring workspace 列表">
    <div class="studio-section-head">
      <span>01 / WORKSPACES</span>
      <button class="console-button compact ghost" type="button" :disabled="Boolean(busy)" @click="emit('refresh')">刷新</button>
    </div>

    <div v-if="busy === 'list'" class="studio-empty">正在读取 workspace…</div>
    <button
      v-for="item in workspaces"
      :key="item.workspaceId"
      type="button"
      class="workspace-ticket"
      :class="{ active: selectedWorkspaceId === item.workspaceId }"
      :aria-pressed="selectedWorkspaceId === item.workspaceId"
      @click="emit('open', item.workspaceId)"
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
          @click="emit('create', bundle)"
        >创建</button>
      </article>
      <div v-if="!bundles.length" class="studio-empty">当前 Namespace 没有 Bundle 来源。</div>
    </div>
  </aside>
</template>

<style scoped>
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

@media (max-width: 760px) {
  .workspace-index { max-height: 340px; overflow: auto; border-right: 0; border-bottom: 1px solid var(--console-line-strong); }
}
</style>
