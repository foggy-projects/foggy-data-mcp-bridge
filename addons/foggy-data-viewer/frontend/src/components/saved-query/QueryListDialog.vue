<template>
  <el-dialog
    v-model="visible"
    title="保存的查询"
    width="900px"
    :close-on-click-modal="false"
    @open="loadQueries"
  >
    <!-- 搜索框 -->
    <div class="query-search mb-4">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索查询名称或描述..."
        :prefix-icon="Search"
        clearable
      />
    </div>

    <!-- 左右分栏 -->
    <div class="query-container">
      <!-- 左侧：我的查询 -->
      <div class="query-section">
        <div class="section-header">
          <h4><el-icon><User /></el-icon> 我的查询</h4>
          <el-tag type="info" size="small">{{ myQueries.length }}</el-tag>
        </div>

        <el-scrollbar height="450px">
          <div v-if="loading" class="loading-container">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载中...</span>
          </div>

          <div v-else-if="myQueries.length === 0" class="empty-hint">
            <el-empty description="暂无保存的查询" />
          </div>

          <el-card
            v-for="query in filteredMyQueries"
            :key="query.id"
            class="query-card"
            shadow="hover"
          >
            <div class="query-header">
              <div class="query-title">
                <el-icon v-if="query.visibility === 'PRIVATE'"><Lock /></el-icon>
                <el-icon v-else-if="query.visibility === 'DEPARTMENT'"><OfficeBuilding /></el-icon>
                <el-icon v-else><Histogram /></el-icon>
                <span>{{ query.title }}</span>
              </div>
              <el-dropdown trigger="click">
                <el-icon class="more-icon"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="applyQuery(query)">
                      <el-icon><View /></el-icon> 应用
                    </el-dropdown-item>
                    <el-dropdown-item @click="deleteQuery(query)" divided class="danger-item">
                      <el-icon><Delete /></el-icon> 删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>

            <p v-if="query.description" class="query-desc">
              {{ query.description }}
            </p>

            <div class="query-meta">
              <el-tag size="small" :type="getVisibilityType(query.visibility)">
                {{ getVisibilityLabel(query.visibility) }}
              </el-tag>
              <span class="query-date">{{ formatDate(query.createdAt) }}</span>
            </div>

            <div class="query-stats">
              <el-icon><Grid /></el-icon>
              <span>{{ query.columns.length }} 字段</span>
              <el-divider direction="vertical" />
              <el-icon><Filter /></el-icon>
              <span>{{ query.slice?.length || 0 }} 筛选</span>
            </div>
          </el-card>
        </el-scrollbar>
      </div>

      <!-- 右侧：共享查询 -->
      <div class="query-section">
        <div class="section-header">
          <h4><el-icon><Share /></el-icon> 共享查询</h4>
          <el-tag type="success" size="small">{{ sharedQueries.length }}</el-tag>
        </div>

        <el-scrollbar height="450px">
          <div v-if="loading" class="loading-container">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载中...</span>
          </div>

          <div v-else-if="sharedQueries.length === 0" class="empty-hint">
            <el-empty description="暂无共享查询" />
          </div>

          <el-card
            v-for="query in filteredSharedQueries"
            :key="query.id"
            class="query-card"
            shadow="hover"
          >
            <div class="query-header">
              <div class="query-title">
                <el-icon v-if="query.visibility === 'DEPARTMENT'"><OfficeBuilding /></el-icon>
                <el-icon v-else><Histogram /></el-icon>
                <span>{{ query.title }}</span>
              </div>
              <el-button
                type="primary"
                size="small"
                @click="applyQuery(query)"
              >
                应用
              </el-button>
            </div>

            <p v-if="query.description" class="query-desc">
              {{ query.description }}
            </p>

            <div class="query-meta">
              <el-tag size="small" :type="getVisibilityType(query.visibility)">
                {{ getVisibilityLabel(query.visibility) }}
              </el-tag>
              <span class="query-owner">创建者: {{ query.ownerId }}</span>
              <span class="query-date">{{ formatDate(query.createdAt) }}</span>
            </div>

            <div class="query-stats">
              <el-icon><Grid /></el-icon>
              <span>{{ query.columns.length }} 字段</span>
              <el-divider direction="vertical" />
              <el-icon><Filter /></el-icon>
              <span>{{ query.slice?.length || 0 }} 筛选</span>
            </div>
          </el-card>
        </el-scrollbar>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listSavedQueries,
  deleteSavedQuery,
  applySavedQuery
} from '@/api/savedQuery'
import type { SavedQueryDef } from '@/api/savedQuery'
import {
  Search, User, Share, Lock, OfficeBuilding, Histogram,
  MoreFilled, View, Delete, Loading, Grid, Filter
} from '@element-plus/icons-vue'
import { formatDistanceToNow } from 'date-fns'
import { zhCN } from 'date-fns/locale'

