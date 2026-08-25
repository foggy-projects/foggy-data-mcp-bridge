<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type {
  AgentToolCall,
  AgentTurn,
  AgentTurnDetail,
  Conversation,
  ConversationSummary,
  QuestionProfile
} from '../api'
import { formatDuration, formatJsonPayload } from './presentation'

const props = defineProps<{
  profiles: QuestionProfile[]
  activeConversation: Conversation | null
  activeSummary: ConversationSummary | null
  activeProfile: QuestionProfile | null
  turns: AgentTurn[]
  selectedProfileId: string
  prompt: string
  pendingPrompt: string
  loading: boolean
  submitting: boolean
  refreshing: boolean
  turnDetails: Record<string, AgentTurnDetail>
  detailLoading: Record<string, boolean>
  detailErrors: Record<string, string>
  waiting: boolean
  error: string
  notice: string
}>()

const emit = defineEmits<{
  'update:profile': [profileId: string]
  'update:prompt': [prompt: string]
  send: []
  refresh: []
  loadTurnDetail: [askInvocationRef: string]
  newConversation: []
  openSidebar: []
}>()

const transcript = ref<HTMLElement | null>(null)
const expandedTurns = ref<Record<string, boolean>>({})
const expandedTools = ref<Record<string, boolean>>({})
const copiedPayload = ref('')
const title = computed(() => props.activeSummary?.title
  ?? props.activeProfile?.displayName
  ?? '新的分析会话')
const canSend = computed(() => Boolean(
  props.prompt.trim() && props.selectedProfileId && !props.submitting && !props.waiting))
const suggestions = [
  '本月订单量和销售额怎么样？',
  '哪个客户的订单金额最高？',
  '按销售团队比较订单表现'
]

watch(
  () => [
    props.turns.length,
    props.turns.at(-1)?.assistantMessage,
    props.pendingPrompt
  ],
  () => nextTick(() => {
    if (transcript.value) transcript.value.scrollTop = transcript.value.scrollHeight
  }),
  { flush: 'post' }
)

const updateProfile = (event: Event) => {
  emit('update:profile', (event.target as HTMLSelectElement).value)
}

const updatePrompt = (event: Event) => {
  emit('update:prompt', (event.target as HTMLTextAreaElement).value)
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  if (canSend.value) emit('send')
}

const useSuggestion = (value: string) => emit('update:prompt', value)

const toggleTurnDetail = (askInvocationRef: string) => {
  const expanded = !expandedTurns.value[askInvocationRef]
  expandedTurns.value = { ...expandedTurns.value, [askInvocationRef]: expanded }
  if (expanded) emit('loadTurnDetail', askInvocationRef)
}

const formatClock = (value: string | null) => value
  ? new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    }).format(new Date(value))
  : '—'

const stateLabel = (state: string) => ({
  RUNNING: '进行中', SUCCEEDED: '完成', FAILED: '失败'
}[state] ?? state)

const toolKey = (askInvocationRef: string, tool: AgentToolCall) =>
  `${askInvocationRef}:${tool.functionInvocationId}`

const hasPayload = (tool: AgentToolCall) => tool.arguments !== null || tool.result !== null

const toggleToolPayload = (key: string, tool: AgentToolCall) => {
  if (!hasPayload(tool)) return
  expandedTools.value = { ...expandedTools.value, [key]: !expandedTools.value[key] }
}

const fallbackCopy = (content: string) => {
  const area = document.createElement('textarea')
  area.value = content
  area.style.position = 'fixed'
  area.style.opacity = '0'
  document.body.appendChild(area)
  area.select()
  const copied = document.execCommand('copy')
  area.remove()
  return copied
}

const copyPayload = async (
  key: string,
  value: Record<string, unknown> | null
) => {
  const content = formatJsonPayload(value)
  if (!content) return
  let copied = false
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(content)
      copied = true
    } else {
      copied = fallbackCopy(content)
    }
  } catch {
    copied = fallbackCopy(content)
  }
  if (!copied) return
  copiedPayload.value = key
  window.setTimeout(() => {
    if (copiedPayload.value === key) copiedPayload.value = ''
  }, 1_600)
}
</script>

