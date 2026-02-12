<template>
  <el-dialog
    v-model="visible"
    title="配置选项"
    width="500px"
  >
    <div class="option-manager">
      <el-button :icon="Plus" @click="addOption" class="mb-3">
        添加选项
      </el-button>

      <el-table :data="options" border>
        <el-table-column label="显示文本" width="200">
          <template #default="{ row }">
            <el-input v-model="row.label" placeholder="显示文本" />
          </template>
        </el-table-column>
        <el-table-column label="值">
          <template #default="{ row }">
            <el-input v-model="row.value" placeholder="实际值" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button
              :icon="Delete"
              link
              type="danger"
              @click="removeOption($index)"
            />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleSave">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Plus, Delete } from '@element-plus/icons-vue'

interface OptionItem {
  label: string
  value: any
}

interface Props {
  modelValue: boolean
  condition?: {
    options?: OptionItem[]
  }
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'save': [options: OptionItem[]]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const options = ref<OptionItem[]>([])

watch(() => props.condition, (val) => {
  if (val?.options) {
    options.value = JSON.parse(JSON.stringify(val.options))
  } else {
    options.value = []
  }
}, { immediate: true })

function addOption() {
  options.value.push({ label: '', value: '' })
}

function removeOption(index: number) {
  options.value.splice(index, 1)
}

function handleSave() {
  emit('save', options.value.filter(o => o.label && o.value))
  visible.value = false
}
</script>

<style scoped>
.mb-3 {
  margin-bottom: 12px;
}
</style>
