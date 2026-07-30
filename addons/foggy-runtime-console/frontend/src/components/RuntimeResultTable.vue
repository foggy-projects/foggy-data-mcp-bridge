<script setup lang="ts">
import { computed } from 'vue'
import { DataTable, type EnhancedColumnSchema } from 'foggy-data-viewer'

const props = withDefaults(defineProps<{
  rows: Record<string, unknown>[]
  loading?: boolean
}>(), {
  loading: false
})

function columnType(values: unknown[]): string {
  const sample = values.find(value => value !== null && value !== undefined)
  if (typeof sample === 'number') return 'NUMBER'
  if (typeof sample === 'boolean') return 'BOOLEAN'
  return 'STRING'
}

const columns = computed<EnhancedColumnSchema[]>(() => {
  const keys = Array.from(new Set(props.rows.flatMap(row => Object.keys(row))))
  return keys.map(key => ({
    name: key,
    title: key,
    type: columnType(props.rows.map(row => row[key])),
    minWidth: 130,
    filterable: false,
    copyable: true
  }))
})
</script>

<template>
  <div class="console-table-host">
    <DataTable
      v-if="rows.length || loading"
      :columns="columns"
      :data="rows"
      :total="rows.length"
      :loading="loading"
      :show-filters="false"
      :show-pager="false"
      density="compact"
    />
    <div v-else class="empty-state">
      <div>
        <strong>暂无结果</strong>
        执行操作后，结构化数据会显示在这里。
      </div>
    </div>
  </div>
</template>
