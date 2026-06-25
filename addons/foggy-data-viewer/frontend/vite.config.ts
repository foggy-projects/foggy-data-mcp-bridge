import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

type CompactDemoOrder = {
  field: string
  order: 'asc' | 'desc'
}

type CompactDemoSlice = {
  field: string
  op: string
  value?: unknown
}

type CompactDemoRow = Record<string, string | number | boolean>

type CompactDemoApiRequest = {
  method?: string
  on(event: 'data', listener: (chunk: unknown) => void): void
  on(event: 'end', listener: () => void): void
}

type CompactDemoApiResponse = {
  statusCode: number
  setHeader(name: string, value: string): void
  end(data?: string): void
}

type CompactDemoServer = {
  middlewares: {
    use(
      path: string,
      handler: (req: CompactDemoApiRequest, res: CompactDemoApiResponse, next: () => void) => void
    ): void
  }
}

const compactDemoRowsForApi: CompactDemoRow[] = [
  ['432154486', '251125000012', '济南集配', '广州', '广州', '广州', true, false, '2026-06-15 13:33:37', 1],
  ['432153784', 'YZ000000019', '青岛集配', '广州', '广州', '广州', true, false, '2026-06-09 16:21:25', 1000],
  ['432154430', '', '青岛集配', '贵阳', '贵阳', '白云区', false, true, '2026-06-09 13:16:10', 2],
  ['432154407', '', '青岛集配', '城阳区', '青岛集配', '城阳区', false, true, '2026-06-08 16:12:52', 2],
  ['432154406', '', '青岛集配', '城阳区', '青岛集配', '城阳区', false, true, '2026-06-08 15:51:26', 2],
  ['432154367', '', '济南集配', '广州', '广州', '白云区', true, false, '2026-06-08 10:03:08', 2],
  ['432154339', '', '美里', '济南集配', '广州', '广州', false, false, '2026-06-05 11:28:55', 3],
  ['432154336', '', '美里', '济南集配', '广州', '广州', false, false, '2026-06-05 11:28:17', 3],
  ['432154335', '', '美里', '济南集配', '济南集配', '济南集配', true, true, '2026-06-05 11:27:18', 3],
  ['432154333', '', '美里', '济南集配', '济南集配', '济南集配', true, true, '2026-06-05 11:26:51', 3]
].map(([waybillNo, customerNo, openingSite, nextStation, arrivalSite, destination, isBranchCompany, isTerminalOrg, stockInTime, stockInCount]) => ({
  waybillNo,
  customerNo,
  openingSite,
  nextStation,
  arrivalSite,
  destination,
  isBranchCompany,
  isTerminalOrg,
  stockInTime,
  stockInCount
}))

function isCompactDemoOrder(value: unknown): value is CompactDemoOrder {
  if (!value || typeof value !== 'object') return false

  const order = value as Partial<CompactDemoOrder>
  return typeof order.field === 'string' && (order.order === 'asc' || order.order === 'desc')
}

function normalizeCompactDemoOrderBy(value: unknown): CompactDemoOrder[] {
  return Array.isArray(value) ? value.filter(isCompactDemoOrder) : []
}

function isCompactDemoSlice(value: unknown): value is CompactDemoSlice {
  if (!value || typeof value !== 'object') return false

  const slice = value as Partial<CompactDemoSlice>
  return typeof slice.field === 'string' && typeof slice.op === 'string'
}

function normalizeCompactDemoSlices(value: unknown): CompactDemoSlice[] {
  return Array.isArray(value) ? value.filter(isCompactDemoSlice) : []
}

function normalizeCompactDemoFilterText(value: unknown): string {
  return String(value ?? '').trim().toLowerCase()
}

function stripCompactDemoLikeWildcard(value: unknown): string {
  return normalizeCompactDemoFilterText(value).replace(/^%+|%+$/g, '')
}

function matchesCompactDemoSlice(row: CompactDemoRow, slice: CompactDemoSlice): boolean {
  if (!(slice.field in row)) return true
  if (slice.value === null || slice.value === undefined || slice.value === '') return true

  const fieldValue = row[slice.field]
  const fieldText = normalizeCompactDemoFilterText(fieldValue)

  switch (slice.op) {
    case '=':
      return fieldText === normalizeCompactDemoFilterText(slice.value)
    case 'in': {
      const values = Array.isArray(slice.value) ? slice.value : [slice.value]
      return values.some(value => fieldText === normalizeCompactDemoFilterText(value))
    }
    case 'right_like':
      return fieldText.startsWith(stripCompactDemoLikeWildcard(slice.value))
    case 'left_like':
      return fieldText.endsWith(stripCompactDemoLikeWildcard(slice.value))
    case 'like':
    default:
      return fieldText.includes(stripCompactDemoLikeWildcard(slice.value))
  }
}

