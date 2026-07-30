<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElDrawer, ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import RuntimeResultTable from '@/components/RuntimeResultTable.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { normalizeResultRows, prettyJson } from '@/utils/json'
import { useContextRail } from '@/stores/contextRail'
import { useNamespaceScope, type NamespaceScopeSnapshot } from '@/composables/useNamespaceScope'
import {
  buildSelectStatement,
  buildTableModelDraft,
  type InspectedTable
} from '@/features/tables/tableModelDraft'

interface DatasourceList {
  datasources?: Array<{ name: string; enabled?: boolean }>
}

interface NamespaceDatasource {
  dataSource?: string
}

interface TableInfo {
  schema?: string
  name: string
  type?: string
  remarks?: string
}

interface TableListResponse {
  dataSource?: string
  schema?: string
  tables?: TableInfo[]
  warnings?: string[]
}

interface SqlResponse {
  rows?: Record<string, unknown>[]
  rowCount?: number
  truncated?: boolean
  warnings?: string[]
  columns?: unknown[]
}

interface TableInspectResponse {
  dataSource?: string
  schema?: string
  table?: string
  tableType?: string
  columns?: InspectedTable['columns']
  primaryKey?: { columns?: string[] } | string[]
  indexes?: unknown[]
  foreignKeys?: unknown[]
}

const datasources = ref<string[]>([])
const contextRail = useContextRail()
const namespaceScope = useNamespaceScope()
const tables = ref<TableInfo[]>([])
const activeTable = ref('')
const busy = ref('')
const inspectRows = ref<Record<string, unknown>[]>([])
const inspectMeta = ref('')
const inspectDetail = ref<InspectedTable | null>(null)
const sqlRows = ref<Record<string, unknown>[]>([])
const sqlMeta = ref('')
const draftOpen = ref(false)
const tmDraft = ref<ReturnType<typeof buildTableModelDraft> | null>(null)
const form = reactive({
  dataSource: '',
  schema: '',
  pattern: '%',
  includeViews: true
})
const sql = ref('SELECT 1 AS runtime_ok')
const maxRows = ref(100)

function errorText(error: unknown): string {
  return error instanceof RuntimeRequestError ? error.message : '表操作失败。'
}

function syncContextRail(): void {
  contextRail.setContext({
    route: 'tables',
    eyebrow: 'Database inspector',
    title: 'Schema Browser',
    description: '切换数据源，或直接检查表结构。',
    loading: busy.value === 'list',
    filterable: true,
    emptyText: '当前筛选条件下没有表。',
    sections: [
      {
        id: 'datasources',
        label: 'Datasources',
        items: datasources.value.map(item => ({
          id: `datasource:${item}`,
          label: item,
          meta: item === form.dataSource ? 'current connection' : 'switch connection',
          badge: 'source',
          active: item === form.dataSource,
          action: () => {
            form.dataSource = item
            void listTables()
          }
        }))
      },
      {
        id: 'tables',
        label: `${tables.value.length} tables / views`,
        items: tables.value.map(item => ({
          id: `${item.schema || ''}.${item.name}`,
          label: item.name,
          meta: item.schema || 'default schema',
          badge: item.type || 'table',
          active: activeTable.value === item.name,
          action: () => void inspectTable(item)
        }))
      }
    ]
  })
}

function resetNamespaceState(): void {
  datasources.value = []
  tables.value = []
  activeTable.value = ''
  inspectRows.value = []
  inspectMeta.value = ''
  inspectDetail.value = null
  sqlRows.value = []
  sqlMeta.value = ''
  draftOpen.value = false
  tmDraft.value = null
  form.dataSource = ''
  busy.value = ''
  syncContextRail()
}

async function loadWorkspace(): Promise<void> {
  const requestScope = namespaceScope.snapshot()
  busy.value = 'list'
  syncContextRail()
  try {
    const [datasourceResult, bindingResult] = await Promise.all([
      runtimeApi.get<DatasourceList>('datasources'),
      requestScope.namespace
        ? runtimeApi.get<NamespaceDatasource>(
          `namespaces/${encodeURIComponent(requestScope.namespace)}/datasource`
        )
        : Promise.resolve<NamespaceDatasource>({})
    ])
    if (!namespaceScope.isCurrent(requestScope)) return
    datasources.value = (datasourceResult.datasources || [])
      .filter(item => item.enabled !== false)
      .map(item => item.name)
    form.dataSource = bindingResult.dataSource || ''
    await listTables(requestScope)
  } catch (error) {
    if (!namespaceScope.isCurrent(requestScope)) return
    ElMessage.error(errorText(error))
  } finally {
    if (namespaceScope.isCurrent(requestScope) && busy.value === 'list') {
      busy.value = ''
      syncContextRail()
    }
  }
}

