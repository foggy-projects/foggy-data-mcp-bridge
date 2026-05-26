<script setup lang="ts">
import { computed } from 'vue'
import type { PivotAxisField, PivotAxisPage } from '@/types'

interface Props {
  rowAxes?: PivotAxisField[]
  columnAxes?: PivotAxisField[]
  rowPages?: PivotAxisPage[]
  columnPages?: PivotAxisPage[]
}

const props = withDefaults(defineProps<Props>(), {
  rowAxes: () => [],
  columnAxes: () => [],
  rowPages: () => [],
  columnPages: () => []
})

interface AxisPageItem extends PivotAxisPage {
  axis: 'rows' | 'columns'
  title: string
}

const pageItems = computed<AxisPageItem[]>(() => [
  ...buildPageItems('rows', props.rowPages, props.rowAxes),
  ...buildPageItems('columns', props.columnPages, props.columnAxes)
])

function buildPageItems(
  axis: AxisPageItem['axis'],
  pages: PivotAxisPage[],
  axes: PivotAxisField[]
): AxisPageItem[] {
  return pages.map(page => {
    const axisField = axes.find(item => item.field === page.field)
    return {
      ...page,
      axis,
      title: axisField?.title ?? page.field
    }
  })
}

function formatRange(page: PivotAxisPage): string {
  const start = page.offset
  const end = page.offset + page.limit
  const total = page.total === undefined ? '?' : String(page.total)
  return `${start}-${end} / ${total}`
}

function formatScope(page: PivotAxisPage): string {
  return page.pageScope === 'perParent' ? '父级内分页' : '全局轴分页'
}
</script>

<template>
  <section v-if="pageItems.length" class="pivot-axis-pager" aria-label="Pivot axis pages">
    <div
      v-for="page in pageItems"
      :key="`${page.axis}:${page.field}`"
      class="pivot-axis-page"
      :data-axis="page.axis"
      :data-field="page.field"
    >
      <span class="pivot-axis-page-axis">{{ page.axis === 'rows' ? '行轴' : '列轴' }}</span>
      <span class="pivot-axis-page-title">{{ page.title }}</span>
      <span class="pivot-axis-page-range">{{ formatRange(page) }}</span>
      <span class="pivot-axis-page-scope">{{ formatScope(page) }}</span>
      <span v-if="page.hasMore" class="pivot-axis-page-more">还有更多</span>
    </div>
  </section>
</template>

<style scoped>
.pivot-axis-pager {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.pivot-axis-page {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
  padding: 6px 10px;
  font-size: 12px;
  color: #303133;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.pivot-axis-page-axis {
  font-weight: 600;
  color: #409eff;
}

.pivot-axis-page-title {
  max-width: 160px;
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pivot-axis-page-range,
.pivot-axis-page-scope,
.pivot-axis-page-more {
  color: #606266;
}
</style>
