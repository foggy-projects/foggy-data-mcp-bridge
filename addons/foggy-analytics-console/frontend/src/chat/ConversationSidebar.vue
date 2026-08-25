<script setup lang="ts">
import { nextTick, ref } from 'vue'
import type { ConversationSummary } from '../api'
import AppearanceMenu from '../components/AppearanceMenu.vue'
import type { Session } from '../domain'
import { formatConversationTime } from './presentation'

const props = defineProps<{
  conversations: ConversationSummary[]
  activeId: string | null
  session: Session | null
  designer: boolean
  open: boolean
  archivingId: string
}>()

const emit = defineEmits<{
  newConversation: []
  select: [conversationId: string]
  archive: [conversationId: string]
  openStudio: []
  close: []
}>()

const contextMenu = ref<{ conversationId: string; left: number; top: number } | null>(null)
const contextAction = ref<HTMLButtonElement | null>(null)

const closeContextMenu = () => {
  contextMenu.value = null
}

const showContextMenu = async (
  conversationId: string,
  requestedLeft: number,
  requestedTop: number
) => {
  const menuWidth = 176
  const menuHeight = 52
  const edge = 8
  contextMenu.value = {
    conversationId,
    left: Math.max(edge, Math.min(requestedLeft, window.innerWidth - menuWidth - edge)),
    top: Math.max(edge, Math.min(requestedTop, window.innerHeight - menuHeight - edge))
  }
  await nextTick()
  contextAction.value?.focus()
}

const openContextMenu = (event: MouseEvent, conversationId: string) => {
  event.preventDefault()
  void showContextMenu(conversationId, event.clientX, event.clientY)
}

const handleConversationKeydown = (event: KeyboardEvent, conversationId: string) => {
  if (event.key !== 'ContextMenu' && !(event.shiftKey && event.key === 'F10')) return
  event.preventDefault()
  const target = event.currentTarget as HTMLElement
  const bounds = target.getBoundingClientRect()
  void showContextMenu(conversationId, bounds.left + 42, bounds.top + bounds.height / 2)
}

const archiveSelectedConversation = () => {
  const conversationId = contextMenu.value?.conversationId
  if (!conversationId || props.archivingId) return
  closeContextMenu()
  emit('archive', conversationId)
}

const startConversation = () => {
  closeContextMenu()
  emit('newConversation')
}

const selectConversation = (conversationId: string) => {
  closeContextMenu()
  emit('select', conversationId)
}
</script>

