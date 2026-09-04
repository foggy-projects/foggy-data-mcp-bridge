import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))
const generatorPath = resolve(currentDir, 'foggy-gen.mjs')

function createFrontendMeta(queryMode: string, visibleColumns = ['orderNo', 'serviceArea']) {
  return {
    metaVersion: 'v1',
    model: 'FactOrderQueryModel',
    caption: 'Order Query',
    fields: [
      {
        name: 'orderNo',
        title: 'Order No',
        type: 'TEXT',
        groupKey: 'order',
        groupTitle: 'Order Info',
        groupOrder: 1,
        category: 'attribute',
        filterType: 'text',
        filterable: true,
        sortable: true,
        measure: false,
        aggregatable: false,
        uiHints: { visible: true }
      },
      {
        name: 'serviceArea',
        title: 'Service Area',
        type: 'TEXT',
        category: 'dimension-caption',
        filterType: 'dimension',
        filterable: true,
        sortable: true,
        measure: false,
        aggregatable: false,
        memberLookup: {
          enabled: true,
          selectionFieldName: 'serviceAreaId',
          displayFieldName: 'serviceArea',
          searchable: true,
          pageable: true,
          defaultLimit: 20
        },
        uiHints: { visible: true }
      },
      {
        name: 'totalTransportFee',
        title: 'Transport Fee',
        type: 'MONEY',
        category: 'measure',
        filterType: 'number',
        filterable: true,
        sortable: true,
        measure: true,
        aggregatable: true,
        extData: {
          viewer: {
            format: 'money',
            rawUnit: 'minor',
            displayUnit: 'CNY',
            scaleFactor: 100,
            precision: 2
          },
          internalOnly: 'must-not-be-generated'
        },
        uiHints: { visible: false }
      }
    ],
    defaults: {
      tableInstanceId: 'fact-order-main',
      visibleColumns,
      requiredRuntimeColumns: ['orderId'],
      lockedColumns: ['serviceArea'],
      searchFields: ['orderNo'],
      pageSize: 20,
      queryMode
    }
  }
}