async function listTables(existingScope?: NamespaceScopeSnapshot): Promise<void> {
  const requestScope = existingScope || namespaceScope.snapshot()
  busy.value = 'list'
  tables.value = []
  activeTable.value = ''
  inspectRows.value = []
  inspectMeta.value = ''
  inspectDetail.value = null
  draftOpen.value = false
  tmDraft.value = null
  syncContextRail()
  try {
    const result = await runtimeApi.post<TableListResponse>('tables/list', { ...form })
    if (!namespaceScope.isCurrent(requestScope)) return
    tables.value = result.tables || []
    ElMessage.success(`读取到 ${tables.value.length} 个表或视图。`)
  } catch (error) {
    if (!namespaceScope.isCurrent(requestScope)) return
    ElMessage.error(errorText(error))
  } finally {
    if (namespaceScope.isCurrent(requestScope)) {
      busy.value = ''
      syncContextRail()
    }
  }
}

async function inspectTable(item: TableInfo): Promise<void> {
  const requestScope = namespaceScope.snapshot()
  busy.value = `inspect:${item.name}`
  activeTable.value = item.name
  inspectRows.value = []
  inspectMeta.value = ''
  inspectDetail.value = null
  draftOpen.value = false
  tmDraft.value = null
  syncContextRail()
  try {
    const result = await runtimeApi.post<TableInspectResponse>('tables/inspect', {
      dataSource: form.dataSource || undefined,
      schema: item.schema || form.schema || undefined,
      table: item.name,
      includeIndexes: true,
      includeForeignKeys: true
    })
    if (!namespaceScope.isCurrent(requestScope)) return
    inspectRows.value = normalizeResultRows(result.columns || [])
    const primaryKeyColumns = Array.isArray(result.primaryKey)
      ? result.primaryKey
      : result.primaryKey?.columns || []
    inspectDetail.value = {
      dataSource: result.dataSource || form.dataSource,
      schema: result.schema || item.schema || form.schema,
      table: result.table || item.name,
      columns: result.columns || [],
      primaryKeyColumns
    }
    inspectMeta.value = prettyJson({
      dataSource: result.dataSource,
      schema: result.schema,
      table: result.table,
      tableType: result.tableType,
      primaryKey: result.primaryKey,
      indexes: result.indexes,
      foreignKeys: result.foreignKeys
    })
  } catch (error) {
    if (!namespaceScope.isCurrent(requestScope)) return
    ElMessage.error(errorText(error))
  } finally {
    if (namespaceScope.isCurrent(requestScope)) {
      busy.value = ''
      syncContextRail()
    }
  }
}

function generateSelect(): void {
  if (!inspectDetail.value) return
  try {
    sql.value = buildSelectStatement(inspectDetail.value.schema, inspectDetail.value.table)
    ElMessage.success('已生成只读 SELECT，请确认后运行。')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '无法安全生成 SQL。')
  }
}

function generateTmDraft(): void {
  if (!inspectDetail.value) return
  tmDraft.value = buildTableModelDraft(inspectDetail.value)
  draftOpen.value = true
}

function downloadTmDraft(): void {
  if (!tmDraft.value) return
  const href = URL.createObjectURL(new Blob([tmDraft.value.content], {
    type: 'text/plain;charset=utf-8'
  }))
  const anchor = document.createElement('a')
  anchor.href = href
  anchor.download = tmDraft.value.filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(href), 0)
}

async function runSql(): Promise<void> {
  if (!sql.value.trim()) {
    ElMessage.warning('请输入只读 SELECT 或 WITH ... SELECT。')
    return
  }
  const requestScope = namespaceScope.snapshot()
  busy.value = 'sql'
  sqlRows.value = []
  try {
    const result = await runtimeApi.post<SqlResponse>('sql/query', {
      dataSource: form.dataSource || undefined,
      sql: sql.value,
      maxRows: maxRows.value,
      timeoutSeconds: 10
    })
    if (!namespaceScope.isCurrent(requestScope)) return
    sqlRows.value = result.rows || []
    sqlMeta.value = prettyJson({
      rowCount: result.rowCount,
      truncated: result.truncated,
      warnings: result.warnings,
      columns: result.columns
    })
    ElMessage.success(`SQL 查询完成，返回 ${sqlRows.value.length} 行。`)
  } catch (error) {
    if (!namespaceScope.isCurrent(requestScope)) return
    ElMessage.error(errorText(error))
  } finally {
    if (namespaceScope.isCurrent(requestScope)) busy.value = ''
  }
}