function filterCompactDemoRowsForApi(slice: CompactDemoSlice[] = []) {
  if (slice.length === 0) {
    return compactDemoRowsForApi.slice()
  }
  return compactDemoRowsForApi.filter(row => slice.every(item => matchesCompactDemoSlice(row, item)))
}

function sortCompactDemoRowsForApi(orderBy: CompactDemoOrder[] = [], rows: CompactDemoRow[] = compactDemoRowsForApi) {
  const sortDef = orderBy[0]
  const items = rows.slice()

  if (!sortDef?.field || !sortDef.order) {
    return items
  }

  return items
    .map((row, index) => ({ row, index }))
    .sort((left, right) => {
      const leftValue = left.row[sortDef.field]
      const rightValue = right.row[sortDef.field]
      const leftEmpty = leftValue === null || leftValue === undefined || leftValue === ''
      const rightEmpty = rightValue === null || rightValue === undefined || rightValue === ''

      if (leftEmpty || rightEmpty) {
        if (leftEmpty && rightEmpty) return left.index - right.index
        return leftEmpty ? 1 : -1
      }

      let result = 0
      if (sortDef.field === 'stockInCount') {
        result = Number(leftValue) - Number(rightValue)
      } else if (sortDef.field === 'stockInTime') {
        result = Date.parse(String(leftValue)) - Date.parse(String(rightValue))
      } else {
        result = String(leftValue).localeCompare(String(rightValue), 'zh-CN', {
          numeric: true,
          sensitivity: 'base'
        })
      }

      const orderedResult = sortDef.order === 'asc' ? result : -result
      return orderedResult || left.index - right.index
    })
    .map(item => item.row)
}

function compactDemoApiPlugin() {
  return {
    name: 'compact-demo-api',
    configureServer(server: CompactDemoServer) {
      server.middlewares.use('/data-viewer/api/demo/compact', (req, res, next) => {
        if (req.method !== 'POST') {
          next()
          return
        }

        let body = ''

        req.on('data', (chunk: unknown) => {
          body += String(chunk)
        })

        req.on('end', () => {
          try {
            const params = body ? JSON.parse(body) as { orderBy?: unknown; slice?: unknown } : {}
            const filteredItems = filterCompactDemoRowsForApi(normalizeCompactDemoSlices(params.slice))
            const items = sortCompactDemoRowsForApi(normalizeCompactDemoOrderBy(params.orderBy), filteredItems)

            res.statusCode = 200
            res.setHeader('Content-Type', 'application/json; charset=utf-8')
            res.end(JSON.stringify({ items, total: items.length }))
          } catch (error) {
            res.statusCode = 400
            res.setHeader('Content-Type', 'application/json; charset=utf-8')
            res.end(JSON.stringify({
              message: error instanceof Error ? error.message : 'Invalid compact demo request'
            }))
          }
        })
      })
    }
  }
}

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // 库模式配置
  if (mode === 'lib') {
    return {
      plugins: [vue()],
      resolve: {
        alias: {
          '@': resolve(__dirname, 'src')
        }
      },
      build: {
        lib: {
          entry: resolve(__dirname, 'src/index.ts'),
          name: 'FoggyDataViewer',
          fileName: (format) => `index.${format === 'es' ? 'js' : format}`
        },
        rollupOptions: {
          // 确保外部化依赖，不打包进库
          external: ['vue', 'vxe-table', 'xe-utils', 'axios', 'element-plus'],
          output: {
            globals: {
              vue: 'Vue',
              'vxe-table': 'VxeTable',
              'xe-utils': 'XEUtils',
              'axios': 'axios',
              'element-plus': 'ElementPlus'
            },
            // 为每个外部化的依赖提供一个全局变量
            assetFileNames: (assetInfo) => {
              if (assetInfo.name === 'style.css') return 'style.css'
              return assetInfo.name || ''
            }
          }
        },
        outDir: 'dist',
        emptyOutDir: true
      }
    }
  }

  // 应用模式配置（默认）
  return {
    plugins: [compactDemoApiPlugin(), vue()],
    base: '/data-viewer/',
    build: {
      outDir: resolve(__dirname, '../src/main/resources/static/data-viewer'),
      emptyOutDir: true,
      rollupOptions: {
        output: {
          manualChunks: {
            'vxe-table': ['vxe-table', 'xe-utils'],
            'vue': ['vue']
          }
        }
      }
    },
    server: {
      port: 5193,
      host: '0.0.0.0', // 添加这一行
      proxy: {
        '/data-viewer/api': {
          target: 'http://localhost:7108',
          changeOrigin: true
        }
      }
    },
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      }
    }
  }
})
