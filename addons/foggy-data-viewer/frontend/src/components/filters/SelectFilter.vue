<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import type { SliceRequestDef, FilterOption, MemberQueryRequest, MemberQueryResponse, MemberOption } from '@/types'

/**
 * SelectFilter - 支持静态选项和远程维度成员两种模式
 *
 * 模式 1（静态）：传入 options 数组，本地搜索
 * 模式 2（远程成员）：传入 remoteLoader，keyword debounce 远程搜索、分页、回填
 *
 * 当存在 selectionField 时，DSL slice 使用 selectionField 而非 field（维度成员场景）
 */
interface Props {
  /** 当前字段名（展示字段，如 customer$caption） */
  field: string
  /** DSL slice 使用的值字段（如 customer$id）。不传则使用 field */
  selectionField?: string
  modelValue?: SliceRequestDef[] | null
  /** 静态选项（模式 1） */
  options?: FilterOption[]
  placeholder?: string
  loading?: boolean
  maxDisplayItems?: number
  /** 远程成员加载器（模式 2） */
  remoteLoader?: (request: MemberQueryRequest) => Promise<MemberQueryResponse>
  /** 远程模式所需的 qmModel */
  qmModel?: string
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '请选择...',
  loading: false,
  maxDisplayItems: 100,
  options: () => []
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: SliceRequestDef[] | null): void
}>()

// ── 状态 ──
const showDropdown = ref(false)
const isMulti = ref(false)
const searchText = ref('')
const selectedValues = ref<Set<string | number>>(new Set())
const containerRef = ref<HTMLElement>()
const inputRef = ref<HTMLElement>()
const highlightIndex = ref(0)
const searchInputRef = ref<HTMLInputElement>()

// 下拉框位置（Teleport 到 body 后需要绝对定位）
const dropdownStyle = ref<Record<string, string>>({})

// 远程模式状态
const remoteOptions = ref<FilterOption[]>([])
const remoteLoading = ref(false)
const remoteTotal = ref(0)
const remoteHasMore = ref(false)
const selectedLabels = ref<Map<string | number, string>>(new Map())
let debounceTimer: ReturnType<typeof setTimeout> | null = null

/** 是否远程模式 */
const isRemote = computed(() => !!props.remoteLoader && !!props.qmModel)

/** DSL 输出字段：优先使用 selectionField */
const sliceField = computed(() => props.selectionField || props.field)

// ── 从 modelValue 初始化 ──
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

// ── 选项列表 ──
const effectiveOptions = computed(() => {
  if (isRemote.value) return remoteOptions.value
  return props.options
})

const filteredOptions = computed(() => {
  if (isRemote.value) return effectiveOptions.value // 远程已过滤
  if (!searchText.value.trim()) return effectiveOptions.value
  const keyword = searchText.value.toLowerCase()
  return effectiveOptions.value.filter(opt =>
    opt.label.toLowerCase().includes(keyword) ||
    String(opt.value).toLowerCase().includes(keyword)
  )
})

const displayOptions = computed(() => {
  return filteredOptions.value.slice(0, props.maxDisplayItems)
})

const hasMore = computed(() => {
  if (isRemote.value) return remoteHasMore.value
  return filteredOptions.value.length > props.maxDisplayItems
})

const isLoading = computed(() => {
  if (isRemote.value) return remoteLoading.value
  return props.loading
})

// ── 显示文本 ──
const displayText = computed(() => {
  if (selectedValues.value.size === 0) return ''

  if (isRemote.value) {
    const labels: string[] = []
    for (const v of selectedValues.value) {
      const label = selectedLabels.value.get(v)
      if (label) labels.push(label)
      else labels.push(String(v))
    }
    if (labels.length <= 2) return labels.join(', ')
    return `已选 ${labels.length} 项`
  }

  const selected = props.options.filter(opt => selectedValues.value.has(opt.value))
  if (selected.length === 0) return Array.from(selectedValues.value).join(', ')
  if (selected.length <= 2) return selected.map(s => s.label).join(', ')
  return `已选 ${selected.length} 项`
})

