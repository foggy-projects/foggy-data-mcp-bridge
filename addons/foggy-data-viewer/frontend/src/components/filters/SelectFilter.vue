<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import type { SliceRequestDef, FilterOption } from '@/types'

interface Props {
  field: string
  modelValue?: SliceRequestDef[] | null
  options: FilterOption[]
  placeholder?: string
  loading?: boolean
  maxDisplayItems?: number
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '请选择...',
  loading: false,
  maxDisplayItems: 100
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: SliceRequestDef[] | null): void
}>()

const showDropdown = ref(false)
const isMulti = ref(false)
const searchText = ref('')
const selectedValues = ref<Set<string | number>>(new Set())
const containerRef = ref<HTMLElement>()
const highlightIndex = ref(0)
const searchInputRef = ref<HTMLInputElement>()

// 从 modelValue 初始化
watch(() => props.modelValue, (slices) => {
  selectedValues.value.clear()
  if (!slices || slices.length === 0) return

  const slice = slices[0]
  if (slice.op === 'in' && Array.isArray(slice.value)) {
    isMulti.value = true
    ;(slice.value as (string | number)[]).forEach(v => selectedValues.value.add(v))
  } else if (slice.op === '=') {
    isMulti.value = false
    selectedValues.value.add(slice.value as string | number)
  }
}, { immediate: true })

// 过滤后的选项
const filteredOptions = computed(() => {
  if (!searchText.value.trim()) {
    return props.options
  }
  const keyword = searchText.value.toLowerCase()
  return props.options.filter(opt =>
    opt.label.toLowerCase().includes(keyword) ||
    String(opt.value).toLowerCase().includes(keyword)
  )
})

// 显示的选项（限制数量）
const displayOptions = computed(() => {
  return filteredOptions.value.slice(0, props.maxDisplayItems)
})

// 是否有更多选项
const hasMore = computed(() => {
  return filteredOptions.value.length > props.maxDisplayItems
})

// 显示文本
const displayText = computed(() => {
  if (selectedValues.value.size === 0) {
    return ''
  }
  const selected = props.options.filter(opt => selectedValues.value.has(opt.value))
  if (selected.length === 0) {
    return Array.from(selectedValues.value).join(', ')
  }
  if (selected.length <= 2) {
    return selected.map(s => s.label).join(', ')
  }
  return `已选 ${selected.length} 项`
})

function toggleDropdown() {
  showDropdown.value = !showDropdown.value
  if (showDropdown.value) {
    searchText.value = ''
    highlightIndex.value = 0
    // 聚焦搜索框
    setTimeout(() => searchInputRef.value?.focus(), 0)
  }
}

function toggleMultiMode() {
  isMulti.value = !isMulti.value
  if (!isMulti.value && selectedValues.value.size > 1) {
    // 切换到单选时，只保留第一个
    const first = selectedValues.value.values().next().value
    selectedValues.value.clear()
    if (first !== undefined) {
      selectedValues.value.add(first)
    }
  }
  emitChange()
}

function isSelected(opt: FilterOption): boolean {
  return selectedValues.value.has(opt.value)
}

function selectItem(opt: FilterOption) {
  if (isMulti.value) {
    if (selectedValues.value.has(opt.value)) {
      selectedValues.value.delete(opt.value)
    } else {
      selectedValues.value.add(opt.value)
    }
    emitChange()
  } else {
    selectedValues.value.clear()
    selectedValues.value.add(opt.value)
    emitChange()
    showDropdown.value = false
  }
}

function emitChange() {
  if (selectedValues.value.size === 0) {
    emit('update:modelValue', null)
    return
  }

  // 生成 DSL slice
  if (isMulti.value || selectedValues.value.size > 1) {
    emit('update:modelValue', [{
      field: props.field,
      op: 'in',
      value: Array.from(selectedValues.value)
    }])
  } else {
    emit('update:modelValue', [{
      field: props.field,
      op: '=',
      value: selectedValues.value.values().next().value
    }])
  }
}

