<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import DataTable from './DataTable.vue'
import { fetchQueryMeta, fetchQueryData } from '@/api/viewer'
import type { QueryMetaResponse, ViewerQueryRequest, FilterValue } from '@/types'

const props = defineProps<{
  queryId: string
}>()

// 状态
const loading = ref(false)
const error = ref<string | null>(null)
const expired = ref(false)
const meta = ref<QueryMetaResponse | null>(null)
const data = ref<Record<string, unknown>[]>([])
const total = ref(0)

// 查询参数
const queryParams = ref<ViewerQueryRequest>({
  start: 0,
  limit: 50,
  filters: {},
  sortField: undefined,
  sortOrder: undefined
})

const dataTableRef = ref<InstanceType<typeof DataTable>>()

// 计算属性
const title = computed(() => meta.value?.title || '数据浏览器')
const expiresAt = computed(() => {
  if (!meta.value?.expiresAt) return ''
  return new Date(meta.value.expiresAt).toLocaleString('zh-CN')
})

// 加载元数据
async function loadMeta() {
  try {
    loading.value = true
    error.value = null
    meta.value = await fetchQueryMeta(props.queryId)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载元数据失败'
  } finally {
    loading.value = false
  }
}

// 加载数据
async function loadData() {
  if (!meta.value) return

  try {
    loading.value = true
    error.value = null

    const response = await fetchQueryData(props.queryId, queryParams.value)

    if (response.expired) {
      expired.value = true
      error.value = response.errorMessage || '查询已过期'
      return
    }

    if (!response.success) {
      error.value = response.errorMessage || '查询失败'
      return
    }

    data.value = response.items
    total.value = response.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载数据失败'
  } finally {
    loading.value = false
  }
}

// 事件处理
function handlePageChange(page: number, size: number) {
  queryParams.value.start = (page - 1) * size
  queryParams.value.limit = size
  loadData()
}

function handleSortChange(field: string | null, order: 'asc' | 'desc' | null) {
  queryParams.value.sortField = field ?? undefined
  queryParams.value.sortOrder = order ?? undefined
  loadData()
}

function handleFilterChange(filters: Record<string, FilterValue>) {
  queryParams.value.filters = filters
  queryParams.value.start = 0
  dataTableRef.value?.resetPagination()
  loadData()
}

// 刷新数据
function refresh() {
  loadData()
}

// 初始化
onMounted(async () => {
  await loadMeta()
  if (meta.value) {
    await loadData()
  }
})
</script>

<template>
  <div class="data-viewer">
    <header class="viewer-header">
      <h1 class="viewer-title">{{ title }}</h1>
      <div class="viewer-info">
        <span v-if="meta?.model" class="info-item">
          模型: <strong>{{ meta.model }}</strong>
        </span>
        <span v-if="meta?.estimatedRowCount" class="info-item">
          预估行数: <strong>{{ meta.estimatedRowCount.toLocaleString() }}</strong>
        </span>
        <span v-if="expiresAt" class="info-item">
          过期时间: <strong>{{ expiresAt }}</strong>
        </span>
        <button class="refresh-btn" @click="refresh" :disabled="loading">
          刷新
        </button>
      </div>
    </header>

    <div v-if="expired" class="viewer-expired">
      <div class="expired-content">
        <h2>链接已过期</h2>
        <p>请联系AI助手重新生成查询链接</p>
      </div>
    </div>

    <div v-else-if="error && !data.length" class="viewer-error">
      <div class="error-content">
        <h2>加载失败</h2>
        <p>{{ error }}</p>
        <button @click="loadData">重试</button>
      </div>
    </div>

    <main v-else class="viewer-main">
      <DataTable
        ref="dataTableRef"
        :columns="meta?.schema || []"
        :data="data"
        :total="total"
        :loading="loading"
        @page-change="handlePageChange"
        @sort-change="handleSortChange"
        @filter-change="handleFilterChange"
      />
    </main>

    <footer class="viewer-footer">
      <span>Foggy Data Viewer</span>
    </footer>
  </div>
</template>

<style scoped>
.data-viewer {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f5f7fa;
}

.viewer-header {
  padding: 16px 24px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.viewer-title {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.viewer-info {
  display: flex;
  align-items: center;
  gap: 24px;
  font-size: 14px;
  color: #606266;
}

.info-item strong {
  color: #303133;
}

.refresh-btn {
  margin-left: auto;
  padding: 6px 16px;
  background-color: #409eff;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.refresh-btn:hover {
  background-color: #66b1ff;
}

.refresh-btn:disabled {
  background-color: #a0cfff;
  cursor: not-allowed;
}

.viewer-main {
  flex: 1;
  padding: 16px 24px;
  overflow: hidden;
}

.viewer-expired,
.viewer-error {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.expired-content,
.error-content {
  text-align: center;
  padding: 32px;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.expired-content h2,
.error-content h2 {
  margin: 0 0 12px 0;
  color: #f56c6c;
}

.expired-content p,
.error-content p {
  margin: 0 0 16px 0;
  color: #909399;
}

.error-content button {
  padding: 8px 24px;
  background-color: #409eff;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.viewer-footer {
  padding: 8px 24px;
  text-align: center;
  font-size: 12px;
  color: #909399;
  background-color: #fff;
  border-top: 1px solid #e4e7ed;
}
</style>
