<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from './api'
import ChatWorkspace from './chat/ChatWorkspace.vue'
import StudioWorkspace from './studio/StudioWorkspace.vue'
import type { Session } from './domain'

type WorkspaceMode = 'CHAT' | 'STUDIO'

const modeFromHash = (): WorkspaceMode => window.location.hash.startsWith('#/studio')
  ? 'STUDIO'
  : 'CHAT'

const mode = ref<WorkspaceMode>(modeFromHash())
const session = ref<Session | null>(null)

const syncRoute = () => { mode.value = modeFromHash() }
const openChat = () => { window.location.hash = '#/chat' }
const openStudio = () => { window.location.hash = '#/studio' }

onMounted(async () => {
  window.addEventListener('hashchange', syncRoute)
  if (!window.location.hash) window.history.replaceState(null, '', '#/chat')
  try {
    session.value = await api.session()
  } catch {
    session.value = null
  }
})

onBeforeUnmount(() => window.removeEventListener('hashchange', syncRoute))
</script>

<template>
  <ChatWorkspace v-if="mode === 'CHAT'" :session="session" @open-studio="openStudio" />
  <StudioWorkspace v-else :session="session" @open-chat="openChat" />
</template>
