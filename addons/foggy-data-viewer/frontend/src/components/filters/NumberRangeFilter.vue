<script setup lang="ts">
import { ref, watch } from 'vue'
import type { SliceRequestDef } from '@/types'

interface Props {
  field: string
  modelValue?: SliceRequestDef[] | null
  placeholderMin?: string
  placeholderMax?: string
}

const props = withDefaults(defineProps<Props>(), {
  placeholderMin: '最小',
  placeholderMax: '最大'
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: SliceRequestDef[] | null): void
}>()

const minValue = ref<string>('')
const maxValue = ref<string>('')

// 从 modelValue 初始化
watch(() => props.modelValue, (slices) => {
  if (!slices || slices.length === 0) {
    minValue.value = ''
    maxValue.value = ''
    return
  }

  // 解析 [] 范围操作符
  const rangeSlice = slices.find(s => s.op === '[]' || s.op === '[)')
  if (rangeSlice && Array.isArray(rangeSlice.value)) {
    const [min, max] = rangeSlice.value as [number, number]
    minValue.value = min != null ? String(min) : ''
    maxValue.value = max != null ? String(max) : ''
  } else {
    // 解析 >= 和 <= 条件
    const gteSlice = slices.find(s => s.op === '>=')
    const lteSlice = slices.find(s => s.op === '<=')
    minValue.value = gteSlice?.value != null ? String(gteSlice.value) : ''
    maxValue.value = lteSlice?.value != null ? String(lteSlice.value) : ''
  }
}, { immediate: true })

function emitChange() {
  const min = minValue.value.trim()
  const max = maxValue.value.trim()

  if (!min && !max) {
    emit('update:modelValue', null)
    return
  }

  const minNum = min ? parseFloat(min) : null
  const maxNum = max ? parseFloat(max) : null

  // 验证数字
  if (min && isNaN(minNum!)) return
  if (max && isNaN(maxNum!)) return

  // 生成 DSL slice
  if (minNum !== null && maxNum !== null) {
    // 两个都有值：使用 [] 闭区间
    emit('update:modelValue', [{
      field: props.field,
      op: '[]',
      value: [minNum, maxNum]
    }])
  } else if (minNum !== null) {
    // 只有最小值：>=
    emit('update:modelValue', [{
      field: props.field,
      op: '>=',
      value: minNum
    }])
  } else if (maxNum !== null) {
    // 只有最大值：<=
    emit('update:modelValue', [{
      field: props.field,
      op: '<=',
      value: maxNum
    }])
  }
}

function clear() {
  minValue.value = ''
  maxValue.value = ''
  emit('update:modelValue', null)
}
</script>

<template>
  <div class="filter-number-range">
    <input
      v-model="minValue"
      type="text"
      :placeholder="placeholderMin"
      @change="emitChange"
      @keyup.enter="emitChange"
    />
    <span class="separator">-</span>
    <input
      v-model="maxValue"
      type="text"
      :placeholder="placeholderMax"
      @change="emitChange"
      @keyup.enter="emitChange"
    />
    <span v-if="minValue || maxValue" class="clear-btn" @click="clear">×</span>
  </div>
</template>

<style scoped>
.filter-number-range {
  display: flex;
  align-items: center;
  gap: 4px;
  position: relative;
}

.filter-number-range input {
  flex: 1;
  min-width: 0;
  width: 50px;
  height: 26px;
  padding: 0 6px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 12px;
  line-height: 26px;
  outline: none;
  text-align: right;
  transition: border-color 0.2s;
}

.filter-number-range input:focus {
  border-color: #409eff;
}

.separator {
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
}

.clear-btn {
  position: absolute;
  right: -16px;
  color: #c0c4cc;
  cursor: pointer;
  font-size: 14px;
}

.clear-btn:hover {
  color: #909399;
}
</style>
