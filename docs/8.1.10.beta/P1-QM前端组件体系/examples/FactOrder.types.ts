/**
 * FactOrderQueryModel - 自动生成的类型定义
 * ⚠️ 此文件由 foggy-gen 自动生成，请勿手动修改
 * 生成时间: 2026-04-03
 * 元数据版本: frontend-meta v1
 */

/** 订单查询 - 行数据类型 */
export interface FactOrderRow {
  'orderDate$id'?: string
  'orderDate$caption'?: string
  'orderDate$year'?: string
  'orderDate$month'?: string
  'customer$id'?: string
  'customer$caption'?: string
  'customer$customerType'?: string
  'customer$province'?: string
  'customer$memberLevel'?: string
  'store$id'?: string
  'store$caption'?: string
  'channel$id'?: string
  'channel$caption'?: string
  orderId?: string
  orderStatus?: string
  paymentStatus?: string
  orderTime?: string
  quantity?: number
  amount?: number
  discountAmount?: number
  payAmount?: number
}

/** 订单查询 - 查询参数类型 */
export interface FactOrderQueryParams {
  orderId?: string
  orderStatus?: string | string[]
  paymentStatus?: string | string[]
  'orderDate$id'?: string | string[]
  'customer$id'?: string | string[]
  'store$id'?: string | string[]
  'channel$id'?: string | string[]
  orderTimeStart?: string
  orderTimeEnd?: string
  amountMin?: number
  amountMax?: number
}

/** 模型名称常量 */
export const FACT_ORDER_QM_MODEL = 'FactOrderQueryModel' as const
