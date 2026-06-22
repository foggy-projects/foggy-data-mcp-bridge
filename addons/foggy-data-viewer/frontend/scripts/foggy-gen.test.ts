import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))
const generatorPath = resolve(currentDir, 'foggy-gen.mjs')

function createFrontendMeta(queryMode: string) {
  return {
    metaVersion: 'v1',
    model: 'FactOrderQueryModel',
    caption: 'Order Query',
    fields: [
      {
        name: 'orderNo',
        title: 'Order No',
        type: 'TEXT',
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
      }
    ],
    defaults: {
      visibleColumns: ['orderNo', 'serviceArea'],
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

      expect(tableVue).toContain("import { computed, ref, useSlots } from 'vue'")
      expect(tableVue).toContain(
        "import type { SliceRequestDef, QueryHooks, EnhancedColumnSchema, QueryMode, QuerySchema } from 'foggy-data-viewer'"
      )
      expect(tableVue).toContain("render?: EnhancedColumnSchema['customRender']")
      expect(tableVue).toContain('const slots = useSlots()')
      expect(tableVue).toContain('const tableRef = ref<DataTableWithSearchExpose>()')
      expect(tableVue).toContain("name.startsWith('column-') || name.startsWith('filter-')")
      expect(tableVue).toContain('...(ov.render && { customRender: ov.render })')
      expect(tableVue).toContain('defineExpose({')
      expect(tableVue).toContain('clearSelection: () => tableRef.value?.clearSelection?.(),')
      expect(tableVue).toContain('getSelectedCount: () => tableRef.value?.getSelectedCount?.() ?? 0')
      expect(tableVue).toContain('ref="tableRef"')
      expect(tableVue).toContain('<template v-for="(_, name) in dynamicSlots" :key="name" #[name]="scope">')
      expect(tableVue).toContain('queryMode?: QueryMode')
      expect(tableVue).toContain('querySchemaOverride?: QuerySchema')
      expect(tableVue).toContain('showQueryPanel?: boolean')
      expect(tableVue).toContain(':query-mode="queryMode"')
      expect(tableVue).toContain(':query-schema="props.querySchemaOverride ?? querySchema"')
      expect(tableVue).toContain(':show-query-panel="showQueryPanel"')
      expect(tableSchema).toContain("queryMode: 'column'")
      expect(tableSchema).toContain('showFilters: true')
      expect(tableSchema).not.toContain('showSearchToolbar')
      expect(querySchema).toContain('export const defaultFormFieldKeys = ["orderNo"]')
      expect(querySchema).toContain('fields: defaultFormFieldKeys')
      expect(tableSchema).toContain('memberLookup')
      expect(tableSchema).toContain("selectionFieldName: 'serviceAreaId'")
      expect(tableSchema).toContain("displayFieldName: 'serviceArea'")
      expect(tableSchema).toContain('defaultLimit: 20')
    } finally {
      rmSync(tempRoot, { recursive: true, force: true })
    }
  })
})
