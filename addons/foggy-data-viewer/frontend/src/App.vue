<script setup lang="ts">
import { ref, computed } from 'vue'
import DataViewer from './components/DataViewer.vue'
import { createQuery, type CreateQueryRequest } from './api/viewer'

// 从 URL 中获取 queryId
const queryId = computed(() => {
  const path = window.location.pathname
  // 匹配 /data-viewer/view/{queryId} 格式
  const match = path.match(/\/data-viewer\/view\/([^/]+)/)
  return match ? match[1] : null
})

// DSL 输入相关状态
const dslInput = ref('')
const isSubmitting = ref(false)
const errorMessage = ref('')

// 示例 DSL 查询
const examples = [
  {
    name: '销售明细查询',
    description: '查询最近30天的销售订单明细',
    dsl: {
      model: 'FactSalesQueryModel',
      title: '销售明细查询',
      columns: ['orderId', 'salesDate$caption', 'product$caption', 'customer$caption', 'quantity', 'salesAmount', 'profitAmount'],
      slice: [
        { field: 'salesDate$caption', op: '>=', value: getDateOffset(-30) },
        { field: 'salesDate$caption', op: '<', value: getDateOffset(0) }
      ],
      orderBy: [{ field: 'salesDate$caption', order: 'desc' }]
    }
  },
  {
    name: '订单查询',
    description: '查询最近7天的订单信息',
    dsl: {
      model: 'FactOrderQueryModel',
      title: '订单查询',
      columns: ['orderId', 'orderStatus', 'paymentStatus', 'orderTime', 'customer$caption', 'amount', 'payAmount'],
      slice: [
        { field: 'orderDate$caption', op: '>=', value: getDateOffset(-7) },
        { field: 'orderDate$caption', op: '<', value: getDateOffset(1) }
      ],
      orderBy: [{ field: 'orderTime', order: 'desc' }]
    }
  },
  {
    name: '商品列表',
    description: '查询所有商品基础信息',
    dsl: {
      model: 'DimProductQueryModel',
      title: '商品列表',
      columns: ['productName', 'productId', 'brand', 'categoryName', 'subCategoryName', 'unitPrice', 'unitCost'],
      slice: [
        { field: 'status', op: '=', value: '正常' }
      ],
      orderBy: [{ field: 'productName', order: 'asc' }]
    }
  },
  {
    name: '客户列表',
    description: '查询VIP会员客户',
    dsl: {
      model: 'DimCustomerQueryModel',
      title: 'VIP客户列表',
      columns: ['customerName', 'customerId', 'customerType', 'memberLevel', 'gender', 'province', 'city'],
      slice: [
        { field: 'memberLevel', op: '=', value: 'VIP' }
      ],
      orderBy: [{ field: 'customerName', order: 'asc' }]
    }
  },
  {
    name: '门店业绩',
    description: '按门店汇总销售业绩',
    dsl: {
      model: 'FactSalesQueryModel',
      title: '门店业绩汇总',
      columns: ['store$caption', 'store$storeType', 'store$province', 'store$city', 'quantity', 'salesAmount', 'profitAmount'],
      slice: [
        { field: 'salesDate$caption', op: '>=', value: getDateOffset(-30) },
        { field: 'salesDate$caption', op: '<', value: getDateOffset(0) }
      ],
      groupBy: [{ field: 'store$id' }],
      orderBy: [{ field: 'salesAmount', order: 'desc' }]
    }
  }
]

// 获取相对日期（用于示例）
function getDateOffset(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return date.toISOString().split('T')[0]
}

// 选择示例
function selectExample(example: typeof examples[0]) {
  dslInput.value = JSON.stringify(example.dsl, null, 2)
  errorMessage.value = ''
}

