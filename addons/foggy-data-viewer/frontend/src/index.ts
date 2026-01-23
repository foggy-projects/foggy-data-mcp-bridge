// 统一引入所有依赖样式（用户只需引入一次）
import 'vxe-table/lib/style.css'
import 'element-plus/dist/index.css'

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
  QueryMetaResponse,
  ViewerQueryRequest,
  ViewerDataResponse,
  SliceRequestDef,
  OrderRequestDef,
  FilterOption
} from './types'
