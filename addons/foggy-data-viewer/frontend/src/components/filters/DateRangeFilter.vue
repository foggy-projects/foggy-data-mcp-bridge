<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { SliceRequestDef } from '@/types'

interface Props {
  field: string
  modelValue?: SliceRequestDef[] | null
  format?: string
  showTime?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  format: 'YYYY-MM-DD',
  showTime: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: SliceRequestDef[] | null): void
}>()

// 日期范围值 [开始, 结束]
const dateRange = ref<[Date, Date] | null>(null)

// 是否显示时间选择
const isDatetime = computed(() => props.showTime || props.format?.includes('HH'))

// 日期格式
const dateFormat = computed(() => {
  if (isDatetime.value) {
    return 'YYYY-MM-DD HH:mm'
  }
  return 'YYYY-MM-DD'
})

// 从 modelValue 初始化
watch(() => props.modelValue, (slices) => {
  if (!slices || slices.length === 0) {
    dateRange.value = null
    return
  }

  // 从 slice 中解析日期范围
  // 支持 [) 操作符或两个 >= <= 条件
  const rangeSlice = slices.find(s => s.op === '[)' || s.op === '[]')
  if (rangeSlice && Array.isArray(rangeSlice.value)) {
    const [start, end] = rangeSlice.value as [string, string]
    dateRange.value = [
      new Date(start.replace(' ', 'T')),
      new Date(end.replace(' ', 'T'))
    ]
  } else {
    // 尝试从 >= 和 <= 条件解析
    const gteSlice = slices.find(s => s.op === '>=')
    const lteSlice = slices.find(s => s.op === '<=' || s.op === '<')
    if (gteSlice || lteSlice) {
      const start = gteSlice ? new Date(String(gteSlice.value).replace(' ', 'T')) : null
      const end = lteSlice ? new Date(String(lteSlice.value).replace(' ', 'T')) : null
      if (start && end) {
        dateRange.value = [start, end]
      } else if (start) {
        dateRange.value = [start, start]
      } else if (end) {
        dateRange.value = [end, end]
      }
    } else {
      dateRange.value = null
    }
  }
}, { immediate: true })

function formatDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  if (isDatetime.value) {
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    const seconds = String(date.getSeconds()).padStart(2, '0')
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
  }

  return `${year}-${month}-${day}`
}

function handleChange(val: [Date, Date] | null) {
  if (!val || val.length !== 2) {
    emit('update:modelValue', null)
    return
  }

  const [start, end] = val
  const startVal = formatDate(start)
  // 对于日期，结束日期使用 < 下一天来实现左闭右开
  let endVal: string
  if (isDatetime.value) {
    endVal = formatDate(end)
  } else {
    // 日期范围：结束日期+1天，使用 [) 左闭右开
    const nextDay = new Date(end)
    nextDay.setDate(nextDay.getDate() + 1)
    endVal = formatDate(nextDay)
  }

  // 生成 DSL slice: { field, op: "[)", value: [start, end] }
  emit('update:modelValue', [{
    field: props.field,
    op: '[)',
    value: [startVal, endVal]
  }])
}
</script>

<template>
  <div class="filter-date-range">
    <el-date-picker
      v-model="dateRange"
      :type="isDatetime ? 'datetimerange' : 'daterange'"
      :format="dateFormat"
      range-separator="~"
      start-placeholder="开始"
      end-placeholder="结束"
      size="small"
      :clearable="true"
      :editable="false"
      @change="handleChange"
    />
  </div>
</template>

<style scoped>
.filter-date-range {
  width: 100%;
}

.filter-date-range :deep(.el-date-editor) {
  width: 100% !important;
  max-width: 100%;
}

.filter-date-range :deep(.el-range-input) {
  font-size: 11px;
}

.filter-date-range :deep(.el-range-separator) {
  font-size: 11px;
  padding: 0 2px;
}

.filter-date-range :deep(.el-input__wrapper) {
  padding: 0 6px;
  height: 26px;
  box-sizing: border-box;
}

.filter-date-range :deep(.el-date-editor) {
  height: 26px;
}
</style>