// 提交查询
async function submitQuery() {
  errorMessage.value = ''

  if (!dslInput.value.trim()) {
    errorMessage.value = '请输入查询 DSL'
    return
  }

  let request: CreateQueryRequest
  try {
    request = JSON.parse(dslInput.value)
  } catch (e) {
    errorMessage.value = 'JSON 格式错误: ' + (e as Error).message
    return
  }

  isSubmitting.value = true
  try {
    const response = await createQuery(request)
    if (response.success && response.viewerUrl) {
      // 跳转到查询页面
      window.location.href = response.viewerUrl
    } else {
      errorMessage.value = response.error || '创建查询失败'
    }
  } catch (e) {
    errorMessage.value = '请求失败: ' + (e as Error).message
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div id="app">
    <DataViewer v-if="queryId" :query-id="queryId" />
    <div v-else class="landing-page">
      <div class="hero">
        <h1>Foggy Data Viewer</h1>
        <p class="subtitle">请通过有效的查询链接访问数据浏览器，或者输入 Foggy DSL 查询参数来浏览数据</p>
      </div>

      <div class="main-content">
        <div class="examples-section">
          <h2>示例查询</h2>
          <p class="section-desc">点击示例将 DSL 填入输入框</p>
          <div class="examples-grid">
            <div
              v-for="example in examples"
              :key="example.name"
              class="example-card"
              @click="selectExample(example)"
            >
              <h3>{{ example.name }}</h3>
              <p>{{ example.description }}</p>
            </div>
          </div>
        </div>

        <div class="input-section">
          <h2>DSL 查询输入</h2>
          <p class="section-desc">输入 JSON 格式的查询参数</p>
          <textarea
            v-model="dslInput"
            class="dsl-textarea"
            placeholder='{
  "model": "FactSalesQueryModel",
  "title": "我的查询",
  "columns": ["orderId", "salesDate", "salesAmount"],
  "slice": [
    { "field": "salesDate", "op": ">=", "value": "2024-01-01" }
  ]
}'
            :disabled="isSubmitting"
          ></textarea>

          <div v-if="errorMessage" class="error-message">
            {{ errorMessage }}
          </div>

          <button
            class="submit-btn"
            @click="submitQuery"
            :disabled="isSubmitting"
          >
            {{ isSubmitting ? '提交中...' : '提交查询' }}
          </button>
        </div>
      </div>

      <div class="help-section">
        <h3>DSL 参数说明</h3>
        <table class="help-table">
          <thead>
            <tr>
              <th>参数</th>
              <th>类型</th>
              <th>必填</th>
              <th>说明</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>model</code></td>
              <td>string</td>
              <td>是</td>
              <td>查询模型名称，如 FactSalesQueryModel</td>
            </tr>
            <tr>
              <td><code>columns</code></td>
              <td>string[]</td>
              <td>是</td>
              <td>要查询的列名列表</td>
            </tr>
            <tr>
              <td><code>slice</code></td>
              <td>object[]</td>
              <td>是</td>
              <td>过滤条件，每项包含 field、op、value</td>
            </tr>
            <tr>
              <td><code>title</code></td>
              <td>string</td>
              <td>否</td>
              <td>查询标题</td>
            </tr>
            <tr>
              <td><code>groupBy</code></td>
              <td>object[]</td>
              <td>否</td>
              <td>分组字段，用于聚合查询</td>
            </tr>
            <tr>
              <td><code>orderBy</code></td>
              <td>object[]</td>
              <td>否</td>
              <td>排序字段，包含 field 和 order</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  width: 100%;
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.landing-page {
  min-height: 100%;
  padding: 40px 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
}

.hero {
  text-align: center;
  margin-bottom: 40px;
}

.hero h1 {
  font-size: 2.5rem;
  color: #303133;
  margin-bottom: 12px;
}

.subtitle {
  font-size: 1.1rem;
  color: #606266;
  max-width: 600px;
  margin: 0 auto;
}

.main-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 30px;
  max-width: 1200px;
  margin: 0 auto 40px;
}

@media (max-width: 900px) {
  .main-content {
    grid-template-columns: 1fr;
  }
}

.examples-section,
.input-section {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.examples-section h2,
.input-section h2 {
  font-size: 1.3rem;
  color: #303133;
  margin-bottom: 8px;
}

.section-desc {
  font-size: 0.9rem;
  color: #909399;
  margin-bottom: 16px;
}

.examples-grid {
  display: grid;
  gap: 12px;
}

.example-card {
  padding: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.example-card:hover {
  border-color: #409eff;
  background: #f0f7ff;
  transform: translateY(-2px);
}

.example-card h3 {
  font-size: 1rem;
  color: #303133;
  margin-bottom: 4px;
}

.example-card p {
  font-size: 0.85rem;
  color: #909399;
}

.dsl-textarea {
  width: 100%;
  height: 300px;
  padding: 12px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.5;
  resize: vertical;
  transition: border-color 0.2s;
}

.dsl-textarea:focus {
  outline: none;
  border-color: #409eff;
}

.dsl-textarea:disabled {
  background: #f5f7fa;
}

.error-message {
  margin-top: 12px;
  padding: 10px 12px;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 6px;
  color: #f56c6c;
  font-size: 0.9rem;
}

.submit-btn {
  margin-top: 16px;
  width: 100%;
  padding: 12px 24px;
  background: #409eff;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  cursor: pointer;
  transition: background 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background: #337ecc;
}

.submit-btn:disabled {
  background: #a0cfff;
  cursor: not-allowed;
}

.help-section {
  max-width: 1200px;
  margin: 0 auto;
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.help-section h3 {
  font-size: 1.1rem;
  color: #303133;
  margin-bottom: 16px;
}

.help-table {
  width: 100%;
  border-collapse: collapse;
}

.help-table th,
.help-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid #ebeef5;
}

.help-table th {
  background: #f5f7fa;
  font-weight: 600;
  color: #606266;
}

.help-table td {
  color: #606266;
}

.help-table code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  color: #409eff;
}
</style>