// ── 远程加载 ──
async function loadRemote(keyword: string) {
  if (!props.remoteLoader || !props.qmModel) return

  remoteLoading.value = true
  try {
    const response = await props.remoteLoader({
      qmModel: props.qmModel,
      fieldName: props.field,
      keyword: keyword || undefined,
      start: 0,
      limit: props.maxDisplayItems,
      selectedValues: selectedValues.value.size > 0
        ? Array.from(selectedValues.value)
        : undefined
    })

    remoteOptions.value = response.items.map(toFilterOption)
    remoteTotal.value = response.total
    remoteHasMore.value = response.hasMore ?? false

    // 缓存已选项的 label
    if (response.selectedItems) {
      for (const item of response.selectedItems) {
        selectedLabels.value.set(item.value, item.label)
      }
    }
    for (const item of response.items) {
      selectedLabels.value.set(item.value, item.label)
    }
  } catch (e) {
    console.error('远程成员加载失败:', e)
    remoteOptions.value = []
  } finally {
    remoteLoading.value = false
  }
}

function toFilterOption(item: MemberOption): FilterOption {
  return { value: item.value, label: item.label }
}

function onSearchInput() {
  highlightIndex.value = 0
  if (isRemote.value) {
    // debounce 远程搜索
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
      loadRemote(searchText.value)
    }, 250)
  }
}

// ── 交互 ──
function updateDropdownPosition() {
  if (!inputRef.value) return
  const rect = inputRef.value.getBoundingClientRect()
  dropdownStyle.value = {
    position: 'fixed',
    top: `${rect.bottom + 4}px`,
    left: `${rect.left}px`,
    width: `${rect.width}px`,
    zIndex: '9999'
  }
}

function toggleDropdown() {
  showDropdown.value = !showDropdown.value
  if (showDropdown.value) {
    searchText.value = ''
    highlightIndex.value = 0
    updateDropdownPosition()
    setTimeout(() => searchInputRef.value?.focus(), 0)
    // 远程模式：打开时首次加载
    if (isRemote.value && remoteOptions.value.length === 0) {
      loadRemote('')
    }
  }
}

function toggleMultiMode() {
  isMulti.value = !isMulti.value
  if (!isMulti.value && selectedValues.value.size > 1) {
    const first = selectedValues.value.values().next().value
    selectedValues.value.clear()
    if (first !== undefined) selectedValues.value.add(first)
    emitChange()
  }
}

function isSelected(opt: FilterOption): boolean {
  return selectedValues.value.has(opt.value)
}

function selectItem(opt: FilterOption) {
  // 缓存 label
  selectedLabels.value.set(opt.value, opt.label)

  if (isMulti.value) {
    if (selectedValues.value.has(opt.value)) {
      selectedValues.value.delete(opt.value)
    } else {
      selectedValues.value.add(opt.value)
    }
  } else {
    selectedValues.value.clear()
    selectedValues.value.add(opt.value)
    emitChange()
    showDropdown.value = false
  }
}

function confirmMultiSelect() {
  emitChange()
  showDropdown.value = false
}

