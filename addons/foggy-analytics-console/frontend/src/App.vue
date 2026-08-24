<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  api,
  type AgentTurn,
  type Conversation,
  type CreateDraftInput,
  type QuestionProfile
} from './api'
import {
  assetsInFolder,
  canDesign,
  shortRevision,
  type Asset,
  type AssetDetail,
  type Folder,
  type RenderResult,
  type Session
} from './domain'

const session = ref<Session | null>(null)
const folders = ref<Folder[]>([])
const assets = ref<Asset[]>([])
const selectedFolder = ref<string | null>(null)
const selectedId = ref<string | null>(null)
const detail = ref<AssetDetail | null>(null)
const editor = ref('')
const preview = ref<RenderResult | null>(null)
const busy = ref('')
const error = ref('')
const notice = ref('')
const newFolderName = ref('')
const showCreate = ref(false)
const showAgent = ref(true)
const workspaceMode = ref<'QUESTION' | 'STUDIO'>('QUESTION')
const agentPrompt = ref('请检查当前定义，并给出更清晰的指标、图表和布局建议。')
const designConversation = ref<Conversation | null>(null)
const designTurns = ref<AgentTurn[]>([])
const questionProfiles = ref<QuestionProfile[]>([])
const selectedQuestionProfile = ref('')
const questionPrompt = ref('本月订单量和销售额怎么样？和上月相比有什么变化？')
const questionConversation = ref<Conversation | null>(null)
const questionTurns = ref<AgentTurn[]>([])
const pendingQuestion = ref('')

const draft = ref<CreateDraftInput>({
  title: '',
  description: '',
  folderId: null,
  kind: 'REPORT',
  bundleRef: '',
  artifactRef: '',
  expectedBundleRevision: '',
  definitionContent: null
})

const selected = computed(() => detail.value?.asset ?? null)
const designer = computed(() => canDesign(session.value))
const visibleAssets = computed(() => assetsInFolder(assets.value, selectedFolder.value))
const validated = computed(() => Boolean(selected.value
  && selected.value.validatedBundleRevision === selected.value.bundleRevision))
const editable = computed(() => Boolean(designer.value && selected.value?.status === 'DRAFT'))

const run = async (label: string, action: () => Promise<void>) => {
  busy.value = label
  error.value = ''
  notice.value = ''
  try {
    await action()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '操作失败'
  } finally {
    busy.value = ''
  }
}

const refreshCatalog = async () => {
  const [nextSession, nextFolders, nextAssets] = await Promise.all([
    api.session(), api.folders(), api.assets()
  ])
  session.value = nextSession
  folders.value = nextFolders
  assets.value = nextAssets
  try {
    questionProfiles.value = await api.questionProfiles()
    selectedQuestionProfile.value ||= questionProfiles.value[0]?.profileId ?? ''
  } catch {
    questionProfiles.value = []
  }
  if (!selectedId.value && nextAssets.length) await loadAsset(nextAssets[0].assetId)
}

const loadAsset = async (assetId: string) => {
  selectedId.value = assetId
  preview.value = null
  const loaded = await api.asset(assetId)
  detail.value = loaded
  editor.value = loaded.definitionContent ?? ''
}

const openAsset = async (assetId: string) => {
  workspaceMode.value = 'STUDIO'
  designConversation.value = null
  designTurns.value = []
  await loadAsset(assetId)
}

const mergeAsset = (asset: Asset) => {
  assets.value = assets.value.map(item => item.assetId === asset.assetId ? asset : item)
  if (detail.value?.asset.assetId === asset.assetId) detail.value.asset = asset
}

const createFolder = () => run('创建目录', async () => {
  if (!newFolderName.value.trim()) return
  const folder = await api.createFolder(newFolderName.value.trim())
  folders.value = [...folders.value, folder].sort((a, b) => a.name.localeCompare(b.name))
  selectedFolder.value = folder.folderId
  newFolderName.value = ''
  notice.value = '目录已创建'
})

const createDraft = () => run('创建草稿', async () => {
  const created = await api.createDraft({ ...draft.value, folderId: selectedFolder.value })
  showCreate.value = false
  assets.value = [created, ...assets.value]
  await openAsset(created.assetId)
  notice.value = '草稿已建立，当前 Bundle revision 已锁定'
})

