<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { Session } from '../domain'
import { canDesign } from '../domain'
import ConversationSidebar from './ConversationSidebar.vue'
import ConversationView from './ConversationView.vue'
import { useQuestionConversations } from './useQuestionConversations'

const props = defineProps<{ session: Session | null }>()
const emit = defineEmits<{ openStudio: [] }>()
const sidebarOpen = ref(false)
const chat = useQuestionConversations()

const selectConversation = async (conversationId: string) => {
  sidebarOpen.value = false
  await chat.openConversation(conversationId)
}

const newConversation = () => {
  sidebarOpen.value = false
  chat.newConversation()
}

const handleShortcut = (event: KeyboardEvent) => {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'n') {
    event.preventDefault()
    newConversation()
  }
}

onMounted(() => {
  void chat.initialize()
  window.addEventListener('hashchange', chat.handleRouteChange)
  window.addEventListener('keydown', handleShortcut)
})

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', chat.handleRouteChange)
  window.removeEventListener('keydown', handleShortcut)
})
</script>

<template>
  <div class="chat-shell">
    <div v-if="sidebarOpen" class="mobile-scrim" @click="sidebarOpen = false"></div>
    <ConversationSidebar
      :conversations="chat.conversations.value"
      :active-id="chat.activeConversation.value?.conversationId ?? null"
      :session="props.session"
      :designer="canDesign(props.session)"
      :open="sidebarOpen"
      :archiving-id="chat.archivingId.value"
      @new-conversation="newConversation"
      @select="selectConversation"
      @archive="chat.archiveConversation"
      @open-studio="emit('openStudio')"
      @close="sidebarOpen = false"
    />
    <ConversationView
      :profiles="chat.profiles.value"
      :active-conversation="chat.activeConversation.value"
      :active-summary="chat.activeSummary.value"
      :active-profile="chat.activeProfile.value"
      :turns="chat.turns.value"
      :selected-profile-id="chat.selectedProfileId.value"
      :prompt="chat.prompt.value"
      :pending-prompt="chat.pendingPrompt.value"
      :loading="chat.loading.value"
      :submitting="chat.submitting.value"
      :refreshing="chat.refreshing.value"
      :turn-details="chat.turnDetails.value"
      :detail-loading="chat.detailLoading.value"
      :detail-errors="chat.detailErrors.value"
      :waiting="chat.waiting.value"
      :error="chat.error.value"
      :notice="chat.notice.value"
      @update:profile="chat.selectedProfileId.value = $event"
      @update:prompt="chat.prompt.value = $event"
      @send="chat.send"
      @refresh="chat.refreshTurns"
      @load-turn-detail="chat.loadTurnDetail"
      @new-conversation="newConversation"
      @open-sidebar="sidebarOpen = true"
    />
  </div>
</template>

<style scoped>
.chat-shell { display: grid; grid-template-columns: 270px minmax(0, 1fr); min-height: 100vh; overflow: hidden; }
.mobile-scrim { display: none; }
@media (max-width: 760px) {
  .chat-shell { grid-template-columns: 1fr; }
  .mobile-scrim { position: fixed; inset: 0; z-index: 19; display: block; background: rgba(23, 33, 31, .42); }
}
</style>
