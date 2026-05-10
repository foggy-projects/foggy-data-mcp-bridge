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

      expect(tableVue).toContain(
        "import type { SliceRequestDef, QueryHooks, EnhancedColumnSchema, QueryMode } from 'foggy-data-viewer'"
      )
      expect(tableVue).toContain('queryMode?: QueryMode')
      expect(tableVue).toContain('showQueryPanel?: boolean')
      expect(tableVue).toContain(':query-mode="queryMode"')
      expect(tableVue).toContain(':show-query-panel="showQueryPanel"')
      expect(tableSchema).toContain("queryMode: 'column'")
      expect(tableSchema).toContain('showFilters: true')
      expect(tableSchema).not.toContain('showSearchToolbar')
    } finally {
      rmSync(tempRoot, { recursive: true, force: true })
    }
  })
})