function emitChange() {
  if (selectedValues.value.size === 0) {
    emit('update:modelValue', null)
    return
  }

  // DSL slice 使用 sliceField（维度成员场景下为 $id 字段）
  if (isMulti.value || selectedValues.value.size > 1) {
    emit('update:modelValue', [{
      field: sliceField.value,
      op: 'in',
      value: Array.from(selectedValues.value)
    }])
  } else {
    emit('update:modelValue', [{
      field: sliceField.value,
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
      if (options[highlightIndex.value]) selectItem(options[highlightIndex.value])
      break
    case 'Escape':
      showDropdown.value = false
      break
  }
}

const dropdownRef = ref<HTMLElement>()

function handleClickOutside(e: MouseEvent) {
  const target = e.target as Node
  const inContainer = containerRef.value?.contains(target)
  const inDropdown = dropdownRef.value?.contains(target)
  if (!inContainer && !inDropdown) {
    if (showDropdown.value && isMulti.value) emitChange()
    showDropdown.value = false
  }
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<template>
  <div ref="containerRef" class="filter-select">
    <div ref="inputRef" class="select-input" @click="toggleDropdown">
      <span v-if="displayText" class="selected-text">{{ displayText }}</span>
      <span v-else class="placeholder-text">{{ placeholder }}</span>
      <span class="toggle-multi" @click.stop="toggleMultiMode" :title="isMulti ? '切换单选' : '切换多选'">
        {{ isMulti ? '多' : '单' }}
      </span>
      <span v-if="displayText" class="clear-btn" @click.stop="clear">×</span>
    </div>

    <Teleport to="body">
      <div v-if="showDropdown" ref="dropdownRef" class="filter-dropdown" :style="dropdownStyle">
        <div class="search-box">
          <input
            ref="searchInputRef"
            v-model="searchText"
            type="text"
            :placeholder="isRemote ? '输入关键词搜索...' : '搜索...'"
            @click.stop
            @input="onSearchInput"
            @keydown="onKeydown"
          />
        </div>

        <div class="options-container">
          <div v-if="isLoading" class="loading-hint">
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
              {{ isRemote && !searchText ? '打开后自动加载' : '无匹配数据' }}
            </div>
          </template>
        </div>

        <div v-if="hasMore" class="more-hint">
          <template v-if="isRemote">
            共 {{ remoteTotal }} 条，请输入关键词缩小范围
          </template>
          <template v-else>
            还有 {{ filteredOptions.length - maxDisplayItems }} 条，请输入关键词搜索
          </template>
        </div>

        <div v-if="isMulti" class="confirm-bar">
          <span class="selected-count">已选 {{ selectedValues.size }} 项</span>
          <button class="confirm-btn" @click="confirmMultiSelect">确定</button>
        </div>
      </div>
    </Teleport>
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

/* dropdown 样式已移至非 scoped <style> 块（Teleport 到 body 后 scoped 不生效） */
</style>

<!-- Teleport 到 body 的下拉框样式（不能 scoped） -->
<style>
.filter-dropdown {
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  max-height: 280px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.filter-dropdown .search-box {
  flex-shrink: 0;
  padding: 8px;
  border-bottom: 1px solid #e4e7ed;
}

.filter-dropdown .search-box input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 12px;
  outline: none;
  box-sizing: border-box;
}

.filter-dropdown .search-box input:focus {
  border-color: #409eff;
}

.filter-dropdown .options-container {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.filter-dropdown .filter-option {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  transition: background-color 0.2s;
  gap: 6px;
}

.filter-dropdown .filter-option:hover,
.filter-dropdown .filter-option.highlighted {
  background-color: #f5f7fa;
}

.filter-dropdown .filter-option.selected {
  color: #409eff;
  background-color: #ecf5ff;
}

.filter-dropdown .filter-option input[type="checkbox"] {
  margin: 0;
}

.filter-dropdown .option-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-dropdown .loading-hint,
.filter-dropdown .no-data {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: #909399;
}

.filter-dropdown .more-hint {
  flex-shrink: 0;
  padding: 8px 12px;
  text-align: center;
  font-size: 12px;
  color: #909399;
  border-top: 1px solid #e4e7ed;
  background: #f5f7fa;
}

.filter-dropdown .confirm-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-top: 1px solid #e4e7ed;
  background: #fafafa;
}

.filter-dropdown .selected-count {
  font-size: 12px;
  color: #606266;
}

.filter-dropdown .confirm-btn {
  padding: 4px 12px;
  font-size: 12px;
  color: white;
  background: #409eff;
  border: none;
  border-radius: 3px;
  cursor: pointer;
}

.filter-dropdown .confirm-btn:hover {
  background: #337ecc;
}
</style>
