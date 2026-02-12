<template>
  <div class="saved-query-manager">
    <el-button-group size="small">
      <el-button :icon="FolderOpened" @click="openQueryList">
        加载查询
      </el-button>
      <el-button :icon="DocumentAdd" @click="openSaveDialog">
        保存查询
      </el-button>
    </el-button-group>

    <!-- 保存对话框 -->
    <SaveQueryDialog
      v-model="saveDialogVisible"
      :model="model"
      :business-id="businessId"
      :available-columns="availableColumns"
      :current-columns="currentState?.columns"
      :current-slice="currentState?.slice"
      :order-by="currentState?.orderBy"
      @saved="handleQuerySaved"
    />

    <!-- 查询列表对话框 -->
    <QueryListDialog
      v-model="queryListVisible"
      :model="model"
      :business-id="businessId"
      :current-user-id="currentUserId"
      @apply="handleQueryApplied"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { FolderOpened, DocumentAdd } from '@element-plus/icons-vue'
import SaveQueryDialog from './SaveQueryDialog.vue'
import QueryListDialog from './QueryListDialog.vue'
import type { SavedQueryDef } from '@/api/savedQuery'
import type { ColumnSchema } from '@/types'

interface DataTableInstance {
  getQueryState(): {
    columns: string[]
    slice: any[]
    orderBy: any[]
  }
  applyQueryState(state: {
    columns: string[]
    slice: any[]
    orderBy: any[]
  }): void
  reload(): Promise<void>
  getSchema(): ColumnSchema[]
}

interface Props {
  tableRef: any  // DataTable 实例引用
  model: string  // QM 模型名
  businessId?: string  // 业务ID（可选）
  currentUserId?: string  // 当前用户ID（用于区分我的查询）
  position?: 'top' | 'bottom'  // 按钮位置
}

const props = withDefaults(defineProps<Props>(), {
  position: 'top'
})

const saveDialogVisible = ref(false)
const queryListVisible = ref(false)

// 从 DataTable 实例获取当前状态
const currentState = computed(() => {
  if (!props.tableRef?.value) return null
  return props.tableRef.value.getQueryState()
})

// 获取可用列（从 DataTable schema）
const availableColumns = computed(() => {
  if (!props.tableRef?.value) return []
  return props.tableRef.value.getSchema?.() || []
})

function openSaveDialog() {
  if (!currentState.value) {
    ElMessage.warning('无法获取表格状态')
    return
  }
  saveDialogVisible.value = true
}

function openQueryList() {
  queryListVisible.value = true
}

async function handleQueryApplied(query: SavedQueryDef, queryId: string) {
  if (!props.tableRef?.value) return

  // 应用查询状态到 DataTable
  props.tableRef.value.applyQueryState({
    columns: query.columns,
    slice: query.slice,
    orderBy: query.orderBy
  })

  // 重新加载数据
  await props.tableRef.value.reload()

  ElMessage.success(`已应用查询: ${query.title}`)
}

function handleQuerySaved(query: SavedQueryDef) {
  ElMessage.success('查询保存成功')
}
</script>

<style scoped>
.saved-query-manager {
  display: inline-block;
}
</style>
