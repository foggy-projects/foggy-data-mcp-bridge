<!--
  订单列表页面
  职责：布局、路由处理、组合多个 Module
  ✅ 此文件由业务团队手工维护
-->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import OrderListModule from '@/modules/order/OrderListModule.vue'

const router = useRouter()

function handleViewDetail(orderId: string) {
  router.push({ name: 'order-detail', params: { id: orderId } })
}

function handleExport() {
  // 调用业务导出接口
  console.log('触发导出')
}
</script>

<template>
  <div class="order-list-page">
    <h2>订单管理</h2>
    <OrderListModule
      @view-detail="handleViewDetail"
      @export="handleExport"
    />
  </div>
</template>

<style scoped>
.order-list-page {
  max-width: 1400px;
  margin: 0 auto;
}

.order-list-page h2 {
  margin-bottom: 16px;
  font-size: 20px;
  font-weight: 600;
}
</style>