<template>
  <main id="main" class="conversation-view">
    <header class="conversation-header">
      <button class="sidebar-trigger" type="button" aria-label="打开会话列表" @click="$emit('openSidebar')">☰</button>
      <div class="conversation-heading">
        <span>{{ activeConversation ? 'ACTIVE CONVERSATION' : 'NEW CONVERSATION' }}</span>
        <h1>{{ title }}</h1>
      </div>
      <div class="header-actions">
        <button v-if="activeConversation" type="button" class="refresh-button"
                :disabled="refreshing" aria-label="刷新回答" @click="$emit('refresh')">↻</button>
        <details class="scope-details">
          <summary>Namespace 与权限 <span>⌄</span></summary>
          <div>
            <b>{{ activeProfile?.displayName ?? '未选择数据范围' }}</b>
            <p>会话锁定 Namespace；AI 在其中选择合适的 QM。数据权限仍沿用当前用户的 QM/TM 上下文。</p>
            <dl v-if="activeConversation">
              <dt>Namespace</dt><dd>{{ activeConversation.namespace }}</dd>
              <dt>QM</dt><dd>{{ activeConversation.modelName ?? '由 AI 按问题选择' }}</dd>
              <dt>Revision</dt><dd>{{ activeConversation.modelRevision
                ? `${activeConversation.modelRevision.slice(0, 20)}…`
                : '每次调用使用 exact revision' }}</dd>
            </dl>
          </div>
        </details>
      </div>
    </header>

    <div v-if="error || notice" class="chat-status" :class="{ error }" role="status">
      <span>{{ error || notice }}</span>
    </div>

    <section ref="transcript" class="conversation-transcript" aria-live="polite">
      <div v-if="loading" class="loading-conversation">
        <i></i><span>正在恢复分析上下文…</span>
      </div>

      <div v-else-if="!profiles.length" class="chat-empty unavailable">
        <span>SETUP REQUIRED / 01</span>
        <h2>还没有可用的数据范围</h2>
        <p>管理员需要在服务端配置 Namespace profile、FAP Skill/Capability 与 Subject binding。</p>
      </div>

      <div v-else-if="!turns.length && !pendingPrompt" class="chat-empty">
        <div class="opening-mark">F/A</div>
        <span>GOVERNED ANALYTICS / 01</span>
        <h2>今天想从数据里<br />知道什么？</h2>
        <p>直接提问即可，不需要先创建报表。回答只使用已配置的语义模型和你当前拥有的数据权限。</p>
        <div class="suggestion-grid">
          <button v-for="suggestion in suggestions" :key="suggestion" type="button"
                  @click="useSuggestion(suggestion)">
            <span>↗</span>{{ suggestion }}
          </button>
        </div>
      </div>

      <div v-else class="message-stream">
        <template v-for="turn in turns" :key="turn.askInvocationRef">
          <article v-if="turn.userMessage" class="chat-message user-chat-message">
            <div class="message-label"><span>YOU</span><small>{{ turn.operation }}</small></div>
            <p>{{ turn.userMessage }}</p>
          </article>
          <article class="chat-message analyst-chat-message">
            <div class="analyst-avatar">FA</div>
            <div class="message-body">
              <div class="message-label">
                <span>FOGGY ANALYST</span><small>{{ turn.displayState }}</small>
              </div>
              <button
                class="turn-disclosure"
                type="button"
                :aria-expanded="Boolean(expandedTurns[turn.askInvocationRef])"
                :aria-controls="`turn-detail-${turn.askInvocationRef}`"
                @click="toggleTurnDetail(turn.askInvocationRef)"
              >
                <span>{{ turn.definitiveTerminal ? '耗时' : '已耗时' }} {{ formatDuration(turn.durationMs) }}</span>
                <i
                  aria-hidden="true"
                  :class="{ expanded: expandedTurns[turn.askInvocationRef] }"
                ></i>
              </button>
              <div
                v-if="expandedTurns[turn.askInvocationRef]"
                :id="`turn-detail-${turn.askInvocationRef}`"
                class="turn-detail"
              >
                <div v-if="detailLoading[turn.askInvocationRef]" class="detail-loading">
                  <i></i><span>读取本轮安全执行摘要…</span>
                </div>
                <p v-else-if="detailErrors[turn.askInvocationRef]" class="detail-error">
                  {{ detailErrors[turn.askInvocationRef] }}
                </p>
                <template v-else-if="turnDetails[turn.askInvocationRef]">
                  <section v-if="turnDetails[turn.askInvocationRef].agentActivities.length" class="activity-section">
                    <h3><span>AGENT UPDATES</span><em>阶段回复</em></h3>
                    <ol class="activity-list">
                      <li v-for="activity in turnDetails[turn.askInvocationRef].agentActivities"
                          :key="activity.sequence" :class="activity.state.toLowerCase()">
                        <i></i>
                        <div><strong>{{ activity.label }}</strong><small>{{ formatClock(activity.occurredAt) }}</small></div>
                        <code v-if="activity.errorCode">{{ activity.errorCode }}</code>
                      </li>
                    </ol>
                  </section>
                  <section v-if="turnDetails[turn.askInvocationRef].toolCalls.length" class="activity-section tool-section">
                    <h3><span>TOOL CALLS</span><em>{{ turnDetails[turn.askInvocationRef].toolCalls.length }} 次调用</em></h3>
                    <div v-for="tool in turnDetails[turn.askInvocationRef].toolCalls"
                         :key="`${tool.sequence}-${tool.functionInvocationId}`" class="tool-record">
                      <button
                        type="button"
                        class="tool-call"
                        :class="{ expandable: hasPayload(tool) }"
                        :aria-expanded="hasPayload(tool)
                          ? Boolean(expandedTools[toolKey(turn.askInvocationRef, tool)])
                          : undefined"
                        @click="toggleToolPayload(toolKey(turn.askInvocationRef, tool), tool)"
                      >
                        <span class="tool-glyph">⌘</span>
                        <span class="tool-identity">
                          <strong>{{ tool.functionRef }}</strong>
                          <small>{{ formatClock(tool.startedAt) }} · {{ stateLabel(tool.state) }}</small>
                        </span>
                        <em>{{ formatDuration(tool.durationMs) }}</em>
                        <i v-if="hasPayload(tool)"
                           :class="{ expanded: expandedTools[toolKey(turn.askInvocationRef, tool)] }">⌄</i>
                        <code v-if="tool.errorCode">{{ tool.errorCode }}</code>
                      </button>
                      <div v-if="expandedTools[toolKey(turn.askInvocationRef, tool)]"
                           class="tool-payload">
                        <section v-if="tool.arguments">
                          <header>
                            <span>CALL ARGUMENTS</span>
                            <button type="button"
                                    @click="copyPayload(`${toolKey(turn.askInvocationRef, tool)}:arguments`, tool.arguments)">
                              {{ copiedPayload === `${toolKey(turn.askInvocationRef, tool)}:arguments` ? '已复制' : '复制 JSON' }}
                            </button>
                          </header>
                          <pre><code>{{ formatJsonPayload(tool.arguments) }}</code></pre>
                        </section>
                        <section v-if="tool.result">
                          <header>
                            <span>FUNCTION RESULT</span>
                            <em v-if="tool.callbackHttpStatus">HTTP {{ tool.callbackHttpStatus }}</em>
                            <button type="button"
                                    @click="copyPayload(`${toolKey(turn.askInvocationRef, tool)}:result`, tool.result)">
                              {{ copiedPayload === `${toolKey(turn.askInvocationRef, tool)}:result` ? '已复制' : '复制 JSON' }}
                            </button>
                          </header>
                          <pre><code>{{ formatJsonPayload(tool.result) }}</code></pre>
                        </section>
                      </div>
                    </div>
                  </section>
                  <p v-if="!turnDetails[turn.askInvocationRef].agentActivities.length
                           && !turnDetails[turn.askInvocationRef].toolCalls.length"
                     class="detail-empty">本轮暂无可展示的安全执行事件。</p>
                  <div class="trace-note">
                    <span>{{ turnDetails[turn.askInvocationRef].historyState }}</span>
                    阶段状态来自 FAP Service Provider；原始参数和结果来自 Console Function 回调。
                  </div>
                </template>
              </div>
              <p>{{ turn.assistantMessage || turn.failureCode || '正在读取受治理数据…' }}</p>
              <div v-if="!turn.definitiveTerminal" class="thinking-line"><i></i><i></i><i></i></div>
            </div>
          </article>
        </template>
        <article v-if="pendingPrompt" class="chat-message user-chat-message pending-chat-message">
          <div class="message-label"><span>YOU</span><small>QUEUED</small></div>
          <p>{{ pendingPrompt }}</p>
        </article>
      </div>
    </section>

    <footer class="composer-dock">
      <form class="chat-composer" @submit.prevent="$emit('send')">
        <div class="composer-scope">
          <label for="chat-profile">Namespace</label>
          <select id="chat-profile" :value="selectedProfileId"
                  :disabled="Boolean(activeConversation)" @change="updateProfile">
            <option v-for="profile in profiles" :key="profile.profileId" :value="profile.profileId">
              {{ profile.displayName }} · {{ profile.namespace }}
            </option>
          </select>
          <span v-if="activeConversation"><i></i> NAMESPACE 已锁定</span>
        </div>
        <div class="composer-input">
          <textarea
            :value="prompt"
            :disabled="!profiles.length"
            rows="2"
            aria-label="输入你的数据问题"
            placeholder="问订单、收入、客户、履约……"
            @input="updatePrompt"
            @keydown="handleKeydown"
          ></textarea>
          <button type="submit" :disabled="!canSend" aria-label="发送问题">
            <span>{{ submitting || waiting ? '···' : '↑' }}</span>
          </button>
        </div>
        <div class="composer-note">
          <span>Enter 发送 · Shift + Enter 换行</span>
          <span>不接受原始 SQL · 不自动创建报表</span>
        </div>
      </form>
    </footer>
  </main>
