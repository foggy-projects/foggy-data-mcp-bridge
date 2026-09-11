import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import type { Plugin } from 'vite'

/** Isolated HTTP fixture, not a substitute for production Mongo/permission acceptance. */
export function acceptanceBackend(): Plugin {
  const directory = resolve(process.cwd(), '.acceptance')
  mkdirSync(directory, { recursive: true })
  const file = resolve(directory, 'presets.json')
  let presets: any[] = existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : []
  const persist = () => writeFileSync(file, JSON.stringify(presets, null, 2))
  return { name: 'custom-query-acceptance-fixture', configureServer(server) {
    server.middlewares.use('/data-viewer/api', async (req, res) => {
      const url = new URL(req.url || '/', 'http://localhost')
      res.setHeader('Content-Type', 'application/json; charset=utf-8')
      const send = (data: unknown) => res.end(JSON.stringify(data))
      const ok = (data: unknown) => send({ code: 200, data })
      try {
        let raw = ''; for await (const chunk of req) raw += chunk
        const body = raw ? JSON.parse(raw) : {}
        if (url.pathname === '/acceptance/members') {
          const items = [{ value: 101, label: '杭州分拨中心' }, { value: 102, label: '上海转运中心' }, { value: 103, label: '北京分拨中心' }]
          const filtered = items.filter(item => !body.keyword || item.label.includes(body.keyword))
          return send({ items: filtered, selectedItems: items.filter(item => body.selectedValues?.includes(item.value)), total: filtered.length, hasMore: false })
        }
        if (url.pathname === '/acceptance/query') {
          const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
          const rows = Array.from({ length: 46 }, (_, index) => ({
            runtimeId: index + 1, orderNo: `YS202609${String(index + 1).padStart(4, '0')}`, status: index % 4 === 0 ? 'TRANSIT' : 'SIGNED',
            openingDate: date, openingTime: `${date} 09:30:00`, 'origin$id': 101 + index % 2, 'origin$caption': index % 2 ? '上海转运中心' : '杭州分拨中心',
            'destination$id': 103, 'destination$caption': '北京分拨中心', consignor: '恒远商贸', consignee: '林先生', phone: '138****1234', goods: '日用百货',
            payment: index % 2 ? 'COLLECT' : 'PREPAID', pieces: index + 1, weight: 35 + index, customer: '恒远商贸', service: 'STANDARD', pickupTime: `${date} 10:00:00`, customerOrderNo: `KH${index + 1}`
          }))
          const matches = (row: any, node: any): boolean => {
            if (node.$and) return node.$and.every((child: any) => matches(row, child))
            if (node.$or) return node.$or.some((child: any) => matches(row, child))
            const value = row[node.field]
            switch (node.op) {
              case '=': return value === node.value
              case '!=': return value !== node.value
              case 'in': return node.value.includes(value)
              case 'not in': return !node.value.includes(value)
              case '>=': return value >= node.value
              case '<=': return value <= node.value
              case '>': return value > node.value
              case '<': return value < node.value
              case '[)': return value >= node.value[0] && value < node.value[1]
              case '[]': return value >= node.value[0] && value <= node.value[1]
              case 'is null': return value == null
              case 'is not null': return value != null
              case 'like': return String(value).includes(node.value)
              default: throw new Error(`Fixture operator not implemented: ${node.op}`)
            }
          }
          const filtered = rows.filter(row => (body.slice || []).every((node: any) => matches(row, node)))
          for (const order of [...(body.orderBy || [])].reverse()) filtered.sort((a: any, b: any) => (String(a[order.field]).localeCompare(String(b[order.field]), 'zh-CN', { numeric: true })) * ((order.dir || order.order) === 'desc' ? -1 : 1))
          const start = ((body.page || 1) - 1) * (body.pageSize || 20)
          return send({ items: filtered.slice(start, start + (body.pageSize || 20)).map(row => Object.fromEntries((body.columns || []).map((key: string) => [key, (row as any)[key] ?? '']))), total: filtered.length })
        }
        const match = url.pathname.match(/^\/list-preset\/users\/([^/]+)\/(models|presets)\/([^/]+)(\/default)?$/)
        if (match) {
          const ownerId = decodeURIComponent(match[1]); const id = decodeURIComponent(match[3]); const modelScope = match[2] === 'models'
          const scope = presets.filter(p => p.ownerId === ownerId && (modelScope ? p.model === id && p.businessKey === (url.searchParams.get('businessKey') || '') : p.id === id))
          if (req.method === 'GET') return ok(match[4] ? scope.find(p => p.isDefault) || null : modelScope ? scope : scope[0] || null)
          if (modelScope && req.method === 'POST') {
            const preset = { ...body, id: crypto.randomUUID(), ownerId, model: id, businessKey: url.searchParams.get('businessKey') || '', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), version: 1 }
            presets.push(preset); persist(); return ok(preset)
          }
          if (!scope[0]) { res.statusCode = 404; return send({ code: 404, msg: '方案不存在' }) }
          if (req.method === 'PUT') { Object.assign(scope[0], body, { updatedAt: new Date().toISOString() }); persist(); return ok(scope[0]) }
          if (req.method === 'DELETE') { presets = presets.filter(p => p.id !== scope[0].id); persist(); return ok(null) }
          if (req.method === 'POST' && match[4]) { presets.filter(p => p.ownerId === ownerId && p.model === scope[0].model && p.businessKey === scope[0].businessKey).forEach(p => p.isDefault = p.id === id); persist(); return ok(scope[0]) }
        }
        if (url.pathname.includes('default')) return ok(null)
        res.statusCode = 404; send({ code: 404, msg: 'Fixture route not implemented' })
      } catch (error) { res.statusCode = 400; send({ code: 400, msg: String(error) }) }
    })
  } }
}
