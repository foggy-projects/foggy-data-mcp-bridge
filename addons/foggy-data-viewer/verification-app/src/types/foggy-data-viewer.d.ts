declare module 'foggy-data-viewer' {
  import type { DefineComponent } from 'vue'

  export type DataTableWithSearchRef = {
    refresh: () => void
    clearAllFilters: () => void
    getSelectedRows: () => Record<string, unknown>[]
    getSelectedCount: () => number
  }

  export type SliceRequestDef = {
    field: string
    op: string
    value?: unknown
  }

  export type OrderRequestDef = {
    field: string
    order: 'asc' | 'desc'
  }

  export type ColumnSchema = {
    name: string
    type: string
    title?: string
    filterType?: string
    filterable?: boolean
    aggregatable?: boolean
    measure?: boolean
  }

  export type EnhancedColumnSchema = ColumnSchema & {
    width?: number
    minWidth?: number
    fixed?: 'left' | 'right'
  }

  export type TableConfig = {
    qmModel?: string
    visibleColumns?: string[]
    customizations?: Array<{
      name: string
      width?: number
      minWidth?: number
      fixed?: 'left' | 'right'
    }>
  }

  export type TableSchema = {
    columns: EnhancedColumnSchema[]
    searchableFields?: string[]
    pageSize?: number
    showFilters?: boolean
    showSearchToolbar?: boolean
    searchLayout?: 'horizontal' | 'vertical'
  }

  export type FetchDataParams = {
    page: number
    pageSize: number
    slice: SliceRequestDef[]
    orderBy: OrderRequestDef[]
  }

  export type FetchDataResult = {
    items: Record<string, unknown>[]
    total: number
    totalData?: Record<string, unknown> | null
  }

  export type CreateQueryRequest = {
    model: string
    title?: string
    payload: {
      columns: string[]
      extData?: Record<string, unknown>
      slice?: SliceRequestDef[]
      orderBy?: OrderRequestDef[]
    }
  }

  export type CreateQueryResponse = {
    success: boolean
    queryId?: string
    error?: string
  }

  export type QueryMetaResponse = {
    title?: string
    tableConfig?: TableConfig
    estimatedRowCount?: number
  }

  export type ViewerQueryRequest = {
    start?: number
    limit?: number
    extData?: Record<string, unknown>
    slice?: SliceRequestDef[]
    orderBy?: OrderRequestDef[]
  }

  export type ViewerDataResponse = {
    success: boolean
    items: Record<string, unknown>[]
    total: number
    totalData?: Record<string, unknown> | null
    error?: string
  }

  export type PivotAxisRole = 'rowAxis' | 'columnAxis'
  export type PivotShape = 'grid' | 'flat' | 'tree'
  export type PivotViewMode = 'pivotTable'

  export type PivotAxisField = {
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

  export type PivotMetric = {
    field: string
    title?: string
    format?: string
    aggregate?: string
  }

  export type PivotRawAxisMember = {
    key: string
    title?: string
    axisValue?: unknown
    values: Record<string, unknown>
  }

  export type PivotRawCell = {
    rowKey: string
    columnKey: string
    metricField: string
    value: unknown
  }

  export type PivotAxisPage = {
    field: string
    offset: number
    limit: number
    total?: number
    hasMore?: boolean
    pageScope: 'globalAxis' | 'perParent'
  }

  export type PivotRawPayload = {
    viewMode: PivotViewMode
    shape: PivotShape
    rowAxes: PivotAxisField[]
    columnAxes: PivotAxisField[]
    metrics: PivotMetric[]
    rowMembers: PivotRawAxisMember[]
    columnMembers: PivotRawAxisMember[]
    cells: PivotRawCell[]
    axisPages?: {
      rows?: PivotAxisPage[]
      columns?: PivotAxisPage[]
    }
    evidence?: Record<string, unknown>
  }

  export const DataViewer: DefineComponent<Record<string, unknown>>
  export const DataTable: DefineComponent<Record<string, unknown>>
  export const SearchToolbar: DefineComponent<Record<string, unknown>>
  export const DataTableWithSearch: DefineComponent<Record<string, unknown>>
  export const SavedQueryManager: DefineComponent<Record<string, unknown>>
  export const PivotRawViewer: DefineComponent<Record<string, unknown>>

  export function buildTableColumns(schema: ColumnSchema[], config?: TableConfig): EnhancedColumnSchema[]
  export function createQuery(request: CreateQueryRequest): Promise<CreateQueryResponse>
  export function fetchQueryMeta(queryId: string): Promise<QueryMetaResponse>
  export function fetchQueryMeta(model: string, queryId: string): Promise<QueryMetaResponse>
  export function fetchQmSchema(model: string): Promise<ColumnSchema[]>
  export function fetchQueryData(queryId: string, request: ViewerQueryRequest): Promise<ViewerDataResponse>
  export function fetchQueryData(model: string, queryId: string, request: ViewerQueryRequest): Promise<ViewerDataResponse>
}

declare module 'foggy-data-viewer/style.css' {}