function clear() {
  selectedValues.value.clear()
  searchText.value = ''
  showDropdown.value = false
  emit('update:modelValue', null)
}

function onSearchInput() {
  highlightIndex.value = 0
}

function onKeydown(e: KeyboardEvent) {
  if (!showDropdown.value) return

  const options = displayOptions.value
  if (options.length === 0) return

  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault()
      highlightIndex.value = (highlightIndex.value + 1) % options.length
      break
    case 'ArrowUp':
      e.preventDefault()
      highlightIndex.value = (highlightIndex.value - 1 + options.length) % options.length
      break
    case 'Enter':
      e.preventDefault()
      if (options[highlightIndex.value]) {
        selectItem(options[highlightIndex.value])
      }
      break
    case 'Escape':
      showDropdown.value = false
      break
  }
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
  <div ref="containerRef" class="filter-select">
    <div class="select-input" @click="toggleDropdown">
      <span v-if="displayText" class="selected-text">{{ displayText }}</span>
      <span v-else class="placeholder-text">{{ placeholder }}</span>
      <span class="toggle-multi" @click.stop="toggleMultiMode" :title="isMulti ? '切换单选' : '切换多选'">
        {{ isMulti ? '多' : '单' }}
      </span>
      <span v-if="displayText" class="clear-btn" @click.stop="clear">×</span>
    </div>

    <div v-if="showDropdown" class="filter-dropdown">
      <div class="search-box">
        <input
          ref="searchInputRef"
          v-model="searchText"
          type="text"
          placeholder="搜索..."
          @click.stop
          @input="onSearchInput"
          @keydown="onKeydown"
        />
      </div>

      <div v-if="loading" class="loading-hint">
        加载中...
      </div>

      <template v-else>
        <div
          v-for="(opt, index) in displayOptions"
          :key="String(opt.value)"
          class="filter-option"
          :class="{ selected: isSelected(opt), highlighted: index === highlightIndex }"
          @click="selectItem(opt)"
          @mouseenter="highlightIndex = index"
        >
          <input
            v-if="isMulti"
            type="checkbox"
            :checked="isSelected(opt)"
            @click.stop
          />
          <span class="option-label">{{ opt.label }}</span>
        </div>

        <div v-if="displayOptions.length === 0" class="no-data">
          无匹配数据
        </div>

        <div v-if="hasMore" class="more-hint">
          还有 {{ filteredOptions.length - maxDisplayItems }} 条，请输入关键词搜索
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.filter-select {
  position: relative;
  width: 100%;
}

.select-input {
  display: flex;
  align-items: center;
  padding: 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  background: white;
  height: 26px;
  gap: 4px;
}

.select-input:hover {
  border-color: #c0c4cc;
}

.selected-text {
  flex: 1;
  font-size: 12px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.placeholder-text {
  flex: 1;
  font-size: 12px;
  color: #c0c4cc;
}

.toggle-multi {
  padding: 1px 4px;
  font-size: 9px;
  color: white;
  background: #409eff;
  border-radius: 2px;
  cursor: pointer;
  line-height: 1.2;
}

.toggle-multi:hover {
  background: #337ecc;
}

.clear-btn {
  color: #c0c4cc;
  cursor: pointer;
  font-size: 12px;
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
  max-height: 280px;
  display: flex;
  flex-direction: column;
}

.search-box {
  padding: 8px;
  border-bottom: 1px solid #e4e7ed;
}

.search-box input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 12px;
  outline: none;
}

.search-box input:focus {
  border-color: #409eff;
}

.filter-option {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  transition: background-color 0.2s;
  gap: 6px;
}

.filter-option:hover,
.filter-option.highlighted {
  background-color: #f5f7fa;
}

.filter-option.selected {
  color: #409eff;
  background-color: #ecf5ff;
}

.filter-option input[type="checkbox"] {
  margin: 0;
}

.option-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.loading-hint,
.no-data,
.more-hint {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: #909399;
}

.more-hint {
  border-top: 1px solid #e4e7ed;
  background: #f5f7fa;
}
</style>