interface Props {
  modelValue: boolean
  model: string
  businessId?: string
  currentUserId?: string  // 当前用户ID，用于区分自己的查询
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'apply': [query: SavedQueryDef, queryId: string]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const queries = ref<SavedQueryDef[]>([])
const loading = ref(false)
const searchKeyword = ref('')

// 分离我的查询和共享查询
const myQueries = computed(() =>
  queries.value.filter(q => q.ownerId === props.currentUserId)
)

const sharedQueries = computed(() =>
  queries.value.filter(q => q.ownerId !== props.currentUserId)
)

// 搜索过滤
const filteredMyQueries = computed(() =>
  filterQueries(myQueries.value, searchKeyword.value)
)

const filteredSharedQueries = computed(() =>
  filterQueries(sharedQueries.value, searchKeyword.value)
)

function filterQueries(list: SavedQueryDef[], keyword: string) {
  if (!keyword.trim()) return list
  const kw = keyword.toLowerCase()
  return list.filter(q =>
    q.title.toLowerCase().includes(kw) ||
    q.description?.toLowerCase().includes(kw)
  )
}

async function loadQueries() {
  loading.value = true
  try {
    queries.value = await listSavedQueries(props.model, props.businessId)
  } catch (error: any) {
    ElMessage.error(error.message || '加载查询列表失败')
  } finally {
    loading.value = false
  }
}

async function applyQuery(query: SavedQueryDef) {
  try {
    const result = await applySavedQuery(query.id)
    ElMessage.success(`已应用查询: ${query.title}`)
    emit('apply', query, result.queryId)
    visible.value = false
  } catch (error: any) {
    ElMessage.error(error.message || '应用查询失败')
  }
}

async function deleteQuery(query: SavedQueryDef) {
  try {
    await ElMessageBox.confirm(
      `确定要删除查询"${query.title}"吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )

    await deleteSavedQuery(query.id)
    ElMessage.success('删除成功')

    // 从列表中移除
    queries.value = queries.value.filter(q => q.id !== query.id)
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

function getVisibilityLabel(visibility: string) {
  const map: Record<string, string> = {
    PRIVATE: '仅自己',
    DEPARTMENT: '部门',
    TENANT: '全公司'
  }
  return map[visibility] || visibility
}

function getVisibilityType(visibility: string) {
  const map: Record<string, any> = {
    PRIVATE: 'info',
    DEPARTMENT: 'warning',
    TENANT: 'success'
  }
  return map[visibility] || 'info'
}

function formatDate(date: string) {
  return formatDistanceToNow(new Date(date), {
    addSuffix: true,
    locale: zhCN
  })
}
</script>

<style scoped>
.mb-4 {
  margin-bottom: 16px;
}

.query-container {
  display: flex;
  gap: 16px;
}

.query-section {
  flex: 1;
  min-width: 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ed;
}

.section-header h4 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #303133;
}

.query-card {
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.3s;
}

.query-card:hover {
  transform: translateY(-2px);
}

.query-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.query-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  font-size: 14px;
  color: #303133;
}

.query-desc {
  margin: 8px 0;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.query-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
  font-size: 12px;
  color: #909399;
}

.query-stats {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
  margin-top: 8px;
}

.loading-container,
.empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #909399;
}

.danger-item {
  color: #f56c6c;
}

.more-icon {
  cursor: pointer;
  font-size: 18px;
  color: #909399;
}

.more-icon:hover {
  color: #409eff;
}
</style>
