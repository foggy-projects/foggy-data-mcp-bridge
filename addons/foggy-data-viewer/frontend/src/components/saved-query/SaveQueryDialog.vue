<template>
  <el-dialog
    v-model="visible"
    title="保存查询"
    width="800px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <!-- 步骤导航 -->
    <el-steps :active="currentStep" finish-status="success" align-center class="mb-6">
      <el-step title="选择列" icon="Grid" />
      <el-step title="设置查询条件" icon="Filter" />
      <el-step title="命名和确认" icon="CircleCheck" />
    </el-steps>

    <div class="step-content">
      <!-- 第一步：选择列 -->
      <div v-show="currentStep === 0" class="step-panel">
        <div class="step-title">
          <h3>选择要保存的字段</h3>
          <p class="step-desc">从所有可用字段中选择需要保存在查询中的列</p>
        </div>

        <div class="column-selector">
          <!-- 搜索框 -->
          <el-input
            v-model="columnSearchKeyword"
            placeholder="搜索字段..."
            :prefix-icon="Search"
            clearable
            class="mb-3"
          />

          <!-- 全选/反选 -->
          <div class="selection-actions mb-3">
            <el-checkbox
              v-model="selectAllColumns"
              :indeterminate="isColumnsIndeterminate"
              @change="handleSelectAllColumns"
            >
              全选 ({{ selectedColumns.length }}/{{ availableColumns.length }})
            </el-checkbox>
            <el-button link @click="handleInvertColumnSelection">反选</el-button>
          </div>

          <!-- 列列表 -->
          <el-scrollbar height="350px">
            <el-checkbox-group v-model="selectedColumns" class="column-list">
              <div
                v-for="col in filteredColumns"
                :key="col.name"
                class="column-item"
              >
                <el-checkbox :label="col.name">
                  <div class="column-info">
                    <span class="column-title">{{ col.title || col.name }}</span>
                    <el-tag v-if="col.type" size="small" type="info">
                      {{ col.type }}
                    </el-tag>
                  </div>
                </el-checkbox>
              </div>
            </el-checkbox-group>
          </el-scrollbar>
        </div>
      </div>

      <!-- 第二步：设置查询条件 -->
      <div v-show="currentStep === 1" class="step-panel">
        <div class="step-title">
          <h3>配置查询条件</h3>
          <p class="step-desc">设置筛选条件的参数（单选/多选/默认值）</p>
        </div>

        <div class="condition-config">
          <el-button
            :icon="Plus"
            @click="addCondition"
            class="mb-3"
          >
            添加筛选条件
          </el-button>

          <div v-if="conditions.length === 0" class="empty-hint">
            <el-empty description="暂无筛选条件，点击上方按钮添加" />
          </div>

          <el-scrollbar v-else height="400px">
            <el-card
              v-for="(cond, index) in conditions"
              :key="index"
              class="condition-card mb-3"
              shadow="hover"
            >
              <div class="condition-header">
                <span class="condition-index">#{{ index + 1 }}</span>
                <el-button
                  :icon="Delete"
                  text
                  type="danger"
                  @click="removeCondition(index)"
                />
              </div>

              <el-form label-width="100px" class="condition-form">
                <!-- 字段选择 -->
                <el-form-item label="筛选字段">
                  <el-select
                    v-model="cond.field"
                    placeholder="选择字段"
                    filterable
                    @change="handleFieldChange(cond)"
                  >
                    <el-option
                      v-for="col in availableColumns"
                      :key="col.name"
                      :label="col.title || col.name"
                      :value="col.name"
                    />
                  </el-select>
                </el-form-item>

                <!-- 操作符 -->
                <el-form-item label="操作符">
                  <el-select v-model="cond.op" placeholder="选择操作符">
                    <el-option label="等于 (=)" value="=" />
                    <el-option label="不等于 (!=)" value="!=" />
                    <el-option label="大于 (>)" value=">" />
                    <el-option label="大于等于 (>=)" value=">=" />
                    <el-option label="小于 (<)" value="<" />
                    <el-option label="小于等于 (<=)" value="<=" />
                    <el-option label="包含 (IN)" value="in" />
                    <el-option label="不包含 (NOT IN)" value="not in" />
                    <el-option label="范围 (BETWEEN)" value="between" />
                    <el-option label="模糊匹配 (LIKE)" value="like" />
                    <el-option label="为空 (IS NULL)" value="is null" />
                    <el-option label="不为空 (IS NOT NULL)" value="is not null" />
                  </el-select>
                </el-form-item>

                <!-- 参数配置 -->
                <el-form-item label="参数类型">
                  <el-radio-group v-model="cond.paramType">
                    <el-radio label="fixed">固定值</el-radio>
                    <el-radio label="single">单选</el-radio>
                    <el-radio label="multiple">多选</el-radio>
                  </el-radio-group>
                </el-form-item>

                <!-- 默认值 -->
                <el-form-item
                  v-if="!['is null', 'is not null'].includes(cond.op)"
                  label="默认值"
                >
                  <!-- 固定值输入 -->
                  <el-input
                    v-if="cond.paramType === 'fixed'"
                    v-model="cond.defaultValue"
                    placeholder="输入默认值"
                  />

                  <!-- 单选/多选 - 需要提供选项列表 -->
                  <div v-else class="param-options">
                    <el-select
                      v-if="cond.paramType === 'single'"
                      v-model="cond.defaultValue"
                      placeholder="选择默认值"
                      clearable
                    >
                      <el-option
                        v-for="opt in cond.options"
                        :key="opt.value"
                        :label="opt.label"
                        :value="opt.value"
                      />
                    </el-select>

                    <el-select
                      v-else-if="cond.paramType === 'multiple'"
                      v-model="cond.defaultValue"
                      placeholder="选择默认值"
                      multiple
                      clearable
                    >
                      <el-option
                        v-for="opt in cond.options"
                        :key="opt.value"
                        :label="opt.label"
                        :value="opt.value"
                      />
                    </el-select>

                    <!-- 选项管理 -->
                    <el-button
                      :icon="Setting"
                      @click="openOptionManager(cond)"
                      class="ml-2"
                    >
                      配置选项
                    </el-button>
                  </div>
                </el-form-item>

                <!-- 可选：显示名称 -->
                <el-form-item label="显示名称">
                  <el-input
                    v-model="cond.label"
                    placeholder="可选，在查询界面显示的名称"
                  />
                </el-form-item>
              </el-form>
            </el-card>
          </el-scrollbar>
        </div>
      </div>

      <!-- 第三步：命名和确认 -->
      <div v-show="currentStep === 2" class="step-panel">
        <div class="step-title">
          <h3>填写查询信息</h3>
          <p class="step-desc">为查询命名并选择可见范围</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="100px"
          class="naming-form"
        >
          <el-form-item label="查询名称" prop="title">
            <el-input
              v-model="form.title"
              placeholder="请输入查询名称"
              maxlength="50"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="描述" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="3"
              placeholder="可选，描述查询用途"
              maxlength="200"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="可见性" prop="visibility">
            <el-radio-group v-model="form.visibility">
              <el-radio label="PRIVATE">
                <el-icon><User /></el-icon> 仅自己可见
              </el-radio>
              <el-radio label="DEPARTMENT">
                <el-icon><OfficeBuilding /></el-icon> 部门内共享
              </el-radio>
              <el-radio label="TENANT">
                <el-icon><Histogram /></el-icon> 全公司共享
              </el-radio>
            </el-radio-group>
            <div class="visibility-hint">
              <el-alert
                v-if="form.visibility === 'PRIVATE'"
                type="info"
                :closable="false"
                show-icon
              >
                只有您自己可以查看和使用此查询
              </el-alert>
              <el-alert
                v-else-if="form.visibility === 'DEPARTMENT'"
                type="warning"
                :closable="false"
                show-icon
              >
                您所在部门的所有成员都可以查看和使用此查询
              </el-alert>
              <el-alert
                v-else
                type="success"
                :closable="false"
                show-icon
              >
                公司内所有成员都可以查看和使用此查询
              </el-alert>
            </div>
          </el-form-item>

          <el-divider />

          <!-- 查询预览 -->
          <el-form-item label="查询预览">
            <div class="query-preview">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="包含字段">
                  {{ selectedColumns.length }} 个
                </el-descriptions-item>
                <el-descriptions-item label="筛选条件">
                  {{ conditions.length }} 个
                </el-descriptions-item>
                <el-descriptions-item label="排序规则" :span="2">
                  {{ orderBy.length }} 个
                </el-descriptions-item>
              </el-descriptions>

              <div v-if="selectedColumns.length > 0" class="preview-section mt-3">
                <h5>选中的字段：</h5>
                <el-tag
                  v-for="col in selectedColumns"
                  :key="col"
                  size="small"
                  class="mr-1 mb-1"
                >
                  {{ getColumnTitle(col) }}
                </el-tag>
              </div>

              <div v-if="conditions.length > 0" class="preview-section mt-3">
                <h5>筛选条件：</h5>
                <div
                  v-for="(cond, idx) in conditions"
                  :key="idx"
                  class="condition-preview"
                >
                  <el-tag type="warning" size="small">
                    {{ getColumnTitle(cond.field) }}
                    {{ cond.op }}
                    <template v-if="cond.defaultValue">
                      {{ cond.defaultValue }}
                    </template>
                    ({{ cond.paramType === 'fixed' ? '固定' : cond.paramType === 'single' ? '单选' : '多选' }})
                  </el-tag>
                </div>
              </div>
            </div>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button
          v-if="currentStep > 0"
          @click="prevStep"
        >
          上一步
        </el-button>
        <el-button
          v-if="currentStep < 2"
          type="primary"
          @click="nextStep"
          :disabled="!canProceed"
        >
          下一步
        </el-button>
        <el-button
          v-if="currentStep === 2"
          type="primary"
          @click="handleSave"
          :loading="saving"
        >
          保存查询
        </el-button>
      </div>
    </template>
  </el-dialog>

  <!-- 选项管理对话框 -->
  <OptionManagerDialog
    v-model="optionManagerVisible"
    :condition="currentEditingCondition"
    @save="handleOptionsSaved"
  />
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Setting, Search, User, OfficeBuilding, Histogram } from '@element-plus/icons-vue'
import { saveQuery } from '@/api/savedQuery'
import type { SaveQueryRequest, SavedQueryDef } from '@/api/savedQuery'
import type { ColumnSchema, SliceRequestDef, OrderRequestDef } from '@/types'
import OptionManagerDialog from './OptionManagerDialog.vue'