const save = () => selected.value && run('保存定义', async () => {
  const saved = await api.saveDefinition(
    selected.value!.assetId, selected.value!.bundleRevision, editor.value)
  mergeAsset(saved)
  preview.value = null
  notice.value = '定义已原子保存；请重新校验'
})

const validate = () => selected.value && run('校验定义', async () => {
  const checked = await api.validate(selected.value!.assetId, selected.value!.bundleRevision)
  mergeAsset(checked)
  notice.value = '结构、引用和当前依赖均已校验'
})

const renderPreview = () => selected.value && run('生成预览', async () => {
  preview.value = await api.preview(selected.value!.assetId, selected.value!.bundleRevision)
  notice.value = '预览使用当前用户的数据权限生成'
})

const publish = () => selected.value && run('发布', async () => {
  const published = await api.publish(selected.value!.assetId, selected.value!.bundleRevision)
  mergeAsset(published)
  notice.value = '已发布 exact revision；定义编辑现已锁定'
})

const toggleAudience = () => selected.value && run('更新可见范围', async () => {
  const visibility = selected.value!.visibility === 'CONSOLE' ? 'PRIVATE' : 'CONSOLE'
  const updated = await api.audience(selected.value!.assetId, visibility, [])
  mergeAsset(updated)
  notice.value = visibility === 'CONSOLE' ? '已对 Console 用户开放' : '已改为私有'
})

const askAgent = () => selected.value && run('提交给 FAP', async () => {
  designConversation.value = await api.ask(selected.value!.assetId, agentPrompt.value)
  designTurns.value = []
  notice.value = 'FAP 已接受设计任务；定义不会被自动发布'
})

const refreshDesignTurns = () => designConversation.value && run('刷新设计建议', async () => {
  designTurns.value = await api.turns(designConversation.value!.conversationId)
})

const askQuestion = () => run('提交问题', async () => {
  const prompt = questionPrompt.value.trim()
  if (!prompt || !selectedQuestionProfile.value) return
  pendingQuestion.value = prompt
  questionConversation.value = questionConversation.value
    ? await api.continueConversation(questionConversation.value.conversationId, prompt)
    : await api.askQuestion(selectedQuestionProfile.value, prompt)
  questionPrompt.value = ''
  notice.value = '问题已进入 FAP；回答将使用当前用户的 QM/TM 数据权限'
})

const refreshQuestionTurns = () => questionConversation.value && run('刷新回答', async () => {
  questionTurns.value = await api.turns(questionConversation.value!.conversationId)
  if (questionTurns.value.some(turn => turn.userMessage === pendingQuestion.value)) {
    pendingQuestion.value = ''
  }
})

const newQuestion = () => {
  questionConversation.value = null
  questionTurns.value = []
  pendingQuestion.value = ''
  questionPrompt.value = ''
}

onMounted(() => run('载入工作台', refreshCatalog))
</script>

