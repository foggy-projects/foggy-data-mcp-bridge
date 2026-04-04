/**
 * FactOrderQueryModel - 生成产物统一导出
 * ⚠️ 此文件由 foggy-gen 自动生成，请勿手动修改
 */

// 类型
export type { FactOrderRow, FactOrderQueryParams } from './FactOrder.types'
export { FACT_ORDER_QM_MODEL } from './FactOrder.types'

// 表格 Schema
export { allColumns, defaultVisibleColumns, defaultSearchFields, tableSchema } from './FactOrder.table.schema'

// 查询 Schema
export { queryFields, querySchema } from './FactOrder.query.schema'

// API
export { queryOrders, queryOrderMembers, getOrderMeta } from './FactOrder.api'

// 组件
export { default as FactOrderTable } from './FactOrderTable.vue'
