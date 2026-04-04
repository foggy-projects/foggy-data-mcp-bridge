/**
 * 订单模块 - 列覆盖与默认配置
 * ✅ 此文件由业务团队手工维护
 */

interface BusinessColumnOverride {
  title?: string
  width?: number | string
  hidden?: boolean
  order?: number
  formatter?: (value: unknown) => string
  uiConfig?: Record<string, unknown>
}

/** 订单列表列覆盖 */
export const orderColumnOverrides: Record<string, BusinessColumnOverride> = {
  // 金额列固定宽度并格式化
  amount: {
    width: 120,
    formatter: (v) => (v != null ? `¥${Number(v).toFixed(2)}` : '-'),
  },
  payAmount: {
    width: 130,
    formatter: (v) => (v != null ? `¥${Number(v).toFixed(2)}` : '-'),
  },
  discountAmount: {
    hidden: true, // 列表页不展示折扣金额，详情页展示
  },

  // 订单状态宽度固定
  orderStatus: {
    width: 100,
  },
}
