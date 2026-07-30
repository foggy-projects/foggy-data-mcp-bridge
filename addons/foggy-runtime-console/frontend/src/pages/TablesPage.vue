<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import RuntimeResultTable from '@/components/RuntimeResultTable.vue'
import { runtimeApi, RuntimeRequestError } from '@/api/client'
import { normalizeResultRows, prettyJson } from '@/utils/json'

interface DatasourceList {
  datasources?: Array<{ name: string; enabled?: boolean }>
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

const datasources = ref<string[]>([])
const tables = ref<TableInfo[]>([])
const busy = ref('')
const inspectRows = ref<Record<string, unknown>[]>([])
const inspectMeta = ref('')
const sqlRows = ref<Record<string, unknown>[]>([])
const sqlMeta = ref('')
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

async function loadDatasources(): Promise<void> {
  try {
    const result = await runtimeApi.get<DatasourceList>('datasources')
    datasources.value = (result.datasources || []).filter(item => item.enabled !== false).map(item => item.name)
    form.dataSource ||= datasources.value[0] || ''
  } catch (error) {
    ElMessage.error(errorText(error))
  }
}

async function listTables(): Promise<void> {
  busy.value = 'list'
  try {
    const result = await runtimeApi.post<TableListResponse>('tables/list', form)
    tables.value = result.tables || []
    ElMessage.success(`读取到 ${tables.value.length} 个表或视图。`)
  } catch (error) {
    ElMessage.error(errorText(error))
  } finally {
    busy.value = ''
  }
}

async function inspectTable(item: TableInfo): Promise<void> {
  busy.value = `inspect:${item.name}`
  inspectRows.value = []
  try {
    const result = await runtimeApi.post<Record<string, unknown>>('tables/inspect', {
      dataSource: form.dataSource || undefined,
      schema: item.schema || form.schema || undefined,
      table: item.name,
      includeIndexes: true,
      includeForeignKeys: true
    })
    inspectRows.value = normalizeResultRows(result.columns || [])
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
    ElMessage.error(errorText(error))
  } finally {
    busy.value = ''
  }
}

async function runSql(): Promise<void> {
  if (!sql.value.trim()) {
    ElMessage.warning('请输入只读 SELECT 或 WITH ... SELECT。')
    return
  }
  busy.value = 'sql'
  sqlRows.value = []
  try {
    const result = await runtimeApi.post<SqlResponse>('sql/query', {
      dataSource: form.dataSource || undefined,
      sql: sql.value,
      maxRows: maxRows.value,
      timeoutSeconds: 10
    })
    sqlRows.value = result.rows || []
    sqlMeta.value = prettyJson({
      rowCount: result.rowCount,
      truncated: result.truncated,
      warnings: result.warnings,
      columns: result.columns
    })
    ElMessage.success(`SQL 查询完成，返回 ${sqlRows.value.length} 行。`)
  } catch (error) {
    ElMessage.error(errorText(error))
  } finally {
    busy.value = ''
  }
}

onMounted(async () => {
  await loadDatasources()
  await listTables()
})
</script>

<template>
  <PageHeader
    eyebrow="Database inspector"
    title="表结构与只读 SQL"
    description="按数据源检查表、列、索引与外键；SQL 端点只接受单条只读 SELECT 或 WITH ... SELECT，并限制行数和超时。"
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
      <div class="console-panel-head"><span class="console-panel-title">表结构</span><span class="console-panel-kicker">COLUMNS</span></div>
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
</style>
