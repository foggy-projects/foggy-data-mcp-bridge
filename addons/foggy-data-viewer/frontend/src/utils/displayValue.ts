import type { EnhancedColumnSchema } from '@/types'
import { formatViewerValue } from './viewer'

/**
 * TMS 导出适配器必须保留的列元数据。
 *
 * `formatCellDisplayValue` 只读取这些字段，不会修改列定义或行数据。
 * 其中 `type` 是必需字段，避免导出适配器裁剪列元数据后丢失类型格式化规则。
 */
export type DisplayValueColumn = Pick<EnhancedColumnSchema,
  'name' | 'type' | 'title' | 'customFormatter' | 'dictItems' | 'extData' | 'category' | 'measure' | 'aggregatable'>

function hasIdentifierKeyword(value: string | undefined): boolean {
  if (!value) return false
  const normalized = value.trim()
  if (!normalized) return false

  return /(?:^|[_\-$.\s])(id|ids|no|code|number)(?:$|[_\-$.\s])/i.test(normalized) ||
    /(ID|编号|编码|代码|单号|运单号|订单号|账号|卡号|票号|证件号|手机号|电话|流水号|序列号)/.test(normalized)
}

function shouldFormatIntegerWithGrouping(col: DisplayValueColumn): boolean {
  if (col.measure === true || col.aggregatable === true || col.category === 'measure') {
    return true
  }
  if (col.category === 'dimension-id') {
    return false
  }
  if (hasIdentifierKeyword(col.name) || hasIdentifierKeyword(col.title)) {
    return false
  }
  return true
}

function formatIntegerDisplayValue(col: DisplayValueColumn, cellValue: unknown): string {
  if (cellValue == null) return ''
  if (typeof cellValue !== 'number') return String(cellValue)
  return shouldFormatIntegerWithGrouping(col)
    ? cellValue.toLocaleString('zh-CN')
    : String(cellValue)
}

/** Shared table/export text projection. Does not mutate values or column metadata. */
export function formatCellDisplayValue(col: DisplayValueColumn, cellValue: unknown): string {
  if (cellValue == null || cellValue === '') return ''

  if (col.customFormatter) {
    return col.customFormatter(cellValue)
  }

  if (col.dictItems && col.dictItems.length > 0) {
    const labelMap = new Map(col.dictItems.map(item => [String(item.value), item.label]))
    if (cellValue == null) return ''
    return labelMap.get(String(cellValue)) ?? String(cellValue)
  }

  const viewerValue = formatViewerValue(cellValue, col)
  if (viewerValue !== null) {
    return viewerValue
  }

  const type = col.type?.toUpperCase()
  switch (type) {
    case 'MONEY':
    case 'NUMBER':
    case 'BIGDECIMAL':
      if (cellValue == null) return ''
      return typeof cellValue === 'number'
        ? cellValue.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
        : String(cellValue)
    case 'INTEGER':
    case 'BIGINT':
    case 'LONG':
      return formatIntegerDisplayValue(col, cellValue)
    case 'DAY':
    case 'DATE':
      if (!cellValue) return ''
      return String(cellValue).split('T')[0]
    case 'DATETIME':
      if (!cellValue) return ''
      return String(cellValue).replace('T', ' ').substring(0, 19)
    case 'BOOL':
    case 'BOOLEAN':
      return cellValue === true ? '是' : cellValue === false ? '否' : ''
    default:
      return cellValue == null ? '' : String(cellValue)
  }
}
