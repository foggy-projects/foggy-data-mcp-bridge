/**
 * FactOrderQueryModel - 自动生成的查询 Schema
 * ⚠️ 此文件由 foggy-gen 自动生成，请勿手动修改
 */
import type { QueryFieldSchema, QuerySchema } from 'foggy-data-viewer'

/** 查询字段定义 */
export const queryFields: QueryFieldSchema[] = [
  // ── 高频文本搜索 ──
  {
    key: 'orderId',
    label: '订单ID',
    sourceField: 'orderId',
    placement: 'column',
    component: 'text',
    defaultOperator: 'like',
    order: 1,
    span: 1,
  },

  // ── 字典下拉 ──
  {
    key: 'orderStatus',
    label: '订单状态',
    sourceField: 'orderStatus',
    placement: 'column',
    component: 'dictSelect',
    defaultOperator: 'in',
    dictId: 'order_status',
    columnRef: 'orderStatus',
    order: 2,
    span: 1,
  },
  {
    key: 'paymentStatus',
    label: '支付状态',
    sourceField: 'paymentStatus',
    placement: 'column',
    component: 'dictSelect',
    defaultOperator: 'in',
    dictId: 'payment_status',
    columnRef: 'paymentStatus',
    order: 3,
    span: 1,
  },

  // ── 时间范围 ──
  {
    key: 'orderTime',
    label: '下单时间',
    sourceField: 'orderTime',
    placement: 'form',
    component: 'dateRange',
    defaultOperator: '[]',
    columnRef: 'orderTime',
    order: 4,
    span: 2,
  },

  // ── 维度成员 ──
  {
    key: 'customer',
    label: '客户',
    sourceField: 'customer$id',
    placement: 'form',
    component: 'memberSelect',
    defaultOperator: 'in',
    lookupRef: 'customer$caption',
    columnRef: 'customer$caption',
    order: 5,
    span: 1,
  },
  {
    key: 'store',
    label: '门店',
    sourceField: 'store$id',
    placement: 'form',
    component: 'memberSelect',
    defaultOperator: 'in',
    lookupRef: 'store$caption',
    columnRef: 'store$caption',
    order: 6,
    span: 1,
  },
  {
    key: 'channel',
    label: '渠道',
    sourceField: 'channel$id',
    placement: 'form',
    component: 'memberSelect',
    defaultOperator: 'in',
    lookupRef: 'channel$caption',
    columnRef: 'channel$caption',
    order: 7,
    span: 1,
  },

  // ── 金额范围 ──
  {
    key: 'amount',
    label: '订单总额',
    sourceField: 'amount',
    placement: 'form',
    component: 'numberRange',
    defaultOperator: '[]',
    columnRef: 'amount',
    order: 8,
    span: 2,
  },
]

/** 查询 Schema */
export const querySchema: QuerySchema = {
  fields: queryFields,
  submitMode: 'manual',
  collapsible: true,
  defaultExpanded: true,
  layout: {
    mode: 'grid',
    columns: { xs: 1, sm: 2, md: 3, lg: 4, xl: 4 },
    labelWidth: 100,
    actionAlign: 'right',
    collapsedRows: 1,
    gutter: 16,
  },
}
