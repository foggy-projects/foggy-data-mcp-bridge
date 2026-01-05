<script setup lang="ts">
import { ref, watch } from 'vue'
import type { SliceRequestDef } from '@/types'

interface Props {
  field: string
  modelValue?: SliceRequestDef[] | null
  trueLabel?: string
  falseLabel?: string
}

const props = withDefaults(defineProps<Props>(), {
  trueLabel: '是',
  falseLabel: '否'
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: SliceRequestDef[] | null): void
}>()

const selectedValue = ref<boolean | null>(null)

// 从 modelValue 初始化
watch(() => props.modelValue, (slices) => {
  if (!slices || slices.length === 0) {
    selectedValue.value = null
    return
  }

  const slice = slices[0]
  if (slice.op === '=') {
    selectedValue.value = slice.value === true || slice.value === 'true' || slice.value === 1
  } else {
    selectedValue.value = null
  }
}, { immediate: true })

function select(val: boolean | null) {
  selectedValue.value = val
  if (val === null) {
    emit('update:modelValue', null)
  } else {
    emit('update:modelValue', [{
      field: props.field,
      op: '=',
      value: val
    }])
  }
}
</script>

<template>
  <div class="filter-bool">
    <button
      :class="{ active: selectedValue === null }"
      @click="select(null)"
    >
      全部
    </button>
    <button
      :class="{ active: selectedValue === true }"
      @click="select(true)"
    >
      {{ trueLabel }}
    </button>
    <button
      :class="{ active: selectedValue === false }"
      @click="select(false)"
    >
      {{ falseLabel }}
    </button>
  </div>
</template>

<style scoped>
.filter-bool {
  display: flex;
  gap: 4px;
}

.filter-bool button {
  height: 26px;
  padding: 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: white;
  font-size: 11px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-bool button:hover {
  border-color: #409eff;
  color: #409eff;
}

.filter-bool button.active {
  background: #409eff;
  border-color: #409eff;
  color: white;
}
</style>
