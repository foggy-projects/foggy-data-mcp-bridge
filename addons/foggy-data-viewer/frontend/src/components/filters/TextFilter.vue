<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import type { SliceRequestDef } from '@/types'

interface Props {
  field: string
  modelValue?: SliceRequestDef[] | null
  placeholder?: string
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '搜索...'
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: SliceRequestDef[] | null): void
}>()

const inputValue = ref('')
const showDropdown = ref(false)
const containerRef = ref<HTMLElement>()
const highlightIndex = ref(0)

// 从 modelValue 初始化
watch(() => props.modelValue, (slices) => {
  if (!slices || slices.length === 0) {
    inputValue.value = ''
    return
  }

  const slice = slices[0]
  if (slice.op === 'in' && Array.isArray(slice.value)) {
    inputValue.value = (slice.value as string[]).join(', ')
  } else {
    inputValue.value = String(slice.value || '').replace(/%/g, '')
  }
}, { immediate: true })

// 操作符选项
const operatorOptions = computed(() => {
  const val = inputValue.value.trim()
  if (!val) return []

  return [
    { op: '=', label: `等于：${val}` },
    { op: 'right_like', label: `左匹配：${val}***` },
    { op: 'in', label: `批量查找：${val}` }
  ]
})

function onInput() {
  if (inputValue.value.trim()) {
    showDropdown.value = true
    highlightIndex.value = 0
  } else {
    showDropdown.value = false
    emit('update:modelValue', null)
  }
}

function selectOperator(op: string) {
  const val = inputValue.value.trim()
  if (!val) return

  let slice: SliceRequestDef

  if (op === 'in') {
    // 批量查找：按分隔符分割
    const values = val.split(/[,，\s]+/).filter(v => v.trim())
    if (values.length === 1) {
      // 只有一个值，改用 =
      slice = { field: props.field, op: '=', value: values[0] }
    } else {
      slice = { field: props.field, op: 'in', value: values }
    }
  } else {
    slice = { field: props.field, op, value: val }
  }

  emit('update:modelValue', [slice])
  showDropdown.value = false
}

function onKeydown(e: KeyboardEvent) {
  if (!showDropdown.value || operatorOptions.value.length === 0) {
    if (e.key === 'Enter' && inputValue.value.trim()) {
      showDropdown.value = true
      highlightIndex.value = 0
    }
    return
  }

  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault()
      highlightIndex.value = (highlightIndex.value + 1) % operatorOptions.value.length
      break
    case 'ArrowUp':
      e.preventDefault()
      highlightIndex.value = (highlightIndex.value - 1 + operatorOptions.value.length) % operatorOptions.value.length
      break
    case 'Enter':
      e.preventDefault()
      selectOperator(operatorOptions.value[highlightIndex.value].op)
      break
    case 'Escape':
      showDropdown.value = false
      break
  }
}

function clear() {
  inputValue.value = ''
  showDropdown.value = false
  emit('update:modelValue', null)
}

// 点击外部关闭下拉
function handleClickOutside(e: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(e.target as Node)) {
    showDropdown.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div ref="containerRef" class="filter-text">
    <div class="input-wrapper">
      <input
        v-model="inputValue"
        type="text"
        :placeholder="placeholder"
        @input="onInput"
        @keydown="onKeydown"
        @focus="inputValue.trim() && (showDropdown = true)"
      />
      <span v-if="inputValue" class="clear-btn" @click.stop="clear">×</span>
    </div>

    <div v-if="showDropdown && operatorOptions.length > 0" class="filter-dropdown">
      <div
        v-for="(opt, index) in operatorOptions"
        :key="opt.op"
        class="filter-option"
        :class="{ highlighted: index === highlightIndex }"
        @click="selectOperator(opt.op)"
        @mouseenter="highlightIndex = index"
      >
        {{ opt.label }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-text {
  position: relative;
  width: 100%;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-wrapper input {
  width: 100%;
  height: 26px;
  padding: 0 24px 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 12px;
  line-height: 26px;
  outline: none;
  transition: border-color 0.2s;
}

.input-wrapper input:focus {
  border-color: #409eff;
}

.clear-btn {
  position: absolute;
  right: 6px;
  color: #c0c4cc;
  cursor: pointer;
  font-size: 14px;
}

.clear-btn:hover {
  color: #909399;
}

.filter-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  z-index: 9999;
  max-height: 200px;
  overflow-y: auto;
}

.filter-option {
  padding: 8px 12px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  transition: background-color 0.2s;
}

.filter-option:hover,
.filter-option.highlighted {
  background-color: #f5f7fa;
}
</style>