<template>
  <div class="app-frame" :class="{ 'question-mode': workspaceMode === 'QUESTION' }">
    <header class="masthead">
      <div class="brand-block">
        <span class="edition">FOGGY / ANALYTICS 01</span>
        <h1>分析工作室</h1>
      </div>
      <div class="masthead-center">
        <span class="signal-dot"></span>
        <span>JAVA RUNTIME</span>
        <b>CONNECTED</b>
      </div>
      <div v-if="session" class="identity-block">
        <span>{{ session.roles.join(' · ') }}</span>
        <strong>{{ session.displayName }}</strong>
      </div>
    </header>

    <div v-if="error || notice" class="message-line" :class="{ danger: error }" role="status">
      <span>{{ error || notice }}</span>
      <button type="button" aria-label="关闭消息" @click="error = ''; notice = ''">×</button>
    </div>

    <aside class="library-rail">
      <button class="ask-entry" :class="{ active: workspaceMode === 'QUESTION' }"
              @click="workspaceMode = 'QUESTION'">
        <span>ASK / 直接问数</span>
        <strong>先问一个业务问题</strong>
        <em>→</em>
      </button>
      <div class="rail-heading">
        <span>COLLECTIONS</span>
        <b>{{ assets.length.toString().padStart(2, '0') }}</b>
      </div>
      <button class="folder-row" :class="{ active: workspaceMode === 'STUDIO' && selectedFolder === null }"
              @click="workspaceMode = 'STUDIO'; selectedFolder = null">
        <span>全部分析</span><em>{{ assets.length }}</em>
      </button>
      <button v-for="folder in folders" :key="folder.folderId" class="folder-row"
              :class="{ active: selectedFolder === folder.folderId }"
              @click="workspaceMode = 'STUDIO'; selectedFolder = folder.folderId">
        <span>{{ folder.name }}</span>
        <em>{{ assets.filter(asset => asset.folderId === folder.folderId).length }}</em>
      </button>
      <form v-if="designer" class="folder-form" @submit.prevent="createFolder">
        <input v-model="newFolderName" aria-label="新目录名称" placeholder="新建目录…" />
        <button type="submit" :disabled="Boolean(busy)">＋</button>
      </form>
      <div class="rail-note">
        <b>边界说明</b>
        <p>目录、Owner 与展示权限由本 Console 管理；数据权限继续由 QM/TM 和当前用户上下文执行。</p>
      </div>
    </aside>

    <main id="main" class="workspace">
      <section v-if="workspaceMode === 'QUESTION'" class="question-workspace">
        <div class="question-header">
          <div>
            <span>01 / ASK YOUR DATA</span>
            <h2>先问问题，<br />再决定要不要做报表。</h2>
          </div>
          <p>FAP 负责理解问题，Java Analytics 只执行受治理的语义查询。每次回答都沿用你当前的 QM/TM 数据权限。</p>
        </div>

        <div v-if="questionProfiles.length" class="question-stage">
          <div class="scope-strip">
            <label for="question-profile">数据范围</label>
            <select id="question-profile" v-model="selectedQuestionProfile"
                    :disabled="Boolean(questionConversation)">
              <option v-for="profile in questionProfiles" :key="profile.profileId"
                      :value="profile.profileId">
                {{ profile.displayName }}
              </option>
            </select>
            <span v-if="questionConversation">EXACT MODEL REVISION LOCKED</span>
            <button v-if="questionConversation" class="text-button" @click="newQuestion">新对话</button>
          </div>

          <div class="question-transcript" aria-live="polite">
            <div v-if="!questionTurns.length && !pendingQuestion" class="question-opening">
              <span>NO REPORT REQUIRED</span>
              <strong>直接说你想知道什么</strong>
              <p>例如：哪个区域本月收入下降最多？大客户的准时交付率如何？回答不会自动生成或保存报表。</p>
            </div>
            <template v-for="turn in questionTurns" :key="turn.askInvocationRef">
              <div v-if="turn.userMessage" class="message user-message">
                <span>YOU / {{ turn.operation }}</span><p>{{ turn.userMessage }}</p>
              </div>
              <div class="message analyst-message">
                <span>FOGGY ANALYST / {{ turn.displayState }}</span>
                <p>{{ turn.assistantMessage || turn.failureCode || '正在读取受治理数据…' }}</p>
              </div>
            </template>
            <div v-if="pendingQuestion" class="message user-message pending-message">
              <span>YOU / QUEUED</span><p>{{ pendingQuestion }}</p>
            </div>
          </div>

          <form class="question-composer" @submit.prevent="askQuestion">
            <label for="question-input">你的问题</label>
            <textarea id="question-input" v-model="questionPrompt"
                      placeholder="问订单、收入、客户、履约……" required></textarea>
            <div>
              <small>不接受原始 SQL；复杂或含糊的问题会先向你澄清。</small>
              <button class="ink-button" :disabled="Boolean(busy) || !questionPrompt.trim()">
                {{ questionConversation ? '继续追问' : '开始分析' }}
              </button>
            </div>
          </form>
          <button v-if="questionConversation" class="refresh-answer paper-button"
                  :disabled="Boolean(busy)" @click="refreshQuestionTurns">刷新回答</button>
        </div>

        <div v-else class="question-unavailable">
          <span>FAP NOT CONFIGURED</span>
          <h3>直接问数尚未绑定数据范围</h3>
          <p>管理员需要配置 question profile、FAP Skill/Capability 和服务端 Subject binding。报表设计能力仍可独立使用。</p>
          <button class="paper-button" @click="workspaceMode = 'STUDIO'">进入分析资产</button>
        </div>
      </section>

      <template v-else>
      <section class="catalog-strip">
        <div class="section-title">
          <div><span>01 / CATALOG</span><h2>分析资产</h2></div>
          <button v-if="designer" class="ink-button" @click="showCreate = true">新建草稿</button>
        </div>
        <div class="asset-cards">
          <button v-for="asset in visibleAssets" :key="asset.assetId" class="asset-card"
                  :class="{ selected: selectedId === asset.assetId }" @click="openAsset(asset.assetId)">
            <span class="asset-index">{{ asset.kind === 'REPORT' ? 'R' : 'D' }}</span>
            <strong>{{ asset.title }}</strong>
            <small>{{ asset.status }} · {{ shortRevision(asset.bundleRevision) }}</small>
            <i :class="asset.status.toLowerCase()"></i>
          </button>
          <div v-if="!visibleAssets.length" class="empty-card">当前目录没有可见资产</div>
        </div>
      </section>

      <section v-if="detail" class="studio-grid">
        <article class="editor-panel">
          <div class="panel-kicker">
            <span>02 / DEFINITION</span>
            <span>{{ detail.definitionContent !== null ? detail.asset.resourcePath : 'PUBLISHED VIEW' }}</span>
          </div>
          <div class="asset-heading">
            <div>
              <h2>{{ detail.asset.title }}</h2>
              <p>{{ detail.asset.description || '暂无说明' }}</p>
            </div>
            <div class="status-stamp" :class="detail.asset.status.toLowerCase()">
              {{ detail.asset.status }}
            </div>
          </div>
          <div class="revision-line">
            <span>BUNDLE</span><b>{{ detail.asset.bundleRef }}</b>
            <span>REVISION</span><code>{{ shortRevision(detail.asset.bundleRevision) }}</code>
            <span>VALIDATED</span><b>{{ validated ? 'YES' : 'NO' }}</b>
          </div>
          <template v-if="detail.definitionContent !== null">
            <label class="editor-label" for="definition-editor">定义 JSON</label>
            <textarea id="definition-editor" v-model="editor" spellcheck="false" :readonly="!editable"></textarea>
          </template>
          <div v-else class="definition-locked">
            <span>CONSUMER VIEW</span>
            <strong>已发布定义由设计者维护</strong>
            <p>当前身份只消费渲染结果，不读取或修改底层定义文件。</p>
          </div>
          <div class="action-bar">
            <button v-if="editable" class="paper-button" :disabled="Boolean(busy)" @click="save">保存</button>
            <button v-if="editable" class="paper-button" :disabled="Boolean(busy)" @click="validate">校验</button>
            <button class="paper-button" :disabled="Boolean(busy)" @click="renderPreview">预览</button>
            <button v-if="editable" class="ink-button" :disabled="Boolean(busy) || !validated" @click="publish">
              发布当前 Revision
            </button>
            <button v-if="detail.asset.status === 'PUBLISHED' && designer" class="text-button" @click="toggleAudience">
              {{ detail.asset.visibility === 'CONSOLE' ? '改为私有' : '开放给 Console' }}
            </button>
          </div>
        </article>

        <article class="preview-panel">
          <div class="panel-kicker"><span>03 / PREVIEW</span><span>GOVERNED DATA</span></div>
          <div v-if="preview" class="render-sheet">
            <div class="render-header">
              <span>{{ preview.artifact.kind }}</span><strong>{{ preview.artifact.ref }}</strong>
              <em>{{ preview.state }}</em>
            </div>
            <section v-for="widget in preview.widgets" :key="widget.widgetRef" class="widget-sheet">
              <div class="widget-heading">
                <strong>{{ widget.widgetRef }}</strong><span>{{ widget.visual.kind }}</span>
              </div>
              <div class="table-wrap">
                <table v-if="widget.rows.length">
                  <thead><tr><th v-for="column in widget.columns" :key="column.name">{{ column.name }}</th></tr></thead>
                  <tbody><tr v-for="(row, index) in widget.rows.slice(0, 8)" :key="index">
                    <td v-for="column in widget.columns" :key="column.name">{{ row[column.name] ?? '—' }}</td>
                  </tr></tbody>
                </table>
                <p v-else class="empty-preview">查询成功，但当前范围没有数据。</p>
              </div>
            </section>
          </div>
          <div v-else class="preview-placeholder">
            <span>PREVIEW</span>
            <strong>用当前身份生成真实预览</strong>
            <p>预览不会读取 Console ACL 作为数据过滤器，也不会绕过 QM/TM 权限。</p>
          </div>
        </article>
      </section>
      </template>
    </main>

    <aside v-if="workspaceMode === 'QUESTION'" class="question-context">
      <div class="agent-title"><span>ANSWER CONTRACT</span></div>
      <div class="context-number">01</div>
      <h3>不是报表设计的前置步骤</h3>
      <p>你可以只完成问答并离开。只有当结果值得长期复用时，后续才考虑保存为 Report 或 Dashboard。</p>
      <dl>
        <div><dt>理解与编排</dt><dd>FAP</dd></div>
        <div><dt>语义查询</dt><dd>JAVA ANALYTICS</dd></div>
        <div><dt>数据权限</dt><dd>QM / TM</dd></div>
        <div><dt>自动写入</dt><dd>NONE</dd></div>
      </dl>
    </aside>

    <aside v-else-if="detail && showAgent" class="agent-rail">
      <div class="agent-title"><span>FAP / DESIGN ASSIST</span><button @click="showAgent = false">×</button></div>
      <div class="agent-manifest">
        <i></i><div><b>只读协助</b><p>Agent 可以检查、预览并提出修改建议；保存和发布仍由你确认。</p></div>
      </div>
      <div class="turns">
        <div v-for="turn in designTurns" :key="turn.askInvocationRef" class="turn">
          <span>{{ turn.displayState }}</span>
          <p>{{ turn.assistantMessage || turn.failureCode || '任务处理中…' }}</p>
        </div>
        <div v-if="designConversation && !designTurns.length" class="turn pending"><span>ACCEPTED</span><p>任务已进入 FAP。</p></div>
      </div>
      <textarea v-model="agentPrompt" aria-label="给 Analytics Agent 的问题"></textarea>
      <div class="agent-actions">
        <button class="ink-button" :disabled="Boolean(busy) || !editable" @click="askAgent">提交设计任务</button>
        <button v-if="designConversation" class="paper-button" :disabled="Boolean(busy)" @click="refreshDesignTurns">刷新结果</button>
      </div>
    </aside>
    <button v-else-if="workspaceMode === 'STUDIO' && detail" class="agent-tab" @click="showAgent = true">FAP<br />ASSIST</button>

    <div v-if="showCreate" class="modal-backdrop" @click.self="showCreate = false">
      <form class="create-sheet" @submit.prevent="createDraft">
        <div class="sheet-heading"><span>NEW ANALYTICS</span><button type="button" @click="showCreate = false">×</button></div>
        <h2>建立设计草稿</h2>
        <p>草稿绑定一个已注册的 runtime-owned Analytics Bundle。发布后该 Bundle 在 Console 中锁定。</p>
        <label>标题<input v-model="draft.title" required /></label>
        <label>说明<input v-model="draft.description" /></label>
        <div class="form-pair">
          <label>类型<select v-model="draft.kind"><option>REPORT</option><option>DASHBOARD</option></select></label>
          <label>Artifact Ref<input v-model="draft.artifactRef" required /></label>
        </div>
        <label>Bundle Ref<input v-model="draft.bundleRef" required /></label>
        <label>当前 Bundle Revision<input v-model="draft.expectedBundleRevision" placeholder="sha256:…" required /></label>
        <label>可选初始定义<textarea v-model="draft.definitionContent" placeholder="留空时读取 Bundle 中已有定义"></textarea></label>
        <div class="sheet-actions"><button type="button" class="text-button" @click="showCreate = false">取消</button><button class="ink-button">建立草稿</button></div>
      </form>
    </div>

    <div v-if="busy" class="busy-mark"><span></span>{{ busy }}</div>
  </div>
</template>