</template>

<style scoped>
.conversation-view { display: grid; grid-template-rows: 70px minmax(0, 1fr) auto; min-width: 0; height: 100vh; overflow: hidden; background: var(--paper-light); }
.conversation-header { position: relative; z-index: 8; display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 0 27px; border-bottom: 1px solid var(--ink); background: rgba(248, 244, 234, .96); }
.conversation-heading { min-width: 0; }
.conversation-heading > span { display: block; color: var(--cobalt); font: 800 8px var(--mono); letter-spacing: .12em; }
.conversation-heading h1 { margin: 5px 0 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font: 800 18px/1 var(--serif); letter-spacing: -.03em; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.refresh-button { width: 36px; height: 36px; border: 1px solid var(--line); background: transparent; cursor: pointer; font-size: 20px; }
.refresh-button:hover { border-color: var(--ink); background: var(--paper); }
.scope-details { position: relative; }
.scope-details summary { min-width: 126px; padding: 10px 11px; border: 1px solid var(--ink); cursor: pointer; list-style: none; background: var(--paper-light); font: 700 10px var(--mono); }
.scope-details summary::-webkit-details-marker { display: none; }
.scope-details summary span { float: right; }
.scope-details > div { position: absolute; top: 44px; right: 0; width: 320px; padding: 17px; border: 1px solid var(--ink); background: var(--paper-light); box-shadow: 6px 6px 0 var(--ink); }
.scope-details b { font: 800 17px var(--serif); }
.scope-details p { margin: 8px 0 14px; color: var(--ink-soft); font-size: 11px; line-height: 1.65; }
.scope-details dl { display: grid; grid-template-columns: 72px 1fr; gap: 6px 10px; margin: 0; padding-top: 11px; border-top: 1px solid var(--line); font: 9px/1.4 var(--mono); }
.scope-details dt { color: var(--ink-soft); }
.scope-details dd { min-width: 0; margin: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sidebar-trigger { display: none; }
.chat-status { position: fixed; z-index: 30; top: 82px; left: calc(50% + 130px); transform: translateX(-50%); padding: 9px 14px; border: 1px solid var(--ink); background: var(--acid); box-shadow: 4px 4px 0 var(--ink); font-size: 11px; }
.chat-status.error { background: var(--vermilion); color: white; }
.conversation-transcript { min-height: 0; overflow-y: auto; scroll-behavior: smooth; background: linear-gradient(90deg, transparent 0, transparent calc(50% - 430px), rgba(23, 33, 31, .04) calc(50% - 430px), rgba(23, 33, 31, .04) calc(50% - 429px), transparent calc(50% - 429px)); }
.loading-conversation { min-height: 100%; display: flex; align-items: center; justify-content: center; gap: 10px; color: var(--ink-soft); font: 10px var(--mono); }
.loading-conversation i { width: 13px; height: 13px; border: 2px solid var(--paper-deep); border-top-color: var(--cobalt); border-radius: 50%; animation: chat-spin .7s linear infinite; }
@keyframes chat-spin { to { transform: rotate(360deg); } }
.chat-empty { min-height: 100%; display: grid; place-content: center; justify-items: center; padding: 55px 24px 30px; text-align: center; }
.opening-mark { display: grid; width: 63px; height: 63px; place-items: center; margin-bottom: 21px; border: 1px solid var(--ink); background: var(--acid); box-shadow: 5px 5px 0 var(--cobalt); font: 900 18px var(--serif); transform: rotate(-2deg); }
.chat-empty > span { color: var(--cobalt); font: 800 9px var(--mono); letter-spacing: .15em; }
.chat-empty h2 { margin: 13px 0 0; font: 900 clamp(37px, 5vw, 62px)/.94 var(--serif); letter-spacing: -.075em; }
.chat-empty > p { max-width: 550px; margin: 15px auto 0; color: var(--ink-soft); font-size: 12px; line-height: 1.75; }
.chat-empty.unavailable h2 { font-size: 36px; }
.suggestion-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 9px; width: min(720px, 100%); margin-top: 31px; }
.suggestion-grid button { display: grid; grid-template-columns: auto 1fr; gap: 10px; min-height: 65px; padding: 12px; border: 1px solid var(--line); background: transparent; cursor: pointer; color: var(--ink-soft); text-align: left; font-size: 11px; line-height: 1.45; }
.suggestion-grid button span { color: var(--vermilion); font-size: 16px; }
.suggestion-grid button:hover { border-color: var(--ink); background: var(--paper); color: var(--ink); box-shadow: 3px 3px 0 var(--paper-deep); }
.message-stream { width: min(820px, calc(100% - 48px)); margin: 0 auto; padding: 45px 0 36px; }
.chat-message { margin-bottom: 30px; }
.message-label { display: flex; align-items: center; gap: 9px; margin-bottom: 8px; font: 800 8px var(--mono); letter-spacing: .1em; }
.message-label small { color: var(--ink-soft); font: 8px var(--mono); }
.chat-message p { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.user-chat-message { width: min(72%, 600px); margin-left: auto; padding: 13px 16px; border: 1px solid var(--ink); border-right: 5px solid var(--vermilion); background: var(--paper); box-shadow: 3px 3px 0 var(--paper-deep); }
.user-chat-message p { font-size: 13px; line-height: 1.65; }
.analyst-chat-message { display: grid; grid-template-columns: 38px 1fr; gap: 15px; }
.analyst-avatar { display: grid; width: 36px; height: 36px; place-items: center; border: 1px solid var(--ink); background: var(--cobalt); color: white; font: 800 10px var(--serif); box-shadow: 3px 3px 0 var(--acid); }
.message-body { min-width: 0; padding: 3px 0 0; }
.analyst-chat-message p { font-size: 14px; line-height: 1.85; }
.turn-disclosure { display: inline-flex; align-items: center; gap: 7px; min-height: 29px; margin: -2px 0 12px; padding: 0 9px; border: 1px solid transparent; background: transparent; color: var(--ink-soft); cursor: pointer; font: 700 10px var(--mono); }
.turn-disclosure:hover, .turn-disclosure[aria-expanded="true"] { border-color: var(--line); background: var(--paper); color: var(--ink); }
.turn-disclosure i { display: inline-block; width: 6px; height: 6px; margin-top: -2px; border-right: 1.5px solid currentColor; border-bottom: 1.5px solid currentColor; transform: rotate(-45deg); transform-origin: center; transition: transform .16s ease; }
.turn-disclosure i.expanded { transform: rotate(45deg); }
.turn-detail { position: relative; margin: 0 0 17px; padding: 2px 0 2px 17px; border-left: 1px solid var(--ink); animation: detail-reveal .18s ease-out; }
@keyframes detail-reveal { from { opacity: 0; transform: translateY(-5px); } to { opacity: 1; transform: translateY(0); } }
.detail-loading { display: flex; align-items: center; gap: 8px; min-height: 44px; color: var(--ink-soft); font: 9px var(--mono); }
.detail-loading i { width: 11px; height: 11px; border: 2px solid var(--paper-deep); border-top-color: var(--cobalt); border-radius: 50%; animation: chat-spin .7s linear infinite; }
.turn-detail .detail-error, .turn-detail .detail-empty { padding: 11px 12px; background: var(--paper); color: var(--ink-soft); font: 10px/1.6 var(--mono); }
.turn-detail .detail-error { border-left: 3px solid var(--vermilion); color: var(--vermilion); }
.activity-section + .activity-section { margin-top: 15px; }
.activity-section h3 { display: flex; align-items: center; justify-content: space-between; margin: 0 0 8px; padding-bottom: 7px; border-bottom: 1px solid var(--line); }
.activity-section h3 span { color: var(--cobalt); font: 800 8px var(--mono); letter-spacing: .12em; }
.activity-section h3 em { color: var(--ink-soft); font: normal 9px var(--sans); }
.activity-list { margin: 0; padding: 0; list-style: none; }
.activity-list li { position: relative; display: grid; grid-template-columns: 11px minmax(0, 1fr) auto; gap: 8px; align-items: start; min-height: 31px; padding: 5px 0; }
.activity-list li::before { content: ''; position: absolute; top: 18px; bottom: -9px; left: 4px; width: 1px; background: var(--line); }
.activity-list li:last-child::before { display: none; }
.activity-list li > i { position: relative; z-index: 1; width: 9px; height: 9px; margin-top: 2px; border: 2px solid var(--paper-light); border-radius: 50%; background: var(--cobalt); box-shadow: 0 0 0 1px var(--cobalt); }
.activity-list li.failed > i { background: var(--vermilion); box-shadow: 0 0 0 1px var(--vermilion); }
.activity-list li.running > i { background: var(--acid); box-shadow: 0 0 0 1px var(--ink); }
.activity-list strong, .activity-list small { display: block; }
.activity-list strong { font-size: 10px; }
.activity-list small { margin-top: 3px; color: var(--ink-soft); font: 8px var(--mono); }
.activity-list code { color: var(--vermilion); font: 8px var(--mono); }
.tool-record { margin-top: 6px; border: 1px solid var(--line); background: var(--paper); }
.tool-call { display: grid; grid-template-columns: 27px minmax(0, 1fr) auto 14px; gap: 9px; align-items: center; width: 100%; min-height: 48px; padding: 8px 10px; border: 0; background: transparent; color: var(--ink); text-align: left; }
.tool-call.expandable { cursor: pointer; }
.tool-call.expandable:hover, .tool-call.expandable[aria-expanded="true"] { background: var(--paper-light); }
.tool-glyph { display: grid; width: 26px; height: 26px; place-items: center; border: 1px solid var(--ink); color: var(--cobalt); font: 800 12px var(--mono); }
.tool-identity { min-width: 0; }
.tool-call strong, .tool-call small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tool-call strong { font: 700 9px var(--mono); }
.tool-call small { margin-top: 4px; color: var(--ink-soft); font: 8px var(--mono); }
.tool-call > em { color: var(--ink-soft); font: normal 9px var(--mono); }
.tool-call > i { color: var(--ink-soft); font: normal 13px/1 var(--sans); transition: transform .16s ease; }
.tool-call > i.expanded { transform: rotate(180deg); }
.tool-call > code { grid-column: 2 / -1; color: var(--vermilion); font: 8px var(--mono); }
.tool-payload { display: grid; gap: 1px; border-top: 1px solid var(--ink); background: var(--ink); animation: detail-reveal .16s ease-out; }
.tool-payload section { min-width: 0; background: #101917; color: #ecf2e8; }
.tool-payload header { display: flex; align-items: center; gap: 9px; min-height: 35px; padding: 0 10px; border-bottom: 1px solid rgba(236, 242, 232, .16); }
.tool-payload header span { color: var(--acid); font: 800 8px var(--mono); letter-spacing: .12em; }
.tool-payload header em { margin-left: auto; color: rgba(236, 242, 232, .58); font: normal 8px var(--mono); }
.tool-payload header button { margin-left: auto; padding: 4px 7px; border: 1px solid rgba(236, 242, 232, .32); background: transparent; color: #ecf2e8; cursor: pointer; font: 7px var(--mono); }
.tool-payload header em + button { margin-left: 0; }
.tool-payload header button:hover { border-color: var(--acid); color: var(--acid); }
.tool-payload pre { max-height: 320px; margin: 0; padding: 12px; overflow: auto; white-space: pre; tab-size: 2; }
.tool-payload pre code { color: #d9e3d5; font: 9px/1.65 var(--mono); }
.trace-note { margin-top: 11px; color: var(--ink-soft); font: 8px/1.55 var(--mono); }
.trace-note span { margin-right: 6px; padding: 2px 5px; background: var(--ink); color: var(--paper-light); letter-spacing: .05em; }
.pending-chat-message { opacity: .65; }
.thinking-line { display: flex; gap: 4px; margin-top: 13px; }
.thinking-line i { width: 5px; height: 5px; border-radius: 50%; background: var(--cobalt); animation: pulse 1.1s ease-in-out infinite; }
.thinking-line i:nth-child(2) { animation-delay: .15s; }
.thinking-line i:nth-child(3) { animation-delay: .3s; }
@keyframes pulse { 0%, 80%, 100% { opacity: .25; transform: translateY(0); } 40% { opacity: 1; transform: translateY(-3px); } }
.composer-dock { position: relative; z-index: 6; padding: 10px 24px 19px; background: linear-gradient(0deg, var(--paper-light) 78%, rgba(248, 244, 234, 0)); }
.chat-composer { width: min(850px, 100%); margin: 0 auto; border: 1px solid var(--ink); background: var(--paper-light); box-shadow: 6px 6px 0 rgba(23, 33, 31, .16); }
.composer-scope { display: flex; align-items: center; gap: 10px; min-height: 37px; padding: 5px 10px; border-bottom: 1px solid var(--line); background: var(--paper); }
.composer-scope label { color: var(--ink-soft); font: 800 8px var(--mono); letter-spacing: .08em; text-transform: uppercase; }
.composer-scope select { min-height: 27px; max-width: 260px; padding: 0 25px 0 7px; border: 0; background: transparent; font-size: 11px; font-weight: 700; }
.composer-scope > span { margin-left: auto; color: var(--cobalt); font: 800 8px var(--mono); letter-spacing: .05em; }
.composer-scope > span i { display: inline-block; width: 6px; height: 6px; margin-right: 4px; border-radius: 50%; background: var(--acid); border: 1px solid var(--ink); }
.composer-input { display: grid; grid-template-columns: 1fr 45px; gap: 9px; align-items: end; padding: 10px 10px 5px 14px; }
.composer-input textarea { width: 100%; min-height: 50px; max-height: 150px; padding: 6px 0; resize: none; border: 0; outline: 0; background: transparent; font: 14px/1.55 var(--sans); }
.composer-input button { display: grid; width: 40px; height: 40px; place-items: center; border: 1px solid var(--ink); background: var(--ink); color: white; cursor: pointer; box-shadow: 3px 3px 0 var(--vermilion); }
.composer-input button span { font: 20px/1 var(--sans); }
.composer-input button:disabled { background: var(--paper-deep); color: var(--line); border-color: var(--line); box-shadow: none; }
.composer-note { display: flex; justify-content: space-between; gap: 20px; padding: 2px 14px 9px; color: var(--ink-soft); font: 8px var(--mono); }

@media (max-width: 760px) {
  .conversation-header { padding: 0 13px; gap: 10px; }
  .sidebar-trigger { display: block; width: 36px; height: 36px; flex: 0 0 auto; border: 1px solid var(--ink); background: transparent; cursor: pointer; }
  .conversation-heading > span { display: none; }
  .conversation-heading h1 { max-width: 42vw; font-size: 15px; }
  .refresh-button { display: none; }
  .scope-details summary { min-width: 0; font-size: 0; }
  .scope-details summary::before { content: '权限'; font-size: 9px; }
  .scope-details summary span { margin-left: 6px; font-size: 10px; }
  .scope-details > div { width: min(310px, calc(100vw - 24px)); }
  .chat-status { left: 50%; max-width: calc(100vw - 30px); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .chat-empty { padding-inline: 16px; }
  .chat-empty h2 { font-size: 39px; }
  .suggestion-grid { grid-template-columns: 1fr; margin-top: 24px; }
  .suggestion-grid button { min-height: 48px; }
  .message-stream { width: calc(100% - 26px); padding-top: 28px; }
  .user-chat-message { width: 88%; }
  .turn-detail { padding-left: 12px; }
  .composer-dock { padding: 8px 9px 12px; }
  .composer-scope > span { display: none; }
  .composer-note span:last-child { display: none; }
}
</style>