interface ConditionConfig extends SliceRequestDef {
  paramType: 'fixed' | 'single' | 'multiple'
  label?: string
  options?: Array<{ label: string; value: any }>
  defaultValue?: any
}

interface Props {
  modelValue: boolean
  model: string
  businessId?: string
  availableColumns: ColumnSchema[]  // 所有可用列
  currentColumns?: string[]         // 当前选中的列（可选）
  currentSlice?: SliceRequestDef[]  // 当前筛选条件（可选）
  orderBy?: OrderRequestDef[]       // 当前排序（可选）
}

const props = withDefaults(defineProps<Props>(), {
  currentColumns: () => [],
  currentSlice: () => [],
  orderBy: () => []
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'saved': [query: SavedQueryDef]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 步骤控制
const currentStep = ref(0)
const saving = ref(false)

// 第一步：选择列
const columnSearchKeyword = ref('')
const selectedColumns = ref<string[]>([])
const selectAllColumns = ref(false)

const filteredColumns = computed(() => {
  if (!columnSearchKeyword.value) return props.availableColumns
  const kw = columnSearchKeyword.value.toLowerCase()
  return props.availableColumns.filter(col =>
    col.name.toLowerCase().includes(kw) ||
    col.title?.toLowerCase().includes(kw)
  )
})

const isColumnsIndeterminate = computed(() => {
  const len = selectedColumns.value.length
  return len > 0 && len < props.availableColumns.length
})

function handleSelectAllColumns(val: boolean) {
  if (val) {
    selectedColumns.value = props.availableColumns.map(c => c.name)
  } else {
    selectedColumns.value = []
  }
}

function handleInvertColumnSelection() {
  const all = new Set(props.availableColumns.map(c => c.name))
  const current = new Set(selectedColumns.value)
  selectedColumns.value = Array.from(all).filter(f => !current.has(f))
}

watch(selectedColumns, (val) => {
  selectAllColumns.value = val.length === props.availableColumns.length
})

// 第二步：设置查询条件
const conditions = ref<ConditionConfig[]>([])
const optionManagerVisible = ref(false)
const currentEditingCondition = ref<ConditionConfig | null>(null)

function addCondition() {
  conditions.value.push({
    field: '',
    op: '=',
    value: undefined,
    paramType: 'fixed',
    options: []
  })
}

function removeCondition(index: number) {
  conditions.value.splice(index, 1)
}

function handleFieldChange(cond: ConditionConfig) {
  // 字段改变时重置值和选项
  cond.value = undefined
  cond.defaultValue = undefined
  cond.options = []
}

function openOptionManager(cond: ConditionConfig) {
  currentEditingCondition.value = cond
  optionManagerVisible.value = true
}

function handleOptionsSaved(options: Array<{ label: string; value: any }>) {
  if (currentEditingCondition.value) {
    currentEditingCondition.value.options = options
  }
}

// 第三步：命名和确认
const formRef = ref()
const form = ref({
  title: '',
  description: '',
  visibility: 'PRIVATE' as 'PRIVATE' | 'DEPARTMENT' | 'TENANT'
})

const rules = {
  title: [
    { required: true, message: '请输入查询名称', trigger: 'blur' },
    { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
  ]
}

function getColumnTitle(field: string) {
  const col = props.availableColumns.find(c => c.name === field)
  return col?.title || field
}

// 步骤导航
const canProceed = computed(() => {
  if (currentStep.value === 0) {
    return selectedColumns.value.length > 0
  }
  if (currentStep.value === 1) {
    // 检查所有条件是否填写完整
    return conditions.value.every(c =>
      c.field &&
      c.op &&
      (c.paramType === 'fixed' ? true : (c.options && c.options.length > 0))
    )
  }
  return true
})

function nextStep() {
  if (!canProceed.value) {
    ElMessage.warning('请完成当前步骤的必填项')
    return
  }
  if (currentStep.value < 2) {
    currentStep.value++
  }
}

function prevStep() {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

async function handleSave() {
  if (!formRef.value) return

  await formRef.value.validate()

  saving.value = true
  try {
    // 构建 slice（筛选条件），使用 defaultValue 作为 value
    const slice: SliceRequestDef[] = conditions.value.map(c => ({
      field: c.field,
      op: c.op,
      value: c.defaultValue
    }))

    const request: SaveQueryRequest = {
      businessId: props.businessId,
      model: props.model,
      title: form.value.title,
      description: form.value.description,
      columns: selectedColumns.value,
      slice,
      orderBy: props.orderBy,
      visibility: form.value.visibility
    }

    const result = await saveQuery(request)

    ElMessage.success('查询保存成功')
    emit('saved', result)
    visible.value = false
    handleClose()
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function handleClose() {
  currentStep.value = 0
  selectedColumns.value = []
  conditions.value = []
  form.value = {
    title: '',
    description: '',
    visibility: 'PRIVATE'
  }
  formRef.value?.clearValidate()
}

// 初始化：从 currentColumns 和 currentSlice 加载初始值
watch(visible, (val) => {
  if (val && props.currentColumns.length > 0) {
    selectedColumns.value = [...props.currentColumns]
  }
  if (val && props.currentSlice.length > 0) {
    conditions.value = props.currentSlice.map(s => ({
      ...s,
      paramType: 'fixed',
      defaultValue: s.value,
      options: []
    }))
  }
})
</script>

<style scoped>
.mb-6 {
  margin-bottom: 24px;
}

.mb-3 {
  margin-bottom: 12px;
}

.mb-1 {
  margin-bottom: 4px;
}

.mr-1 {
  margin-right: 4px;
}

.ml-2 {
  margin-left: 8px;
}

.mt-3 {
  margin-top: 12px;
}

.step-content {
  min-height: 450px;
  padding: 20px 0;
}

.step-panel {
  animation: fadeIn 0.3s;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateX(20px); }
  to { opacity: 1; transform: translateX(0); }
}

.step-title h3 {
  margin: 0 0 8px 0;
  font-size: 16px;
  color: #303133;
}

.step-desc {
  margin: 0 0 20px 0;
  font-size: 13px;
  color: #909399;
}

.selection-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.column-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.column-item {
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  transition: all 0.2s;
}

.column-item:hover {
  border-color: #409eff;
  background: #f5f7fa;
}

.column-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.column-title {
  font-weight: 500;
  color: #303133;
}

.condition-card {
  position: relative;
}

.condition-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.condition-index {
  font-weight: 600;
  color: #409eff;
}

.param-options {
  display: flex;
  gap: 8px;
}

.naming-form {
  max-width: 600px;
  margin: 0 auto;
}

.visibility-hint {
  margin-top: 12px;
}

.query-preview {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
}

.preview-section h5 {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: #606266;
}

.condition-preview {
  margin-bottom: 8px;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
}

.empty-hint {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 60px 20px;
}
</style>
