// 导出组件
export { default as DataTable } from './components/DataTable.vue'
export { default as DataViewer } from './components/DataViewer.vue'

// 导出过滤器组件
export * from './components/filters'

// 导出 Composables
export * from './components/composables'

// 导出工具函数
export { buildTableColumns } from './utils/schemaHelper'

// 导出类型定义
export type {
  ColumnSchema,
  EnhancedColumnSchema,
  TableConfig,
  ColumnCustomization,
  QueryMeta,
  ViewerConfig,
  SliceCondition
} from './types'