<template>
  <aside class="conversation-sidebar" :class="{ open }" aria-label="会话导航">
    <div class="sidebar-brand">
      <button class="mobile-close" type="button" aria-label="关闭会话列表" @click="$emit('close')">×</button>
      <span>FOGGY / ANALYTICS</span>
      <strong>问数台</strong>
      <small><i></i> JAVA RUNTIME CONNECTED</small>
    </div>

    <button class="new-conversation" type="button" @click="startConversation">
      <span>＋</span>
      <strong>新建会话</strong>
      <kbd>⌘ N</kbd>
    </button>

    <div class="history-heading">
      <span>最近会话</span>
      <b>{{ conversations.length.toString().padStart(2, '0') }}</b>
    </div>
    <nav class="conversation-history" aria-label="最近会话">
      <button
        v-for="conversation in conversations"
        :key="conversation.conversationId"
        type="button"
        :class="{ active: activeId === conversation.conversationId }"
        aria-haspopup="menu"
        :aria-expanded="contextMenu?.conversationId === conversation.conversationId"
        @click="selectConversation(conversation.conversationId)"
        @contextmenu="openContextMenu($event, conversation.conversationId)"
        @keydown="handleConversationKeydown($event, conversation.conversationId)"
      >
        <span class="history-mark">{{ conversation.title.slice(0, 1) }}</span>
        <span class="history-copy">
          <strong>{{ conversation.title }}</strong>
          <small>{{ formatConversationTime(conversation.lastActivityAt) }}</small>
        </span>
        <i></i>
      </button>
      <p v-if="!conversations.length" class="history-empty">
        你的问数会话会出现在这里。Console 不保存问题正文。
      </p>
    </nav>

    <div class="sidebar-footer">
      <button v-if="designer" type="button" class="studio-link" @click="$emit('openStudio')">
        <span>▦</span><strong>分析工作室</strong><em>报表与 Dashboard →</em>
      </button>
      <AppearanceMenu />
      <div v-if="session" class="sidebar-identity">
        <span>{{ session.displayName.slice(0, 1).toUpperCase() }}</span>
        <div><strong>{{ session.displayName }}</strong><small>{{ session.roles.join(' · ') }}</small></div>
      </div>
    </div>
  </aside>

  <Teleport to="body">
    <div
      v-if="contextMenu"
      class="conversation-context-layer"
      @pointerdown.self="closeContextMenu"
      @contextmenu.prevent="closeContextMenu"
    >
      <div
        class="conversation-context-menu"
        role="menu"
        aria-label="会话操作"
        :style="{ left: `${contextMenu.left}px`, top: `${contextMenu.top}px` }"
        @keydown.esc.stop.prevent="closeContextMenu"
      >
        <button
          ref="contextAction"
          type="button"
          role="menuitem"
          :disabled="archivingId === contextMenu.conversationId"
          @click="archiveSelectedConversation"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 7.5h16M6 7.5v11h12v-11M9.5 11.5h5M5 4h14l1 3.5H4L5 4Z" />
          </svg>
          <span>{{ archivingId === contextMenu.conversationId ? '归档中…' : '归档会话' }}</span>
        </button>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.conversation-sidebar {
  position: relative;
  z-index: 20;
  display: flex;
  min-width: 0;
  height: 100vh;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid #3c4945;
  background: var(--ink);
  color: var(--paper-light);
}
.sidebar-brand { position: relative; padding: 24px 22px 20px; border-bottom: 1px solid #3c4945; }
.sidebar-brand > span { display: block; color: #9ca8a3; font: 700 9px var(--mono); letter-spacing: .16em; }
.sidebar-brand > strong { display: block; margin-top: 7px; font: 800 27px/.95 var(--serif); letter-spacing: -.06em; }
.sidebar-brand > small { display: flex; align-items: center; gap: 7px; margin-top: 15px; color: #aeb8b3; font: 700 8px var(--mono); letter-spacing: .07em; }
.sidebar-brand small i { width: 7px; height: 7px; border: 1px solid var(--paper-light); border-radius: 50%; background: var(--acid); box-shadow: 0 0 0 3px rgba(216, 228, 76, .12); }
.mobile-close { display: none; }
.new-conversation { display: grid; grid-template-columns: 24px 1fr auto; align-items: center; gap: 10px; margin: 18px; padding: 11px 12px; border: 1px solid var(--paper-light); background: var(--paper-light); color: var(--ink); cursor: pointer; box-shadow: 4px 4px 0 var(--cobalt); text-align: left; }
.new-conversation > span { font: 300 23px/1 var(--sans); }
.new-conversation strong { font-size: 13px; }
.new-conversation kbd { color: var(--ink-soft); font: 9px var(--mono); }
.new-conversation:hover { background: var(--acid); box-shadow: 4px 4px 0 var(--vermilion); }
.history-heading { display: flex; justify-content: space-between; margin: 3px 20px 9px; padding-bottom: 9px; border-bottom: 1px solid #52605b; color: #9ca8a3; font: 700 9px var(--mono); letter-spacing: .12em; text-transform: uppercase; }
.conversation-history { min-height: 0; flex: 1; overflow-y: auto; padding: 0 10px 16px; scrollbar-color: #56625e transparent; }
.conversation-history button { position: relative; display: grid; grid-template-columns: 31px 1fr 6px; align-items: center; gap: 10px; width: 100%; margin: 2px 0; padding: 10px; border: 0; background: transparent; color: inherit; cursor: pointer; text-align: left; }
.conversation-history button:hover { background: #26322f; }
.conversation-history button.active { background: #303e3a; }
.history-mark { display: grid; width: 30px; height: 30px; place-items: center; border: 1px solid #5c6964; color: var(--acid); font: 800 12px var(--serif); }
.history-copy { min-width: 0; }
.history-copy strong, .history-copy small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.history-copy strong { font-size: 12px; }
.history-copy small { margin-top: 5px; color: #919d98; font: 9px var(--mono); }
.conversation-history button > i { width: 5px; height: 5px; border-radius: 50%; background: transparent; }
.conversation-history button.active > i { background: var(--acid); }
.history-empty { margin: 24px 13px; color: #899590; font-size: 11px; line-height: 1.7; }
.sidebar-footer { margin-top: auto; border-top: 1px solid #3c4945; }
.sidebar-footer > .appearance-menu { margin: 0 12px 4px; }
.studio-link { display: grid; grid-template-columns: 25px 1fr; width: calc(100% - 24px); margin: 12px; padding: 11px; border: 1px solid #4a5752; background: transparent; color: inherit; cursor: pointer; text-align: left; }
.studio-link > span { grid-row: 1 / 3; color: var(--acid); font-size: 20px; }
.studio-link strong { font-size: 12px; }
.studio-link em { margin-top: 3px; color: #929e99; font: normal 8px var(--mono); }
.studio-link:hover { border-color: var(--acid); }
.sidebar-identity { display: grid; grid-template-columns: 34px 1fr; gap: 10px; align-items: center; padding: 13px 18px 17px; }
.sidebar-identity > span { display: grid; width: 33px; height: 33px; place-items: center; border-radius: 50%; background: var(--cobalt); font: 800 12px var(--serif); }
.sidebar-identity strong, .sidebar-identity small { display: block; }
.sidebar-identity strong { font-size: 11px; }
.sidebar-identity small { margin-top: 4px; color: #8e9a95; font: 8px var(--mono); }
.conversation-context-layer { position: fixed; inset: 0; z-index: 120; }
.conversation-context-menu { position: fixed; width: 176px; padding: 5px; border: 1px solid var(--ink); background: var(--paper-light); color: var(--ink); box-shadow: 4px 4px 0 rgba(23, 33, 31, .28); }
.conversation-context-menu button { display: grid; grid-template-columns: 22px 1fr; align-items: center; gap: 9px; width: 100%; min-height: 40px; padding: 8px 10px; border: 0; background: transparent; color: inherit; cursor: pointer; text-align: left; }
.conversation-context-menu button:hover, .conversation-context-menu button:focus-visible { outline: 0; background: #e9e9e2; }
.conversation-context-menu button:disabled { cursor: wait; opacity: .55; }
.conversation-context-menu svg { width: 18px; height: 18px; fill: none; stroke: currentColor; stroke-linecap: square; stroke-linejoin: miter; stroke-width: 1.6; }
.conversation-context-menu span { font: 700 12px var(--sans); }

@media (max-width: 760px) {
  .conversation-sidebar { position: fixed; inset: 0 auto 0 0; width: min(286px, 88vw); transform: translateX(-104%); box-shadow: 12px 0 30px rgba(0, 0, 0, .26); transition: transform .2s ease; }
  .conversation-sidebar.open { transform: translateX(0); }
  .mobile-close { position: absolute; top: 13px; right: 14px; display: block; border: 0; background: transparent; color: white; cursor: pointer; font-size: 24px; }
}
</style>
