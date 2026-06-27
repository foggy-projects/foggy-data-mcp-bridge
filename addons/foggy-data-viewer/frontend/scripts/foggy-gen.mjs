#!/usr/bin/env node
/**
 * foggy-gen — QM 前端代码生成器
 *
 * 从 frontend-meta v1 JSON 生成 TypeScript + Vue 3 代码：
 *   types.ts / table.schema.ts / query.schema.ts / api.ts / Table.vue / index.ts
 *
 * 用法：
 *   node scripts/foggy-gen.mjs --model FactOrderQueryModel --server http://localhost:7108 --output src/generated/qm/order
 *   node scripts/foggy-gen.mjs --file meta.json --output src/generated/qm/order
 *   node scripts/foggy-gen.mjs --all --server http://localhost:7108 --output src/generated/qm
 */

import { writeFileSync, mkdirSync, readFileSync, existsSync } from 'fs'
import { resolve, join } from 'path'

// ── CLI 参数解析 ──

const args = process.argv.slice(2)
function getArg(name) {
  const idx = args.indexOf(`--${name}`)
  return idx >= 0 && idx + 1 < args.length ? args[idx + 1] : null
}
const hasFlag = (name) => args.includes(`--${name}`)

const modelName = getArg('model')
const filePath = getArg('file')
const outputDir = getArg('output') || 'src/generated/qm'
const serverUrl = getArg('server') || 'http://localhost:7108'

if (!modelName && !filePath) {
  console.error('Usage: foggy-gen --model <QmModelName> [--server <url>] --output <dir>')
  console.error('       foggy-gen --file <meta.json> --output <dir>')
  process.exit(1)
}

// ── 加载元数据 ──

