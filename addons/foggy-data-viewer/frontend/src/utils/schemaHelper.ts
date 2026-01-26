import type { ColumnSchema, EnhancedColumnSchema, TableConfig } from '@/types'

/**
 * 智能计算列宽度
 *
 * 根据列标题长度和数据类型自动计算合理的列宽度，
 * 确保列名和排序图标能完整显示在一行内。
 *
 * @param title - 列标题文本
 * @param type - 数据类型
 * @returns 推荐的列宽度（像素）
 *
 * @example
 * ```typescript
 * // 基本使用
 * const width = calculateColumnWidth('订单ID', 'INTEGER')  // 返回 120
 *
 * // 长列名
 * const width = calculateColumnWidth('速度(km/h)', 'NUMBER')  // 返回 170
 *
 * // 在 schema 中使用
 * const columns: EnhancedColumnSchema[] = [
 *   {
 *     name: 'speed',
 *     type: 'NUMBER',
 *     title: '速度(km/h)',
 *     width: calculateColumnWidth('速度(km/h)', 'NUMBER'),
 *     sortable: true
 *   }
 * ]
 * ```
 */
export function calculateColumnWidth(title: string, type: string): number {
  // 常量配置
  const charWidth = 14        // 中文字符平均宽度（像素）
  const iconWidth = 30        // 排序图标宽度
  const padding = 24          // 左右内边距

  // 根据标题长度计算基础宽度
  const titleWidth = title.length * charWidth
  const minWidth = titleWidth + iconWidth + padding

  // 根据数据类型设置最小宽度
  const typeMinWidths: Record<string, number> = {
    'INTEGER': 100,      // 整数类型
    'LONG': 120,         // 长整数
    'MONEY': 120,        // 金额类型
    'NUMBER': 120,       // 数值类型
    'DATETIME': 180,     // 日期时间
    'DAY': 120,          // 日期
    'TEXT': 150,         // 文本
    'STRING': 150,       // 字符串
    'BOOL': 80,          // 布尔值
    'DICT': 120          // 字典
  }

  return Math.max(minWidth, typeMinWidths[type] || 120)
}

/**
 * 构建表格列配置
 *
 * @param qmSchema - 从后台获取的 QM schema
 * @param config - 表格配置（如果 visibleColumns 为空或未指定，默认显示所有列）
 * @returns 增强的列配置数组
 */
export function buildTableColumns(
  qmSchema: ColumnSchema[],
  config: TableConfig
): EnhancedColumnSchema[] {
  const schemaMap = new Map(qmSchema.map(s => [s.name, s]))
  const customMap = new Map(config.customizations?.map(c => [c.name, c]) || [])

  // 确定要显示的列及顺序
  // 如果 visibleColumns 为空数组或未定义，且 showAll 未设置，则默认显示所有列
  const shouldShowAll = config.showAll || !config.visibleColumns || config.visibleColumns.length === 0
  const columnNames = shouldShowAll
    ? qmSchema.map(s => s.name)
    : config.visibleColumns

  return columnNames
    .map(name => {
      const schema = schemaMap.get(name)
      if (!schema) {
        console.warn(`Column "${name}" not found in QM schema`)
        return null
      }

      const custom = customMap.get(name)

      // 自动计算列宽度：如果 width 未定义或为 0，使用 calculateColumnWidth 自动计算
      const width = custom?.width || schema.width || calculateColumnWidth(schema.title || schema.name, schema.type)

      return {
        ...schema,
        width,
        minWidth: custom?.minWidth ?? 120,
        fixed: custom?.fixed,
        filterable: schema.filterable ?? true,  // 默认可筛选
        customRender: custom?.render,
        customFilterComponent: custom?.filterComponent,
        customFormatter: custom?.formatter
      }
    })
    .filter((col): col is EnhancedColumnSchema => col !== null)
}
