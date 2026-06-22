import { ref, computed, type Ref } from 'vue'
import type { ColumnSchema } from '@/types'

/** 度量类型列表 */
const MEASURE_TYPES = ['NUMBER', 'MONEY', 'BIGDECIMAL', 'INTEGER', 'BIGINT', 'LONG']

function isNumericMeasureType(type?: string): boolean {
  return MEASURE_TYPES.includes(type?.toUpperCase() || '')
}

function isSummaryColumn(col: ColumnSchema | undefined): col is ColumnSchema {
  return !!col && (col.measure === true || col.aggregatable === true) && isNumericMeasureType(col.type)
}

function readCountValue(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }

  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }

  return null
}

/**
 * 表格汇总数据计算逻辑
 */
export function useTableSummary(columns: Ref<ColumnSchema[]>) {
  /** 后端返回的全量汇总 */
  const serverSummary = ref<Record<string, unknown> | null>(null)

  /** 度量列（用于汇总计算） */
  const measureColumns = computed(() => {
    return columns.value.filter(isSummaryColumn)
  })

  /**
   * 计算选中行的汇总
   */
  function calculateSelectedSummary(
    selectedRows: Record<string, unknown>[]
  ): Record<string, unknown> {
    const summary: Record<string, unknown> = {
      _count: selectedRows.length
    }

    if (selectedRows.length === 0) {
      return summary
    }

    for (const col of measureColumns.value) {
      summary[col.name] = selectedRows.reduce((sum, row) => {
        const val = row[col.name]
        return sum + (typeof val === 'number' ? val : 0)
      }, 0)
    }

    return summary
  }

  /**
   * 格式化数值用于显示
   */
  function formatValue(value: unknown, type?: string): string {
    if (value == null) return ''
    if (typeof value !== 'number') return String(value)

    const upperType = type?.toUpperCase()
    if (upperType === 'MONEY' || upperType === 'NUMBER' || upperType === 'BIGDECIMAL') {
      return value.toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      })
    }
    return value.toLocaleString('zh-CN')
  }

  /**
   * 生成 footer 数据（二维数组）
   * @param visibleColumns 可见列配置（包含 checkbox 列）
   * @param selectedSummary 选中行汇总
   * @returns footer 数据，第一行选中汇总，第二行全量汇总
   */
  function generateFooterData(
    visibleColumns: { field?: string; type?: string }[],
    selectedSummary: Record<string, unknown>,
    fallbackTotal = 0
  ): (string | number | null)[][] {
    const row1: (string | number | null)[] = []  // 选中汇总
    const row2: (string | number | null)[] = []  // 全量汇总

    for (let i = 0; i < visibleColumns.length; i++) {
      const col = visibleColumns[i]
      const field = col.field

      // 第一列（checkbox 列）显示标签
      if (i === 0) {
        row1.push('选中')
        row2.push('合计')
        continue
      }

      // 第二列显示记录数
      if (i === 1) {
        const selectedCount = readCountValue(selectedSummary._count) ?? 0
        const totalCount = readCountValue(serverSummary.value?.total)
          ?? readCountValue(serverSummary.value?._count)
          ?? fallbackTotal
        row1.push(`${selectedCount} 条`)
        row2.push(`${totalCount} 条`)
        continue
      }

      // 其他列：如果是度量列则显示汇总值
      if (field) {
        const colSchema = columns.value.find(c => c.name === field)

        if (isSummaryColumn(colSchema)) {
          const selectedVal = selectedSummary[field]
          const serverVal = serverSummary.value?.[field]
          row1.push(formatValue(selectedVal, colSchema?.type))
          row2.push(formatValue(serverVal, colSchema?.type))
        } else {
          row1.push(null)
          row2.push(null)
        }
      } else {
        row1.push(null)
        row2.push(null)
      }
    }

    return [row1, row2]
  }

  /**
   * 设置服务端汇总数据
   */
  function setServerSummary(data: Record<string, unknown> | null) {
    serverSummary.value = data
  }

  return {
    serverSummary,
    measureColumns,
    calculateSelectedSummary,
    generateFooterData,
    setServerSummary,
    formatValue
  }
}