async function loadMeta() {
  if (filePath) {
    const raw = readFileSync(resolve(filePath), 'utf-8')
    const parsed = JSON.parse(raw)
    // 支持 RX 包装 { code: 200, data: {...} } 或直接 FrontendMeta
    return parsed.data || parsed
  }

  const url = `${serverUrl}/data-viewer/api/frontend-meta/${encodeURIComponent(modelName)}`
  console.log(`Fetching: ${url}`)
  const res = await fetch(url)
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`)
  const json = await res.json()
  if (json.code !== 200) throw new Error(`API error: ${json.msg}`)
  return json.data
}

// ── 类型映射 ──

const TS_TYPE_MAP = {
  TEXT: 'string', STRING: 'string',
  INTEGER: 'number', BIGINT: 'number', NUMBER: 'number', MONEY: 'number',
  DAY: 'string', DATETIME: 'string', DATE: 'string',
  BOOL: 'boolean', BOOLEAN: 'boolean',
}

function toTsType(fieldType) {
  return TS_TYPE_MAP[fieldType?.toUpperCase()] || 'unknown'
}

function toCamelCase(name) {
  // FactOrderQueryModel -> FactOrder
  return name.replace(/QueryModel$/, '')
}

// ── 生成 types.ts ──

function genTypes(meta) {
  const prefix = toCamelCase(meta.model)
  const lines = [
    `/**`,
    ` * ${meta.model} - 自动生成的类型定义`,
    ` * ⚠️ 此文件由 foggy-gen 自动生成，请勿手动修改`,
    ` */`,
    ``,
    `/** ${meta.caption} - 行数据类型 */`,
    `export interface ${prefix}Row {`,
  ]

  for (const f of meta.fields) {
    const tsType = toTsType(f.type)
    const key = f.name.includes('$') ? `'${f.name}'` : f.name
    lines.push(`  ${key}?: ${tsType}`)
  }

  lines.push(`}`, ``)
  lines.push(`/** 模型名称常量 */`)
  lines.push(`export const ${prefix.replace(/([a-z])([A-Z])/g, '$1_$2').toUpperCase()}_QM_MODEL = '${meta.model}' as const`)

  return lines.join('\n') + '\n'
}

// ── 生成 table.schema.ts ──

function genTableSchema(meta) {
  const prefix = toCamelCase(meta.model)
  const visibleCols = meta.defaults?.visibleColumns || meta.fields.filter(f => f.uiHints?.visible !== false).map(f => f.name)
  const searchFields = meta.defaults?.searchFields || []
  const pageSize = meta.defaults?.pageSize || 50
  const queryMode = ['panel', 'column', 'combined', 'none'].includes(meta.defaults?.queryMode)
    ? meta.defaults.queryMode
    : null

  const lines = [
    `/**`,
    ` * ${meta.model} - 自动生成的表格 Schema`,
    ` * ⚠️ 此文件由 foggy-gen 自动生成，请勿手动修改`,
    ` */`,
    `import type { EnhancedColumnSchema, TableSchema } from 'foggy-data-viewer'`,
    `import { calculateColumnWidth } from 'foggy-data-viewer'`,
    ``,
    `/** 全量列定义 */`,
    `export const allColumns: EnhancedColumnSchema[] = [`,
  ]

  for (const f of meta.fields) {
    const props = [`    name: '${f.name}'`, `    title: '${f.title}'`, `    type: '${f.type}'`]
    if (f.description) props.push(`    description: ${JSON.stringify(f.description)}`)
    if (f.filterType) props.push(`    filterType: '${f.filterType}'`)
    if (f.filterable != null) props.push(`    filterable: ${f.filterable}`)
    if (f.measure) props.push(`    measure: true`)
    if (f.aggregatable) props.push(`    aggregatable: true`)
    if (f.dictId) props.push(`    dictId: '${f.dictId}'`)
    if (f.dictItems?.length) {
      const items = f.dictItems.map(i => `{ value: ${JSON.stringify(i.value)}, label: ${JSON.stringify(i.label)} }`).join(', ')
      props.push(`    dictItems: [${items}]`)
    }
    if (f.memberLookup?.enabled) {
      props.push(`    memberLookup: { enabled: true, selectionFieldName: '${f.memberLookup.selectionFieldName}', displayFieldName: '${f.memberLookup.displayFieldName}', searchable: true, pageable: true, defaultLimit: ${f.memberLookup.defaultLimit || 20} }`)
    }
    props.push(`    width: calculateColumnWidth('${f.title}', '${f.type}')`)
    lines.push(`  {\n${props.join(',\n')},\n  },`)
  }

  lines.push(`]`, ``)
  lines.push(`/** 默认可见列 */`)
  lines.push(`export const defaultVisibleColumns = ${JSON.stringify(visibleCols, null, 2)}`)
  lines.push(``)
  lines.push(`/** 默认搜索字段 */`)
  lines.push(`export const defaultSearchFields = ${JSON.stringify(searchFields)}`)
  lines.push(``)
  lines.push(`/** 表格 Schema */`)
  lines.push(`export const tableSchema: TableSchema = {`)
  lines.push(`  columns: allColumns.filter(c => defaultVisibleColumns.includes(c.name)),`)
  lines.push(`  searchableFields: defaultSearchFields.length > 0 ? defaultSearchFields : undefined,`)
  lines.push(`  pageSize: ${pageSize},`)
  if (queryMode) lines.push(`  queryMode: '${queryMode}',`)
  lines.push(`  showFilters: true,`)
  lines.push(`  showPager: true,`)
  lines.push(`}`)

  return lines.join('\n') + '\n'
}

// ── 生成 query.schema.ts ──

function genQuerySchema(meta) {
  const queryFields = meta.fields.filter(f =>
    f.filterable &&
    f.category !== 'dimension-id' &&
    !f.calculated &&
    !(f.measure && !f.dictId)
  )
  const explicitFormFields = Array.isArray(meta.defaults?.queryPanelFields)
    ? meta.defaults.queryPanelFields
    : Array.isArray(meta.defaults?.formFields)
      ? meta.defaults.formFields
      : Array.isArray(meta.defaults?.queryFields)
        ? meta.defaults.queryFields
        : []
  const defaultSearchFields = Array.isArray(meta.defaults?.searchFields) ? meta.defaults.searchFields : []
  const defaultFormFieldKeys = explicitFormFields.length > 0
    ? explicitFormFields
    : defaultSearchFields.slice(0, 1)
  const queryFieldNames = new Set(queryFields.map(f => f.name))
  const resolvedDefaultFormFieldKeys = defaultFormFieldKeys.filter(key => queryFieldNames.has(key))

  const lines = [
    `/**`,
    ` * ${meta.model} - 自动生成的查询 Schema`,
    ` * ⚠️ 此文件由 foggy-gen 自动生成，请勿手动修改`,
    ` */`,
    ``,
    `/** 查询字段定义 */`,
    `export const queryFields = [`,
  ]

  let order = 0
  for (const f of queryFields) {
    order++
    const isMember = f.filterType === 'dimension' && f.memberLookup?.enabled
    const isDict = !!f.dictId
    const isDate = f.filterType === 'date' || f.filterType === 'datetime'
    const isNumber = f.filterType === 'number'

    let component = 'text'
    let defaultOp = 'like'
    let placement = 'column'
    let span = 1

    if (isMember) { component = 'memberSelect'; defaultOp = 'in'; placement = 'form'; }
    else if (isDict) { component = 'dictSelect'; defaultOp = 'in'; }
    else if (isDate) { component = 'dateRange'; defaultOp = '[]'; placement = 'form'; span = 2; }
    else if (isNumber) { component = 'numberRange'; defaultOp = '[]'; placement = 'form'; span = 2; }

    const sourceField = isMember ? f.memberLookup.selectionFieldName : f.name

    const props = [
      `    key: '${f.name}'`,
      `    label: '${f.title}'`,
      `    sourceField: '${sourceField}'`,
      `    placement: '${placement}' as const`,
      `    component: '${component}' as const`,
      `    defaultOperator: '${defaultOp}'`,
    ]
    if (isDict && f.dictId) props.push(`    dictId: '${f.dictId}'`)
    if (isMember) props.push(`    lookupRef: '${f.name}'`)
    props.push(`    order: ${order}`)
    props.push(`    span: ${span}`)

    lines.push(`  {\n${props.join(',\n')},\n  },`)
  }

  lines.push(`]`, ``)
  lines.push(`/** 默认顶部查询字段；未显式配置时只保留一个主查询键，其余字段走列头筛选 */`)
  lines.push(`export const defaultFormFieldKeys = ${JSON.stringify(resolvedDefaultFormFieldKeys)}`)
  lines.push(``)
  lines.push(`/** 查询 Schema */`)
  lines.push(`export const querySchema = {`)
  lines.push(`  fields: defaultFormFieldKeys`)
  lines.push(`    .map(key => queryFields.find(field => field.key === key))`)
  lines.push(`    .filter((field): field is (typeof queryFields)[number] => Boolean(field))`)
  lines.push(`    .map((field, index) => ({ ...field, placement: 'form' as const, order: index + 1, span: field.span ?? 1 })),`)
  lines.push(`  submitMode: 'manual' as const,`)
  lines.push(`  collapsible: true,`)
  lines.push(`  defaultExpanded: true,`)
  lines.push(`  layout: {`)
  lines.push(`    mode: 'grid' as const,`)
  lines.push(`    columns: { xs: 1, sm: 2, md: 3, lg: 4, xl: 4 },`)
  lines.push(`    labelWidth: 100,`)
  lines.push(`    actionAlign: 'right' as const,`)
  lines.push(`    collapsedRows: 1,`)
  lines.push(`    gutter: 16,`)
  lines.push(`  },`)
  lines.push(`}`)

  return lines.join('\n') + '\n'
}

// ── 生成 api.ts ──

function genApi(meta) {
  const prefix = toCamelCase(meta.model)
  const constName = `${prefix.replace(/([a-z])([A-Z])/g, '$1_$2').toUpperCase()}_QM_MODEL`

  return `/**
 * ${meta.model} - 自动生成的 API 封装
 * ⚠️ 此文件由 foggy-gen 自动生成，请勿手动修改
 */
import { fetchQueryDataDirect, fetchMemberOptions, fetchFrontendMeta } from 'foggy-data-viewer'
import type { FetchDataParams, FetchDataResult, MemberQueryRequest, MemberQueryResponse, FrontendMeta } from 'foggy-data-viewer'
import type { ${prefix}Row } from './${prefix}.types'
import { ${constName} } from './${prefix}.types'

export async function query${prefix}(params: FetchDataParams): Promise<FetchDataResult<${prefix}Row>> {
  const columns = params.columns
    .map(column => column.trim())
    .filter(column => column && column !== '_actions')

  if (columns.length === 0) {
    throw new Error('query${prefix} requires params.columns for direct query')
  }

  return fetchQueryDataDirect(${constName}, {
    start: (params.page - 1) * params.pageSize,
    limit: params.pageSize,
    columns,
    slice: params.slice,
    orderBy: params.orderBy,
  })
}

export async function query${prefix}Members(request: Omit<MemberQueryRequest, 'qmModel'>): Promise<MemberQueryResponse> {
  return fetchMemberOptions({ ...request, qmModel: ${constName} })
}

export async function get${prefix}Meta(): Promise<FrontendMeta> {
  return fetchFrontendMeta(${constName})
}
`
}

// ── 生成 Table.vue ──

function genVue(meta) {
  const prefix = toCamelCase(meta.model)
  const constName = `${prefix.replace(/([a-z])([A-Z])/g, '$1_$2').toUpperCase()}_QM_MODEL`

  return `<!--
  ${meta.model} - 自动生成的表格组件
  ⚠️ 此文件由 foggy-gen 自动生成，请勿手动修改
-->
<script setup lang="ts">
import { computed, ref, useSlots } from 'vue'
import { DataTableWithSearch, fetchMemberOptions } from 'foggy-data-viewer'
import type { SliceRequestDef, QueryHooks, EnhancedColumnSchema, QueryMode, QuerySchema } from 'foggy-data-viewer'
import { tableSchema } from './${prefix}.table.schema'
import { querySchema } from './${prefix}.query.schema'
import { query${prefix} } from './${prefix}.api'
import { ${constName} } from './${prefix}.types'

interface BusinessColumnOverride {
  title?: string; width?: number | string; hidden?: boolean
  order?: number; formatter?: (value: unknown) => string
  render?: EnhancedColumnSchema['customRender']
  uiConfig?: Record<string, unknown>
}

interface DataTableWithSearchExpose {
  refresh?: () => void | Promise<void>
  reload?: () => void | Promise<void>
  clearSelection?: () => void
  getSelectedRows?: () => Record<string, unknown>[]
  getSelectedCount?: () => number
}

const slots = useSlots()
const tableRef = ref<DataTableWithSearchExpose>()
const dynamicSlots = computed(() => {
  const result: Record<string, unknown> = {}
  for (const name of Object.keys(slots)) {
    if (name.startsWith('column-') || name.startsWith('filter-')) {
      result[name] = slots[name]
    }
  }
  return result
})

const props = withDefaults(defineProps<{
  globalParams?: Record<string, unknown>
  customParams?: Record<string, unknown>
  initialSlices?: SliceRequestDef[]
  columnOverrides?: Record<string, BusinessColumnOverride>
  queryHooks?: QueryHooks
  queryMode?: QueryMode
  querySchemaOverride?: QuerySchema
  showQueryPanel?: boolean
}>(), { showQueryPanel: false })

const mergedSchema = computed(() => {
  let columns: EnhancedColumnSchema[] = [...tableSchema.columns]
  if (props.columnOverrides) {
    columns = columns
      .map(col => {
        const ov = props.columnOverrides?.[col.name]
        if (!ov) return col
        if (ov.hidden) return null
        return { ...col, ...(ov.title && { title: ov.title }), ...(ov.width && { width: ov.width }),
          ...(ov.formatter && { customFormatter: ov.formatter }),
          ...(ov.render && { customRender: ov.render }),
          ...(ov.uiConfig && { uiConfig: { ...col.uiConfig, ...ov.uiConfig } }) }
      })
      .filter(Boolean) as EnhancedColumnSchema[]
  }
  return { ...tableSchema, columns }
})

defineExpose({
  refresh: () => tableRef.value?.refresh?.(),
  reload: () => tableRef.value?.reload?.(),
  clearSelection: () => tableRef.value?.clearSelection?.(),
  getSelectedRows: () => tableRef.value?.getSelectedRows?.() ?? [],
  getSelectedCount: () => tableRef.value?.getSelectedCount?.() ?? 0
})
</script>

<template>
  <DataTableWithSearch
    ref="tableRef"
    :schema="mergedSchema"
    :fetch-data="query${prefix}"
    :query-schema="props.querySchemaOverride ?? querySchema"
    :query-mode="queryMode"
    :show-query-panel="showQueryPanel"
    :qm-model="${constName}"
    :filter-member-loader="fetchMemberOptions"
    :query-hooks="queryHooks"
    :initial-slice="initialSlices"
  >
    <template v-if="$slots.toolbar" #toolbar>
      <slot name="toolbar" />
    </template>
    <template v-if="$slots['row-actions']" #row-actions="scope">
      <slot name="row-actions" v-bind="scope" />
    </template>
    <template v-for="(_, name) in dynamicSlots" :key="name" #[name]="scope">
      <slot :name="name" v-bind="scope || {}" />
    </template>
  </DataTableWithSearch>
</template>
`
}

// ── 生成 index.ts ──

function genIndex(meta) {
  const prefix = toCamelCase(meta.model)
  const constName = `${prefix.replace(/([a-z])([A-Z])/g, '$1_$2').toUpperCase()}_QM_MODEL`

  return `/**
 * ${meta.model} - 生成产物统一导出
 * ⚠️ 此文件由 foggy-gen 自动生成，请勿手动修改
 */
export type { ${prefix}Row } from './${prefix}.types'
export { ${constName} } from './${prefix}.types'
export { allColumns, defaultVisibleColumns, defaultSearchFields, tableSchema } from './${prefix}.table.schema'
export { queryFields, querySchema } from './${prefix}.query.schema'
export { query${prefix}, query${prefix}Members, get${prefix}Meta } from './${prefix}.api'
export { default as ${prefix}Table } from './${prefix}Table.vue'
`
}

// ── 主流程 ──

async function main() {
  const meta = await loadMeta()
  console.log(`Model: ${meta.model} (${meta.caption})`)
  console.log(`Fields: ${meta.fields.length}`)

  const prefix = toCamelCase(meta.model)
  const dir = resolve(outputDir)
  mkdirSync(dir, { recursive: true })

  const files = [
    [`${prefix}.types.ts`, genTypes(meta)],
    [`${prefix}.table.schema.ts`, genTableSchema(meta)],
    [`${prefix}.query.schema.ts`, genQuerySchema(meta)],
    [`${prefix}.api.ts`, genApi(meta)],
    [`${prefix}Table.vue`, genVue(meta)],
    [`index.ts`, genIndex(meta)],
  ]

  for (const [name, content] of files) {
    const filePath = join(dir, name)
    writeFileSync(filePath, content, 'utf-8')
    console.log(`  ✓ ${name}`)
  }

  console.log(`\nGenerated ${files.length} files in ${dir}`)
}

main().catch(e => { console.error('ERROR:', e.message); process.exit(1) })
