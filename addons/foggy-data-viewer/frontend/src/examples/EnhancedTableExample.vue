<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import DataTable from '@/components/DataTable.vue'
import { fetchQueryMeta, fetchQueryData } from '@/api/viewer'
import { buildTableColumns } from '@/utils/schemaHelper'
import type { EnhancedColumnSchema, TableConfig } from '@/types'

// 示例1: 显式指定列及顺序
const tableConfig: TableConfig = {
  visibleColumns: ['orderId', 'orderDate', 'customerName', 'amount', 'status', 'isPaid', 'metadata'],
  customizations: [
    {
      name: 'orderId',
      width: 150,
      fixed: 'left'
    },
    {
      name: 'orderDate',
      width: 120
    },
    {
      name: 'customerName',
      width: 150
    },
    {
      // formatter: 用于格式化数据（导出时使用）
      name: 'amount',
      width: 120,
      formatter: (value) => `¥${Number(value).toFixed(2)}`
    },
    {
      // render: 用于自定义显示（仅前端显示，不影响导出）
      name: 'status',
      width: 100,
      render: ({ value }) => {
        const statusMap: Record<string, { text: string; color: string }> = {
          paid: { text: '已支付', color: '#67c23a' },
          pending: { text: '待支付', color: '#e6a23c' },
          cancelled: { text: '已取消', color: '#909399' }
        }
        const status = statusMap[value as string] || { text: value as string, color: '#909399' }
        return h('span', { style: { color: status.color, fontWeight: 'bold' } }, status.text)
      }
    },
    {
      // render: 将 true 渲染成勾符号
      name: 'isPaid',
      width: 80,
      render: ({ value }) => {
        return h('span', {
          style: { fontSize: '18px', color: value ? '#67c23a' : '#f56c6c' }
        }, value ? '✓' : '✗')
      }
    },
    {
      // formatter: 将 JSON 对象转成文字（用于导出）
      name: 'metadata',
      width: 200,
      formatter: (value) => {
        if (!value) return ''
        if (typeof value === 'object') {
          return JSON.stringify(value)
        }
        return String(value)
      }
    }
  ]
}

// 示例2: 显示所有列（按 QM schema 顺序）
const tableConfigShowAll: TableConfig = {
  showAll: true,
  customizations: [
    {
      name: 'orderId',
      width: 150,
      fixed: 'left'
    }
  ]
}

const columns = ref<EnhancedColumnSchema[]>([])
const data = ref<Record<string, unknown>[]>([])
const total = ref(0)
const loading = ref(false)

async function loadData() {
  try {
    loading.value = true

    // 1. 获取 QM schema
    const meta = await fetchQueryMeta('your-model', 'your-query-id')

    // 2. 合并 schema 和定制参数
    columns.value = buildTableColumns(meta.schema, tableConfig)

    // 3. 加载数据
    const response = await fetchQueryData('your-model', 'your-query-id', {
      start: 0,
      limit: 50
    })

    data.value = response.items
    total.value = response.total
  } catch (error) {
    console.error('Failed to load data:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="example-container">
    <h2>增强表格示例</h2>
    <DataTable
      :columns="columns"
      :data="data"
      :total="total"
      :loading="loading"
      @page-change="(page, size) => console.log('Page changed:', page, size)"
      @sort-change="(field, order) => console.log('Sort changed:', field, order)"
      @filter-change="(slices) => console.log('Filter changed:', slices)"
    />
  </div>
</template>

<style scoped>
.example-container {
  padding: 20px;
}
</style>
