export type PivotShape = 'grid' | 'flat' | 'tree'

export type PivotViewMode = 'pivotTable'

export type PivotAxisRole = 'rowAxis' | 'columnAxis'

export type PivotHeaderRole =
  | 'rowAxis'
  | 'columnAxisMember'
  | 'metric'
  | 'subtotal'
  | 'grandTotal'

export type PivotAxisPageScope = 'globalAxis' | 'perParent'

export interface PivotAxisField {
  field: string
  title?: string
  role: PivotAxisRole
  start?: number
  offset?: number
  limit?: number
  orderBy?: string[]
  domainSliceEnabled?: boolean
  havingEnabled?: boolean
}

export interface PivotMetric {
  field: string
  title?: string
  format?: string
  aggregate?: string
}

export interface PivotHeaderNode {
  key?: string
  field?: string
  title: string
  role: PivotHeaderRole
  children?: PivotHeaderNode[]
  axisValue?: unknown
  metricField?: string
  width?: number
  minWidth?: number
  fixed?: 'left' | 'right'
}

export interface PivotAxisPage {
  field: string
  offset: number
  limit: number
  total?: number
  hasMore?: boolean
  pageScope: PivotAxisPageScope
}

export interface PivotViewModel {
  viewMode: PivotViewMode
  shape: PivotShape
  rowAxes: PivotAxisField[]
  columnAxes: PivotAxisField[]
  metrics: PivotMetric[]
  headerTree: PivotHeaderNode[]
  items: Record<string, unknown>[]
  axisPages?: {
    rows?: PivotAxisPage[]
    columns?: PivotAxisPage[]
  }
  evidence?: Record<string, unknown>
}

export interface PivotRawAxisMember {
  key: string
  title?: string
  axisValue?: unknown
  values: Record<string, unknown>
}

export interface PivotRawCell {
  rowKey: string
  columnKey: string
  metricField: string
  value: unknown
}

export interface PivotRawPayload {
  viewMode: PivotViewMode
  shape: PivotShape
  rowAxes: PivotAxisField[]
  columnAxes: PivotAxisField[]
  metrics: PivotMetric[]
  rowMembers: PivotRawAxisMember[]
  columnMembers: PivotRawAxisMember[]
  cells: PivotRawCell[]
  axisPages?: PivotViewModel['axisPages']
  evidence?: Record<string, unknown>
}
