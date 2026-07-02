import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

interface StyleBlock {
  scoped: boolean
  content: string
}

function readComponentStyles(relativePath: string): StyleBlock[] {
  const filePath = fileURLToPath(new URL(relativePath, import.meta.url))
  const source = readFileSync(filePath, 'utf8')
  const blocks: StyleBlock[] = []
  const stylePattern = /<style(?<attrs>[^>]*)>(?<content>[\s\S]*?)<\/style>/g
  let match: RegExpExecArray | null

  while ((match = stylePattern.exec(source)) !== null) {
    blocks.push({
      scoped: /\bscoped\b/.test(match.groups?.attrs ?? ''),
      content: match.groups?.content ?? ''
    })
  }

  return blocks
}

function getUnscopedCss(relativePath: string): string {
  return readComponentStyles(relativePath)
    .filter(block => !block.scoped)
    .map(block => block.content)
    .join('\n')
}

describe('component style contracts', () => {
  it('keeps DataTable height, vxe slot, filter, and copy styles available without scoped attributes', () => {
    const css = getUnscopedCss('./DataTable.vue')

    expect(css).toContain('.data-table {')
    expect(css).toContain('.data-table .table-wrapper {')
    expect(css).toContain('.data-table .table-wrapper > .vxe-grid,')
    expect(css).toContain('.data-table .table-wrapper > .vxe-table {')
    expect(css).toContain('.data-table .column-header-wrapper {')
    expect(css).toContain('.data-table .column-title {')
    expect(css).toContain('.data-table .column-help-icon {')
    expect(css).toContain('.data-table .sort-icon {')
    expect(css).toContain('.data-table .sort-icon-svg {')
    expect(css).toContain('.data-table .column-filter input:not(.el-range-input):not(.el-input__inner),')
    expect(css).toContain('.data-table .column-filter select {')
    expect(css).toContain('.data-table .data-table-copyable-cell {')
    expect(css).toContain('.data-table .cell-copy-button {')
    expect(css).toContain('.data-table .cell-copy-icon {')
    expect(css).toContain('.data-table .data-table-boolean-cell {')
    expect(css).toContain('.data-table .vxe-body--column.data-table-boolean-column .vxe-cell svg,')
  })

  it('keeps DataTable header filter layout compact in unscoped styles', () => {
    const css = getUnscopedCss('./DataTable.vue')

    expect(css).toContain('height: 46px;')
    expect(css).toContain('max-height: 46px;')
    expect(css).toContain('height: 45px;')
    expect(css).toContain('max-height: 45px;')
    expect(css).toContain('height: 22px;')
    expect(css).toContain('height: 25px;')
    expect(css).toContain('min-height: 25px;')
    expect(css).toContain('.data-table .header-with-filter .vxe-header--column {')
    expect(css).toContain('padding: 3px 4px !important;')
  })

  it('keeps DataTableWithSearch height chain available without scoped attributes', () => {
    const css = getUnscopedCss('./DataTableWithSearch.vue')

    expect(css).toContain('.data-table-with-search {')
    expect(css).toContain('.data-table-with-search > .query-panel-wrapper,')
    expect(css).toContain('.data-table-with-search > .search-toolbar-wrapper {')
    expect(css).toContain('.data-table-with-search > .data-table-wrapper {')
    expect(css).toContain('.data-table-with-search > .data-table-wrapper > .data-table {')
  })
})
