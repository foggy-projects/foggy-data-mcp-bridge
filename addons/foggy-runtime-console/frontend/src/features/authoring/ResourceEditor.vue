<script setup lang="ts">
import { shortRevision } from './authoringWorkspace'
import type { AuthoringResource } from './types'

const props = defineProps<{
  resources: AuthoringResource[]
  selectedResource: AuthoringResource | null
  creatingResource: boolean
  dirty: boolean
  pathError: string
  editorPath: string
  editorContent: string
  canMutate: boolean
  busy: string
  candidateRevision: string
  conflictServerContent: string | null
}>()

const emit = defineEmits<{
  new: []
  select: [resource: AuthoringResource]
  save: []
  delete: []
  'update:editorPath': [value: string]
  'update:editorContent': [value: string]
}>()
</script>

<template>
  <div class="resource-workbench">
    <aside class="resource-index" aria-label="Workspace 资源">
      <div class="studio-section-head">
        <span>02 / RESOURCES · {{ resources.length }}</span>
        <button class="console-button compact" type="button" :disabled="!canMutate || Boolean(busy)" @click="emit('new')">新建</button>
      </div>
      <button
        v-for="resource in resources"
        :key="resource.path"
        type="button"
        class="resource-line"
        :class="{ active: selectedResource?.path === resource.path && !creatingResource }"
        @click="emit('select', resource)"
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
          <input
            :value="editorPath"
            class="console-input"
            aria-label="Workspace 资源路径"
            :disabled="!creatingResource"
            autocomplete="off"
            @input="emit('update:editorPath', ($event.target as HTMLInputElement).value)"
          >
          <small v-if="pathError" class="field-error">{{ pathError }}</small>
        </label>
        <label class="console-field editor-content-field">
          <span class="console-label">UTF-8 草稿内容</span>
          <textarea
            :value="editorContent"
            class="console-textarea authoring-code"
            aria-label="Workspace 资源内容"
            spellcheck="false"
            :disabled="!canMutate"
            @input="emit('update:editorContent', ($event.target as HTMLTextAreaElement).value)"
          />
        </label>
        <div v-if="conflictServerContent !== null" class="conflict-compare">
          <div>
            <span>LOCAL UNSAVED</span>
            <pre>{{ editorContent }}</pre>
          </div>
          <div>
            <span>SERVER / {{ shortRevision(candidateRevision) }}</span>
            <pre>{{ conflictServerContent }}</pre>
          </div>
          <p>本地草稿仍保留。请人工比较后，再决定是否基于当前服务端 revision 显式保存。</p>
        </div>
        <div class="editor-actions">
          <button class="console-button primary" type="button" :disabled="!canMutate || !dirty || Boolean(pathError) || Boolean(busy)" @click="emit('save')">
            {{ busy === 'save' ? '保存中…' : '保存为新 revision' }}
          </button>
          <button v-if="selectedResource && !creatingResource" class="console-button danger" type="button" :disabled="!canMutate || Boolean(busy)" @click="emit('delete')">删除草稿资源</button>
        </div>
      </div>
      <div v-else class="workspace-zero-state compact">
        <span>RESOURCE EDITOR</span>
        <h3>选择一个资源或新建文件</h3>
        <p>保存只推进 candidate revision，不会 validate、refresh 或 publish。</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.resource-workbench { display: grid; grid-template-columns: 250px minmax(0, 1fr); min-height: 440px; border-bottom: 1px solid var(--console-line-strong); }
.resource-index { border-right: 1px solid var(--console-line-strong); background: var(--console-panel-2); }
.studio-section-head { min-height: 43px; display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 12px; border-bottom: 1px solid var(--console-line); color: var(--console-dim); font: 700 9px/1 var(--console-mono); letter-spacing: .08em; }
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
.studio-empty { padding: 18px; color: var(--console-dim); font: 11px/1.6 var(--console-mono); }
.workspace-zero-state { min-height: 280px; display: flex; align-items: flex-start; justify-content: center; flex-direction: column; padding: 48px; background: repeating-linear-gradient(135deg, transparent 0 14px, var(--console-hatch-line) 14px 15px); }
.workspace-zero-state span { color: var(--console-dim); font: 700 10px/1 var(--console-mono); }
.workspace-zero-state h3 { max-width: 620px; margin: 12px 0; font-size: 28px; letter-spacing: -.04em; }
.workspace-zero-state p { max-width: 640px; margin: 0; color: var(--console-muted); font-size: 12px; line-height: 1.65; }
.workspace-zero-state.compact { min-height: 340px; padding: 30px; }
.workspace-zero-state.compact h3 { font-size: 20px; }
.conflict-compare { display: grid; grid-template-columns: 1fr 1fr; gap: 1px; margin-top: 14px; border: 1px solid var(--console-line-strong); background: var(--console-line-strong); }
.conflict-compare > div { min-width: 0; padding: 10px; background: var(--console-panel-2); }
.conflict-compare span { color: var(--console-dim); font: 8px/1 var(--console-mono); }
.conflict-compare pre { min-height: 90px; max-height: 250px; margin: 8px 0 0; overflow: auto; white-space: pre-wrap; word-break: break-word; font: 10px/1.55 var(--console-mono); }
.conflict-compare p { grid-column: 1 / -1; margin: 0; padding: 10px; background: var(--console-panel); color: var(--console-muted); font: 10px/1.5 var(--console-mono); }

@media (max-width: 1080px) {
  .resource-workbench { grid-template-columns: 210px minmax(0, 1fr); }
}

@media (max-width: 760px) {
  .resource-workbench { grid-template-columns: 1fr; }
  .resource-index { max-height: 340px; overflow: auto; border-right: 0; border-bottom: 1px solid var(--console-line-strong); }
  .conflict-compare { grid-template-columns: 1fr; }
  .conflict-compare p { grid-column: auto; }
  .workspace-zero-state { min-height: 240px; padding: 28px 18px; }
  .workspace-zero-state h3 { font-size: 22px; }
  .authoring-code { min-height: 260px; }
}
</style>
