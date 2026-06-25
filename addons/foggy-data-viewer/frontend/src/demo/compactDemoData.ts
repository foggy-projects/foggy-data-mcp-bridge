import type { EnhancedColumnSchema, OrderRequestDef, TableSchema } from '../types'

export const compactDemoColumns: EnhancedColumnSchema[] = [
  { name: 'waybillNo', title: '运单号', description: '运输单据编号，用于定位单票货物', type: 'string', width: 150, filterable: true, filterType: 'text' },
  { name: 'customerNo', title: '客单号', description: '客户侧订单编号，可能为空', type: 'string', width: 150, filterable: true, filterType: 'text' },
  { name: 'openingSite', title: '开单网点', description: '创建运单并录入货物信息的网点', type: 'string', width: 150, filterable: true, filterType: 'text' },
  { name: 'nextStation', title: '下一站', type: 'string', width: 150, filterable: true, filterType: 'text' },
  { name: 'arrivalSite', title: '运达网点', type: 'string', width: 150, filterable: true, filterType: 'text' },
  { name: 'destination', title: '目的地', type: 'string', width: 150, filterable: true, filterType: 'text' },
  { name: 'isBranchCompany', title: '分公司', type: 'BOOL', width: 88, filterable: true, filterType: 'bool' },
  { name: 'isTerminalOrg', title: '末端机构', type: 'BOOLEAN', width: 88, filterable: true, filterType: 'bool' },
  { name: 'stockInTime', title: '入库时间', type: 'datetime', width: 220, filterable: true, filterType: 'date' },
  { name: 'stockInCount', title: '已入库件数', type: 'number', width: 150, filterable: true, filterType: 'number' }
]

export const compactDemoRows: Record<string, unknown>[] = [
  ['432154486', '251125000012', '济南集配', '广州', '广州', '广州', true, false, '2026-06-15 13:33:37', 1],
  ['432153784', 'YZ000000019', '青岛集配', '广州', '广州', '广州', true, false, '2026-06-09 16:21:25', 1000],
  ['432154430', '', '青岛集配', '贵阳', '贵阳', '白云区', false, true, '2026-06-09 13:16:10', 2],
  ['432154407', '', '青岛集配', '城阳区', '青岛集配', '城阳区', false, true, '2026-06-08 16:12:52', 2],
  ['432154406', '', '青岛集配', '城阳区', '青岛集配', '城阳区', false, true, '2026-06-08 15:51:26', 2],
  ['432154367', '', '济南集配', '广州', '广州', '白云区', true, false, '2026-06-08 10:03:08', 2],
  ['432154339', '', '美里', '济南集配', '广州', '广州', false, false, '2026-06-05 11:28:55', 3],
  ['432154336', '', '美里', '济南集配', '广州', '广州', false, false, '2026-06-05 11:28:17', 3],
  ['432154335', '', '美里', '济南集配', '济南集配', '济南集配', true, true, '2026-06-05 11:27:18', 3],
  ['432154333', '', '美里', '济南集配', '济南集配', '济南集配', true, true, '2026-06-05 11:26:51', 3]
].map(([waybillNo, customerNo, openingSite, nextStation, arrivalSite, destination, isBranchCompany, isTerminalOrg, stockInTime, stockInCount]) => ({
  waybillNo,
  customerNo,
  openingSite,
  nextStation,
  arrivalSite,
  destination,
  isBranchCompany,
  isTerminalOrg,
  stockInTime,
  stockInCount
}))

export const compactDemoSchema: TableSchema = {
  columns: compactDemoColumns,
  queryMode: 'column',
  density: 'compact',
  showPager: false,
  pageSize: 50
}

function isEmptyCompactSortValue(value: unknown): boolean {
  return value === null || value === undefined || value === ''
}

function getCompactNumericSortValue(value: unknown): number | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }

  if (typeof value === 'string') {
    const normalized = value.replace(/,/g, '').trim()
    if (!normalized) return null
    const numberValue = Number(normalized)
    return Number.isFinite(numberValue) ? numberValue : null
  }

  return null
}

function compareCompactSortValues(leftValue: unknown, rightValue: unknown, field: string): number {
  const column = compactDemoColumns.find(col => col.name === field)
  const type = column?.type?.toUpperCase() ?? ''

  if (['NUMBER', 'INTEGER', 'DECIMAL', 'MONEY'].some(keyword => type.includes(keyword))) {
    const leftNumber = getCompactNumericSortValue(leftValue)
    const rightNumber = getCompactNumericSortValue(rightValue)
    if (leftNumber !== null && rightNumber !== null) {
      return leftNumber - rightNumber
    }
  }

  if (type.includes('DATE') || type.includes('TIME')) {
    const leftTime = Date.parse(String(leftValue))
    const rightTime = Date.parse(String(rightValue))
    if (Number.isFinite(leftTime) && Number.isFinite(rightTime)) {
      return leftTime - rightTime
    }
  }

  return String(leftValue).localeCompare(String(rightValue), 'zh-CN', {
    numeric: true,
    sensitivity: 'base'
  })
}

export function sortCompactDemoRows(orderBy: OrderRequestDef[] = []): Record<string, unknown>[] {
  const sortDef = orderBy[0]
  const sortDir = sortDef?.dir ?? sortDef?.order
  let items = [...compactDemoRows]

  if (sortDef?.field && sortDir) {
    items = items
      .map((row, index) => ({ row, index }))
      .sort((left, right) => {
        const leftValue = left.row[sortDef.field]
        const rightValue = right.row[sortDef.field]
        const leftEmpty = isEmptyCompactSortValue(leftValue)
        const rightEmpty = isEmptyCompactSortValue(rightValue)

        if (leftEmpty || rightEmpty) {
          if (leftEmpty && rightEmpty) return left.index - right.index
          return leftEmpty ? 1 : -1
        }

        const result = compareCompactSortValues(leftValue, rightValue, sortDef.field)
        const orderedResult = sortDir === 'asc' ? result : -result
        return orderedResult || left.index - right.index
      })
      .map(item => item.row)
  }

  return items
}
