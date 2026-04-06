<!--
  订单列表 - 业务包装层
  职责：组合 generated 组件 + 业务动作 + 参数注入
  ✅ 此文件由业务团队手工维护
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import type { QueryHooks, SliceRequestDef } from 'foggy-data-viewer'
import FactOrderTable from '@/generated/qm/order/FactOrderTable.vue'
import { orderColumnOverrides } from './order-module.config'
import { useOrderQueryHooks } from './order-query-hooks'

const props = defineProps<{
  /** 外部注入的额外全局参数 */
  globalParams?: Record<string, unknown>
}>()

const emit = defineEmits<{
  /** 查看订单详情 */
  (e: 'view-detail', orderId: string): void
  /** 导出数据 */
  (e: 'export'): void
}>()

const route = useRoute()

// ── 参数组装 ──

/** 全局参数：合并宿主上下文 + 外部注入 */
const mergedGlobalParams = computed(() => ({
  tenantId: route.query.tenantId,
  ...props.globalParams,
}))

/** 模型级定制参数 */
const customParams = computed(() => ({
  excludeTestOrders: true,
}))

/** 默认过滤：最近 30 天 */
const initialSlices: SliceRequestDef[] = [
  {
    field: 'orderTime',
    op: '[]',
    value: [getDateDaysAgo(30), getTodayEnd()],
  },
]

// ── 查询钩子 ──

const queryHooks: QueryHooks = useOrderQueryHooks()

// ── 工具函数 ──

function getDateDaysAgo(days: number): string {
  const d = new Date()
  d.setDate(d.getDate() - days)
  return d.toISOString().split('T')[0] + ' 00:00:00'
}

function getTodayEnd(): string {
  return new Date().toISOString().split('T')[0] + ' 23:59:59'
}

function handleViewDetail(row: Record<string, unknown>) {
  const orderId = row.orderId as string
  if (orderId) emit('view-detail', orderId)
}
</script>

<template>
  <div class="order-list-module">
    <FactOrderTable
      :global-params="mergedGlobalParams"
      :custom-params="customParams"
      :initial-slices="initialSlices"
      :column-overrides="orderColumnOverrides"
      :query-hooks="queryHooks"
      :show-query-panel="true"
    >
      <!-- 工具栏：业务动作按钮 -->
      <template #toolbar>
        <el-button type="primary" @click="$emit('export')">
          导出
        </el-button>
      </template>

      <!-- 行操作 -->
      <template #row-actions="{ row }">
        <el-button link type="primary" @click="handleViewDetail(row)">
          查看详情
        </el-button>
      </template>
    </FactOrderTable>
  </div>
</template>

<style scoped>
.order-list-module {
  padding: 16px;
}
</style>
