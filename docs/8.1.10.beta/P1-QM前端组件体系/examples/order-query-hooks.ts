/**
 * 订单模块 - 查询钩子
 * ✅ 此文件由业务团队手工维护
 *
 * 职责：查询前后处理、埋点、错误处理
 */
import type { QueryHooks, QueryHookContext, FetchDataResult } from '@foggy/data-viewer'

export function useOrderQueryHooks(): QueryHooks {
  return {
    /**
     * 查询前：注入业务约束
     * - 自动添加租户隔离条件
     * - 查询埋点
     */
    onBeforeQuery(ctx: QueryHookContext) {
      // 示例：埋点记录
      console.log(`[订单模块] 查询触发: ${ctx.trigger}`, {
        page: ctx.params.page,
        filters: ctx.params.slice.length,
      })

      // 示例：强制添加不可覆盖的业务条件
      // （安全上下文字段通常由 globalQueryHooks 注入，这里仅作演示）
      // ctx.params.slice.push({ field: 'isDeleted', op: '=', value: false })
    },

    /**
     * 查询后：结果后处理
     * - 数据格式化
     * - 埋点记录
     */
    onAfterQuery(_ctx: QueryHookContext, result: FetchDataResult) {
      console.log(`[订单模块] 查询完成: ${result.total} 条`)
      // 不修改结果，返回 void
    },

    /**
     * 查询错误：统一错误处理
     */
    onQueryError(_ctx: QueryHookContext, error: Error) {
      console.error('[订单模块] 查询失败:', error.message)
      // 返回 void 继续默认错误处理
      // 返回 true 表示已处理，阻止冒泡
    },
  }
}
