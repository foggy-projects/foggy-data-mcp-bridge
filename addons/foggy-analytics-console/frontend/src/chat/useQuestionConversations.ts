import { computed, onBeforeUnmount, ref } from 'vue'
import {
  api,
  type AgentTurn,
  type Conversation,
  type ConversationSummary,
  type QuestionProfile
} from '../api'
import {
  conversationHash,
  conversationIdFromHash,
  turnSequenceFinished
} from './presentation'

const POLL_DELAY_MS = 1_400

export const useQuestionConversations = () => {
  const profiles = ref<QuestionProfile[]>([])
  const conversations = ref<ConversationSummary[]>([])
  const activeConversation = ref<Conversation | null>(null)
  const turns = ref<AgentTurn[]>([])
  const selectedProfileId = ref('')
  const prompt = ref('')
  const pendingPrompt = ref('')
  const loading = ref(true)
  const submitting = ref(false)
  const refreshing = ref(false)
  const error = ref('')
  const notice = ref('')
  let pollTimer: number | undefined

  const activeSummary = computed(() => conversations.value.find(value =>
    value.conversationId === activeConversation.value?.conversationId) ?? null)
  const activeProfile = computed(() => profiles.value.find(value =>
    value.profileId === (activeConversation.value?.questionProfileId
      ?? selectedProfileId.value)) ?? null)
  const waiting = computed(() => Boolean(
    pendingPrompt.value
      || (turns.value.length && !turnSequenceFinished(turns.value, pendingPrompt.value))))

  const stopPolling = () => {
    if (pollTimer !== undefined) window.clearTimeout(pollTimer)
    pollTimer = undefined
  }

  const replaceRoute = (conversationId?: string | null) => {
    window.history.replaceState(null, '', conversationHash(conversationId))
  }

  const refreshConversations = async () => {
    conversations.value = await api.conversations()
  }

  const refreshTurns = async (scheduleNext = true) => {
    if (!activeConversation.value || refreshing.value) return
    refreshing.value = true
    try {
      const next = await api.turns(activeConversation.value.conversationId)
      turns.value = next
      if (pendingPrompt.value
          && next.some(value => value.userMessage === pendingPrompt.value)) {
        pendingPrompt.value = ''
      }
      if (turnSequenceFinished(next, pendingPrompt.value)) {
        stopPolling()
        await refreshConversations()
      } else if (scheduleNext) {
        schedulePoll()
      }
    } catch (reason) {
      stopPolling()
      error.value = reason instanceof Error ? reason.message : '回答状态读取失败'
    } finally {
      refreshing.value = false
    }
  }

  const schedulePoll = () => {
    stopPolling()
    pollTimer = window.setTimeout(() => void refreshTurns(), POLL_DELAY_MS)
  }

  const openConversation = async (conversationId: string, updateRoute = true) => {
    if (conversationId === activeConversation.value?.conversationId) return
    stopPolling()
    loading.value = true
    error.value = ''
    notice.value = ''
    try {
      const conversation = await api.conversation(conversationId)
      activeConversation.value = conversation
      selectedProfileId.value = conversation.questionProfileId ?? selectedProfileId.value
      turns.value = await api.turns(conversationId)
      pendingPrompt.value = ''
      if (updateRoute) replaceRoute(conversationId)
      if (!turnSequenceFinished(turns.value, '')) schedulePoll()
    } catch (reason) {
      error.value = reason instanceof Error ? reason.message : '会话载入失败'
    } finally {
      loading.value = false
    }
  }

  const newConversation = (updateRoute = true) => {
    stopPolling()
    activeConversation.value = null
    turns.value = []
    pendingPrompt.value = ''
    prompt.value = ''
    error.value = ''
    notice.value = ''
    selectedProfileId.value ||= profiles.value[0]?.profileId ?? ''
    if (updateRoute) replaceRoute()
  }

  const send = async () => {
    const message = prompt.value.trim()
    if (!message || !selectedProfileId.value || submitting.value || waiting.value) return
    submitting.value = true
    error.value = ''
    notice.value = ''
    pendingPrompt.value = message
    try {
      const conversation = activeConversation.value
        ? await api.continueConversation(activeConversation.value.conversationId, message)
        : await api.askQuestion(selectedProfileId.value, message)
      activeConversation.value = conversation
      prompt.value = ''
      replaceRoute(conversation.conversationId)
      await refreshConversations()
      await refreshTurns()
      notice.value = '正在使用当前用户的 QM/TM 数据权限分析'
    } catch (reason) {
      pendingPrompt.value = ''
      error.value = reason instanceof Error ? reason.message : '问题提交失败'
    } finally {
      submitting.value = false
    }
  }

  const initialize = async () => {
    loading.value = true
    error.value = ''
    try {
      const [nextProfiles, nextConversations] = await Promise.all([
        api.questionProfiles(), api.conversations()
      ])
      profiles.value = nextProfiles
      conversations.value = nextConversations
      selectedProfileId.value = nextProfiles[0]?.profileId ?? ''
      const requestedId = conversationIdFromHash(window.location.hash)
      if (requestedId) await openConversation(requestedId, false)
    } catch (reason) {
      error.value = reason instanceof Error ? reason.message : '问数服务载入失败'
    } finally {
      loading.value = false
    }
  }

  const handleRouteChange = () => {
    const requestedId = conversationIdFromHash(window.location.hash)
    if (requestedId && requestedId !== activeConversation.value?.conversationId) {
      void openConversation(requestedId, false)
    } else if (!requestedId && activeConversation.value) {
      newConversation(false)
    }
  }

  onBeforeUnmount(stopPolling)

  return {
    profiles,
    conversations,
    activeConversation,
    activeSummary,
    activeProfile,
    turns,
    selectedProfileId,
    prompt,
    pendingPrompt,
    loading,
    submitting,
    refreshing,
    waiting,
    error,
    notice,
    initialize,
    openConversation,
    newConversation,
    send,
    refreshTurns,
    handleRouteChange
  }
}
