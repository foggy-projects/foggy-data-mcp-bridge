<script setup lang="ts">
import { computed } from 'vue'
import type { SliceRequestDef } from '@/types'
import { isRelativeDateValue, relativeDateOptions, type RelativeDateRange } from '@/utils/customQuery'
import DateRangeFilter from './DateRangeFilter.vue'

const props = defineProps<{ condition: SliceRequestDef; showTime?: boolean }>()
const emit = defineEmits<{ (event: 'update:condition', condition: SliceRequestDef): void }>()
const relative = computed(() => isRelativeDateValue(props.condition.value) ? props.condition.value.$relativeDate : '')
const isRange = computed(() => ['[]', '[)'].includes(props.condition.op))
function setRelative(value: string) {
  emit('update:condition', {
    ...props.condition, op: '[)',
    value: value ? { $relativeDate: value as RelativeDateRange, dateTime: !!props.showTime } : undefined
  })
}
</script>

<template>
  <div class="preset-date-value">
    <el-select v-if="isRange" :model-value="relative" size="small" clearable aria-label="日期范围方式" @change="setRelative" @clear="setRelative('')">
      <el-option label="自定义日期" value="" />
      <el-option v-for="option in relativeDateOptions" :key="option.value" :label="option.label" :value="option.value" />
    </el-select>
    <DateRangeFilter
      v-if="isRange && !relative"
      :field="condition.field"
      :model-value="[condition]"
      :show-time="showTime"
      @update:model-value="value => emit('update:condition', value?.[0] ?? { ...condition, value: undefined })"
    />
    <el-date-picker
      v-else-if="!isRange"
      :model-value="condition.value"
      :type="showTime ? 'datetime' : 'date'"
      :value-format="showTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'"
      size="small"
      clearable
      @update:model-value="value => emit('update:condition', { ...condition, value })"
    />
  </div>
</template>

<style scoped>
.preset-date-value { display: grid; gap: 6px; flex: 1; min-width: 180px; }
</style>