contextRail.setContext({
  route: 'tables',
  eyebrow: 'Database inspector',
  title: 'Schema Browser',
  description: '切换数据源，或直接检查表结构。',
  loading: true,
  filterable: true,
  emptyText: '当前筛选条件下没有表。',
  sections: []
})
watch(namespaceScope.namespace, () => {
  resetNamespaceState()
  void loadWorkspace()
})
onMounted(() => {
  resetNamespaceState()
  void loadWorkspace()
})
onBeforeUnmount(() => contextRail.clearContext('tables'))
</script>

<template>
  <PageHeader
    eyebrow="Database inspector"
    title="表结构与只读 SQL"
    :description="`按数据源检查表、列、索引与外键；当前空间：${namespaceScope.label.value}。SQL 端点只接受单条只读 SELECT 或 WITH ... SELECT，并限制行数和超时。`"
  >
    <template #actions>
      <button class="console-button primary" type="button" :disabled="Boolean(busy)" @click="listTables">读取表清单</button>
    </template>
  </PageHeader>

  <div class="toolbar">
    <label class="console-field">
      <span class="console-label">数据源</span>
      <select v-model="form.dataSource" class="console-select">
        <option value="">namespace 默认绑定</option>
        <option v-for="item in datasources" :key="item" :value="item">{{ item }}</option>
      </select>
    </label>
    <label class="console-field">
      <span class="console-label">Schema</span>
      <input v-model="form.schema" class="console-input" placeholder="可选" autocomplete="off">
    </label>
    <label class="console-field">
      <span class="console-label">表名 Pattern</span>
      <input v-model="form.pattern" class="console-input" placeholder="%" autocomplete="off">
    </label>
    <label class="inline-check"><input v-model="form.includeViews" type="checkbox"> 包含视图</label>
  </div>

  <div class="split-grid">
    <section class="console-panel">
      <div class="console-panel-head">
        <span class="console-panel-title">表清单</span>
        <span class="console-panel-kicker">{{ tables.length }} ITEMS</span>
      </div>
      <div class="resource-table-wrap">
        <table class="resource-table table-list">
          <thead><tr><th>名称</th><th>Schema</th><th>类型</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in tables" :key="`${item.schema || ''}.${item.name}`">
              <td><div class="cell-title">{{ item.name }}</div><div class="cell-subtitle">{{ item.remarks || '—' }}</div></td>
              <td>{{ item.schema || 'default' }}</td>
              <td>{{ item.type || 'TABLE' }}</td>
              <td><button class="console-button compact" type="button" :disabled="Boolean(busy)" @click="inspectTable(item)">检查</button></td>
            </tr>
          </tbody>
        </table>
        <div v-if="!tables.length" class="empty-state"><div><strong>暂无表</strong>调整数据源、Schema 或 Pattern 后重试。</div></div>
      </div>
    </section>
    <section class="console-panel">
      <div class="console-panel-head inspector-head">
        <div>
          <span class="console-panel-title">表结构</span>
          <span class="console-panel-kicker">COLUMNS</span>
        </div>
        <div class="inspector-actions">
          <button class="console-button compact" type="button" :disabled="!inspectDetail || Boolean(busy)" @click="generateSelect">
            生成 SELECT
          </button>
          <button class="console-button compact primary" type="button" :disabled="!inspectDetail || Boolean(busy)" @click="generateTmDraft">
            生成 TM 草稿
          </button>
        </div>
      </div>
      <RuntimeResultTable :rows="inspectRows" :loading="busy.startsWith('inspect:')" />
      <details class="diagnostics-details">
        <summary>主键、索引与外键</summary>
        <pre class="raw-output">{{ inspectMeta || '选择一个表进行检查。' }}</pre>
      </details>
    </section>
  </div>

  <section class="console-panel sql-panel">
    <div class="console-panel-head">
      <span class="console-panel-title">只读 SQL</span>
      <span class="console-panel-kicker">MAX 500 ROWS / 30 SECONDS</span>
    </div>
    <div class="workbench-grid">
      <div class="workbench-editor">
        <div class="workbench-toolbar">
          <label class="max-rows">MAX ROWS <input v-model.number="maxRows" class="console-input" type="number" min="1" max="500"></label>
          <button class="console-button compact primary" type="button" :disabled="Boolean(busy)" @click="runSql">
            {{ busy === 'sql' ? '执行中…' : '运行 SQL' }}
          </button>
        </div>
        <label><span class="visually-hidden">只读 SQL</span><textarea v-model="sql" class="console-textarea sql-editor" spellcheck="false" /></label>
        <div class="workbench-hint">DDL、DML、多语句和非结果集 SQL 会由服务端拒绝。</div>
      </div>
      <div class="workbench-result">
        <div class="console-panel-head"><span class="console-panel-title">SQL 结果</span><span class="console-panel-kicker">{{ sqlRows.length }} ROWS</span></div>
        <RuntimeResultTable :rows="sqlRows" :loading="busy === 'sql'" />
        <details class="diagnostics-details"><summary>结果元数据</summary><pre class="raw-output">{{ sqlMeta || '暂无结果。' }}</pre></details>
      </div>
    </div>
  </section>

  <ElDrawer
    v-model="draftOpen"
    title="TM 机械草稿"
    size="min(720px, 94vw)"
    class="tm-draft-drawer"
  >
    <div v-if="tmDraft" class="draft-shell">
      <div class="draft-ident">
        <div>
          <span>MODEL NAME</span>
          <strong>{{ tmDraft.modelName }}</strong>
        </div>
        <div>
          <span>LOCAL FILE</span>
          <strong>{{ tmDraft.filename }}</strong>
        </div>
      </div>
      <div class="notice draft-warning" role="note">
        这是基于表 metadata 的机械起点，尚未校验、保存、注册或刷新。请人工补充业务标题、维度、度量、关系与描述。
      </div>
      <label class="draft-code">
        <span class="console-label">可选择复制的 TM 草稿</span>
        <textarea
          class="console-textarea draft-editor"
          aria-label="TM 草稿内容"
          :value="tmDraft.content"
          readonly
          spellcheck="false"
        />
      </label>
    </div>
    <template #footer>
      <div class="draft-footer">
        <span>仅下载到浏览器，不写入 Runtime。</span>
        <button class="console-button primary" type="button" :disabled="!tmDraft" @click="downloadTmDraft">
          下载 .tm
        </button>
      </div>
    </template>
  </ElDrawer>