describe('foggy-gen QueryTable template', () => {
  it("passes queryMode through and emits schema queryMode for column-only filtering", () => {
    const tempRoot = mkdtempSync(join(tmpdir(), 'foggy-gen-'))
    const metaPath = join(tempRoot, 'meta.json')
    const outputDir = join(tempRoot, 'generated')

    try {
      writeFileSync(metaPath, JSON.stringify(createFrontendMeta('column'), null, 2))

      execFileSync(process.execPath, [
        generatorPath,
        '--file',
        metaPath,
        '--output',
        outputDir
      ], { cwd: resolve(currentDir, '..'), stdio: 'pipe' })

      const tableVue = readFileSync(join(outputDir, 'FactOrderTable.vue'), 'utf-8')
      const tableSchema = readFileSync(join(outputDir, 'FactOrder.table.schema.ts'), 'utf-8')
      const querySchema = readFileSync(join(outputDir, 'FactOrder.query.schema.ts'), 'utf-8')
      const apiTs = readFileSync(join(outputDir, 'FactOrder.api.ts'), 'utf-8')

      expect(tableVue).toContain("import { computed, ref, useSlots } from 'vue'")
      expect(tableVue).toContain(
        "import type { SliceRequestDef, QueryHooks, EnhancedColumnSchema, QueryMode, QuerySchema, TableDefaultQueryConfig, TableDefaultQueryConfigScope, TableDefaultQueryConfigLoadOptions, ListPresetConfig } from 'foggy-data-viewer'"
      )
      expect(tableVue).toContain("render?: EnhancedColumnSchema['customRender']")
      expect(tableVue).toContain('const slots = useSlots()')
      expect(tableVue).toContain('const tableRef = ref<DataTableWithSearchExpose>()')
      expect(tableVue).toContain("name.startsWith('column-') || name.startsWith('filter-')")
      expect(tableVue).toContain('...(ov.render && { customRender: ov.render })')
      expect(tableVue).toContain('defineExpose({')
      const exposeBlock = tableVue.match(/defineExpose\(\{[\s\S]*?\n\}\)/)?.[0] ?? ''
      expect(exposeBlock).toContain('reload: () => tableRef.value?.reload?.(),')
      expect(exposeBlock).not.toMatch(/reload:[\s\S]*refresh/)
      expect(tableVue).toContain('clearSelection: () => tableRef.value?.clearSelection?.(),')
      expect(tableVue).toContain('getSelectedCount: () => tableRef.value?.getSelectedCount?.() ?? 0')
      expect(tableVue).toContain('ref="tableRef"')
      expect(tableVue).toContain('<template v-for="(_, name) in dynamicSlots" :key="name" #[name]="scope">')
      expect(tableVue).toContain('queryMode?: QueryMode')
      expect(tableVue).toContain('querySchemaOverride?: QuerySchema')
      expect(tableVue).toContain('showQueryPanel?: boolean')
      expect(tableVue).toContain('tableInstanceId?: string')
      expect(tableVue).toContain('defaultQueryConfig?: TableDefaultQueryConfig | null')
      expect(tableVue).toContain('defaultQueryConfigScope?: TableDefaultQueryConfigLoadOptions')
      expect(tableVue).toContain('defaultQueryConfigLoader?: (scope: TableDefaultQueryConfigScope) => Promise<TableDefaultQueryConfig | null>')
      expect(tableVue).toContain('listPreset?: boolean | ListPresetConfig')
      expect(tableVue).toContain(':query-mode="queryMode"')
      expect(tableVue).toContain(':query-schema="props.querySchemaOverride ?? querySchema"')
      expect(tableVue).toContain(':show-query-panel="showQueryPanel"')
      expect(tableVue).toContain(':table-instance-id="tableInstanceId ?? tableSchema.tableInstanceId"')
      expect(tableVue).toContain(':default-query-config="defaultQueryConfig"')
      expect(tableVue).toContain(':default-query-config-scope="defaultQueryConfigScope"')
      expect(tableVue).toContain(':default-query-config-loader="defaultQueryConfigLoader"')
      expect(tableVue).toContain(':list-preset="listPreset"')
      expect(tableSchema).toContain("qmModel: 'FactOrderQueryModel'")
      expect(tableSchema).toContain('export const defaultTableInstanceId = "fact-order-main"')
      expect(tableSchema).toContain('tableInstanceId: defaultTableInstanceId')
      expect(tableSchema).toContain('export const defaultRequiredRuntimeColumns = [\n  "orderId"\n]')
      expect(tableSchema).toContain('export const defaultLockedColumns = [\n  "serviceArea"\n]')
      expect(tableSchema).toContain('requiredRuntimeColumns: defaultRequiredRuntimeColumns')
      expect(tableSchema).toContain('lockedColumns: defaultLockedColumns')
      expect(tableSchema).toContain('columns: allColumns.filter(c => defaultVisibleColumns.includes(c.name) || defaultLockedColumns.includes(c.name))')
      expect(tableSchema).toContain('groupKey: "order"')
      expect(tableSchema).toContain('groupTitle: "Order Info"')
      expect(tableSchema).toContain('groupOrder: 1')
      expect(tableSchema).toContain("queryMode: 'column'")
      expect(tableSchema).toContain("import { calculateColumnWidth, MONEY_VIEWER } from 'foggy-data-viewer'")
      expect(tableSchema).toContain('extData: { viewer: MONEY_VIEWER }')
      expect(tableSchema).not.toContain('internalOnly')
      expect(tableSchema).toContain('showFilters: true')
      expect(tableSchema).not.toContain('showSearchToolbar')
      expect(querySchema).toContain('export const defaultFormFieldKeys = ["orderNo"]')
      expect(querySchema).toContain('fields: defaultFormFieldKeys')
      expect(tableSchema).toContain('memberLookup')
      expect(tableSchema).toContain("selectionFieldName: 'serviceAreaId'")
      expect(tableSchema).toContain("displayFieldName: 'serviceArea'")
      expect(tableSchema).toContain('defaultLimit: 20')
      expect(apiTs).toContain('const columns = (params.columns ?? [])')
      expect(apiTs).toContain(".filter(column => column && column !== '_actions')")
      expect(apiTs).toContain("throw new Error(FACT_ORDER_QM_MODEL + ' direct query requires non-empty business columns')")
      expect(apiTs).toContain('columns,')
    } finally {
      rmSync(tempRoot, { recursive: true, force: true })
    }
  })

  it('falls back to default visible business columns when visibleColumns is empty', () => {
    const tempRoot = mkdtempSync(join(tmpdir(), 'foggy-gen-'))
    const metaPath = join(tempRoot, 'meta.json')
    const outputDir = join(tempRoot, 'generated')

    try {
      writeFileSync(metaPath, JSON.stringify(createFrontendMeta('column', []), null, 2))

      execFileSync(process.execPath, [
        generatorPath,
        '--file',
        metaPath,
        '--output',
        outputDir
      ], { cwd: resolve(currentDir, '..'), stdio: 'pipe' })

      const tableSchema = readFileSync(join(outputDir, 'FactOrder.table.schema.ts'), 'utf-8')

      expect(tableSchema).not.toContain('export const defaultVisibleColumns = []')
      expect(tableSchema).toContain('export const defaultVisibleColumns = [\n  "orderNo",\n  "serviceArea"\n]')
    } finally {
      rmSync(tempRoot, { recursive: true, force: true })
    }
  })
})
