<script setup lang="ts">
import { computed, h, useAttrs } from 'vue'
import type { VxeGridProps } from 'vxe-table'
import type { PivotHeaderNode, PivotMetric, PivotViewModel } from '@/types'
import type { PivotGridColumn } from '@/utils/pivotHeaderTree'
import { buildPivotGridColumns, flattenPivotLeafNodes } from '@/utils/pivotHeaderTree'

defineOptions({
  inheritAttrs: false
})

interface Props {
  viewModel: PivotViewModel
  loading?: boolean
  height?: string | number
  emptyText?: string
  emptyCellText?: string
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  height: '100%',
  emptyText: '暂无透视数据',
  emptyCellText: '-'
})

type PivotGridRenderColumn = Omit<PivotGridColumn, 'children'> & {
  children?: PivotGridRenderColumn[]
  slots?: {
    default: (params: { row: Record<string, unknown> }) => ReturnType<typeof h>
  }
}

const attrs = useAttrs()

const metricByField = computed(() => {
  return new Map(props.viewModel.metrics.map(metric => [metric.field, metric]))
})

const leafNodeByField = computed(() => {
  const entries = flattenPivotLeafNodes(props.viewModel.headerTree)
    .filter((node): node is PivotHeaderNode & { field: string } => Boolean(node.field))
    .map(node => [node.field, node] as const)

  return new Map(entries)
})

const baseColumns = computed(() => buildPivotGridColumns(props.viewModel.headerTree))

const gridColumns = computed<VxeGridProps['columns']>(() => {
  return baseColumns.value.map(column => withCellRenderer(column)) as VxeGridProps['columns']
})

const gridOptions = computed<VxeGridProps>(() => {
  const userProps = Object.keys(attrs)
    .filter(key => !key.startsWith('on'))
    .reduce((acc, key) => ({ ...acc, [key]: attrs[key] }), {})

  const defaultOptions: VxeGridProps = {
    border: true,
    stripe: true,
    showOverflow: true,
    height: props.height,
    loading: props.loading,
    columnConfig: {
      resizable: true
    },
    rowConfig: {
      isHover: true
    },
    pagerConfig: {
      enabled: false
    },
    columns: gridColumns.value,
    data: props.viewModel.items
  }

  return { ...defaultOptions, ...userProps }
})

function withCellRenderer(column: PivotGridColumn): PivotGridRenderColumn {
  if (column.children?.length) {
    return {
      ...column,
      children: column.children.map(child => withCellRenderer(child))
    }
  }

  if (!column.field) {
    return column
  }

  return {
    ...column,
    slots: {
      default: ({ row }: { row: Record<string, unknown> }) => {
        return h('span', {
          class: [
            'pivot-grid-cell',
            `pivot-grid-cell-${column.meta?.role ?? 'unknown'}`
          ]
        }, formatPivotCell(column, row[column.field as string]))
      }
    }
  }
}

function formatPivotCell(column: PivotGridColumn, value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return props.emptyCellText
  }

  const leafNode = column.field ? leafNodeByField.value.get(column.field) : undefined
  const metric = resolveMetric(column, leafNode)
  const format = metric?.format?.toLowerCase()

  if (typeof value === 'number') {
    if (format === 'money' || format === 'number') {
      return value.toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      })
    }

    return value.toLocaleString('zh-CN')
  }

  return String(value)
}

function resolveMetric(
  column: PivotGridColumn,
  leafNode?: PivotHeaderNode
): PivotMetric | undefined {
  const metricField = column.meta?.metricField ?? leafNode?.metricField
  return metricField ? metricByField.value.get(metricField) : undefined
}
</script>

<template>
  <div class="pivot-grid">
    <vxe-grid v-bind="gridOptions">
      <template #empty>
        <slot name="empty">
          <div class="pivot-grid-empty">{{ emptyText }}</div>
        </slot>
      </template>
    </vxe-grid>
  </div>
</template>

<style scoped>
.pivot-grid {
  width: 100%;
  height: 100%;
  min-height: 0;
  background: #fff;
}

.pivot-grid-cell {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pivot-grid-cell-metric,
.pivot-grid-cell-subtotal,
.pivot-grid-cell-grandTotal {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.pivot-grid-empty {
  padding: 24px;
  color: #909399;
  text-align: center;
}
</style>
