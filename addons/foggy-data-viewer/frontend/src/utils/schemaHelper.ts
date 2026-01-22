import type { ColumnSchema, EnhancedColumnSchema, TableConfig } from '@/types'

/**
 * 构建表格列配置
 *
 * @param qmSchema - 从后台获取的 QM schema
 * @param config - 表格配置（必须指定 visibleColumns 或 showAll）
 * @returns 增强的列配置数组
 */
export function buildTableColumns(
  qmSchema: ColumnSchema[],
  config: TableConfig
): EnhancedColumnSchema[] {
  // 验证：必须指定 visibleColumns 或 showAll
  if (!config.visibleColumns && !config.showAll) {
    throw new Error('Must specify either visibleColumns or showAll=true')
  }

  const schemaMap = new Map(qmSchema.map(s => [s.name, s]))
  const customMap = new Map(config.customizations?.map(c => [c.name, c]) || [])

  // 确定要显示的列及顺序
  const columnNames = config.showAll
    ? qmSchema.map(s => s.name)
    : config.visibleColumns!

  return columnNames
    .map(name => {
      const schema = schemaMap.get(name)
      if (!schema) {
        console.warn(`Column "${name}" not found in QM schema`)
        return null
      }

      const custom = customMap.get(name)
      return {
        ...schema,
        width: custom?.width,
        minWidth: custom?.minWidth ?? 120,
        fixed: custom?.fixed,
        customRender: custom?.render,
        customFilterComponent: custom?.filterComponent,
        customFormatter: custom?.formatter
      }
    })
    .filter((col): col is EnhancedColumnSchema => col !== null)
}