</template>

<style scoped>
.table-list {
  min-width: 560px;
}

.sql-panel {
  margin-top: 14px;
}

.sql-editor {
  min-height: 260px !important;
}

.max-rows {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--console-dim);
  font: 10px/1 var(--console-mono);
}

.max-rows .console-input {
  width: 86px;
  min-height: 36px;
}

.inspector-head,
.inspector-head > div,
.inspector-actions,
.draft-footer {
  display: flex;
  align-items: center;
}

.inspector-head {
  justify-content: space-between;
  gap: 12px;
}

.inspector-head > div:first-child {
  gap: 8px;
}

.inspector-actions {
  gap: 8px;
}

.draft-shell {
  display: grid;
  gap: 18px;
}

.draft-ident {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border: 1px solid var(--console-border);
}

.draft-ident > div {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.draft-ident > div + div {
  border-left: 1px solid var(--console-border);
}

.draft-ident span,
.draft-footer span {
  color: var(--console-dim);
  font: 10px/1.2 var(--console-mono);
  letter-spacing: .08em;
}

.draft-ident strong {
  overflow-wrap: anywhere;
  font: 600 15px/1.3 var(--console-sans);
}

.draft-code {
  display: grid;
  gap: 8px;
}

.draft-editor {
  min-height: 480px !important;
  resize: vertical;
}

.draft-footer {
  justify-content: space-between;
  gap: 16px;
  width: 100%;
}

@media (max-width: 760px) {
  .inspector-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .inspector-actions {
    width: 100%;
  }

  .inspector-actions .console-button {
    flex: 1;
  }

  .draft-ident {
    grid-template-columns: 1fr;
  }

  .draft-ident > div + div {
    border-top: 1px solid var(--console-border);
    border-left: 0;
  }

  .draft-editor {
    min-height: 420px !important;
  }
}
</style>
