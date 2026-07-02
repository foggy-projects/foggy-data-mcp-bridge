import { expect, type Locator, type Page, type Route, test } from '@playwright/test'

interface PresetRecord {
  id: string
  model: string
  businessKey: string
  title: string
  description?: string
  columns: string[]
  columnSettings: Array<Record<string, unknown>>
  query: {
    slice: Array<Record<string, unknown>>
    orderBy: Array<Record<string, unknown>>
  }
  pageSize?: number
  visibility: 'PRIVATE' | 'DEPARTMENT' | 'TENANT'
  ownerId: string
  isDefault: boolean
  version: number
  createdAt: string
  updatedAt: string
}

interface MockBackendState {
  events: string[]
  dataRequests: Array<Record<string, unknown>>
}

test('custom query preset can be saved, applied, set as default, deleted, and restored after reload', async ({ page }) => {
  const backend = await installMockBackend(page)

  await page.goto('/')
  await page.getByText('自定义查询（新）').click()

  await expect(page.getByRole('heading', { name: '自定义查询功能测试（新功能）' })).toBeVisible()
  await expect(page.getByTestId('list-preset-open')).toBeVisible()

  await page.getByTestId('list-preset-open').click()
  const dialog = page.getByTestId('list-preset-dialog')
  await expect(dialog).toBeVisible()

  await fillPresetTitle(dialog, 'P2 默认查询')
  await page.getByTestId('list-preset-save').click()
  await expectLatestMessage(page, '自定义查询已保存')

  const defaultItem = dialog.getByTestId('list-preset-item').filter({ hasText: 'P2 默认查询' })
  await expect(defaultItem).toBeVisible()
  await defaultItem.getByTestId('list-preset-more').click()
  await page.getByRole('menuitem', { name: /设为默认/ }).click()
  await expectLatestMessage(page, '默认查询已更新')
  await expect(defaultItem).toContainText('默认')

  await defaultItem.getByTestId('list-preset-apply').click()
  await expectLatestMessage(page, '已应用: P2 默认查询')

  await page.getByTestId('list-preset-open').click()
  await fillPresetTitle(dialog, 'P2 临时查询')
  await page.getByTestId('list-preset-save').click()
  await expectLatestMessage(page, '自定义查询已保存')

  const temporaryItem = dialog.getByTestId('list-preset-item').filter({ hasText: 'P2 临时查询' })
  await expect(temporaryItem).toBeVisible()
  await temporaryItem.getByTestId('list-preset-more').click()
  await page.getByRole('menuitem', { name: /删除/ }).click()
  await page.getByRole('button', { name: '删除' }).click()
  await expectLatestMessage(page, '自定义查询已删除')
  await expect(temporaryItem).toHaveCount(0)

  const eventCountBeforeReload = backend.events.length
  await page.reload()
  const dataAfterReload = page.waitForResponse(response =>
    response.url().includes('/data-viewer/api/query/') && response.url().endsWith('/data')
  )
  await page.getByText('自定义查询（新）').click()
  await dataAfterReload

  const reloadEvents = backend.events.slice(eventCountBeforeReload)
  const defaultIndex = reloadEvents.indexOf('getDefault')
  const dataIndex = reloadEvents.indexOf('queryData')
  expect(defaultIndex).toBeGreaterThanOrEqual(0)
  expect(dataIndex).toBeGreaterThan(defaultIndex)

  await page.getByTestId('list-preset-open').click()
  await expect(dialog.getByTestId('list-preset-item').filter({ hasText: 'P2 默认查询' })).toContainText('默认')
  expect(backend.dataRequests.length).toBeGreaterThan(0)
})

async function fillPresetTitle(dialog: Locator, title: string) {
  await dialog.getByTestId('list-preset-save-tab').click()
  await dialog.getByTestId('list-preset-title').locator('input').fill(title)
}

async function installMockBackend(page: Page): Promise<MockBackendState> {
  const presets: PresetRecord[] = []
  const state: MockBackendState = {
    events: [],
    dataRequests: []
  }

  await page.route('**/data-viewer/api/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const method = request.method()

    if (path.includes('/list-preset/')) {
      await handleListPresetRoute(route, presets, path, method, url, state)
      return
    }

    if (method === 'POST' && path.endsWith('/query/create')) {
      await route.fulfill({ json: rxOk({ success: true, queryId: 'query-custom-list', viewerUrl: null, error: null }) })
      return
    }

    if (method === 'GET' && path.endsWith('/meta')) {
      await route.fulfill({
        json: rxOk({
          tableConfig: {
            qmModel: 'FactSalesQueryModel',
            visibleColumns: ['orderId', 'salesDate$caption', 'product$caption', 'customer$caption', 'quantity', 'salesAmount'],
            customizations: []
          }
        })
      })
      return
    }

    if (method === 'GET' && path.includes('/schema/')) {
      await route.fulfill({
        json: rxOk({
          version: 'v3',
          fields: {
            orderId: { name: '订单号', type: 'TEXT', filterable: true },
            'salesDate$caption': { name: '销售日期', type: 'DAY', filterable: true },
            'product$caption': { name: '商品', type: 'TEXT', filterable: true },
            'customer$caption': { name: '客户', type: 'TEXT', filterable: true },
            'store$caption': { name: '门店', type: 'TEXT', filterable: true },
            quantity: { name: '数量', type: 'INTEGER', filterable: true, measure: true, aggregatable: true },
            salesAmount: { name: '销售额', type: 'MONEY', filterable: true, measure: true, aggregatable: true },
            profitAmount: { name: '利润额', type: 'MONEY', filterable: true, measure: true, aggregatable: true }
          }
        })
      })
      return
    }

    if (method === 'POST' && path.endsWith('/data')) {
      state.events.push('queryData')
      state.dataRequests.push((request.postDataJSON() ?? {}) as Record<string, unknown>)
      await route.fulfill({
        json: rxOk({
          success: true,
          items: [
            {
              orderId: 'SO-001',
              'salesDate$caption': '2026-05-24',
              'product$caption': '测试商品',
              'customer$caption': '测试客户',
              'store$caption': '测试门店',
              quantity: 3,
              salesAmount: 1200,
              profitAmount: 320
            }
          ],
          total: 1,
          totalData: { quantity: 3, salesAmount: 1200, profitAmount: 320 }
        })
      })
      return
    }

    await route.fulfill({ status: 404, json: { code: 404, msg: 'not found', data: null } })
  })

  return state
}

async function handleListPresetRoute(
  route: Route,
  presets: PresetRecord[],
  path: string,
  method: string,
  url: URL,
  state: MockBackendState
) {
  const request = route.request()
  const pathParts = path.split('/').map(decodeURIComponent)
  const userId = pathParts[pathParts.indexOf('users') + 1]
  const model = pathParts[pathParts.indexOf('models') + 1]
  const presetId = pathParts[pathParts.indexOf('presets') + 1]
  const businessKey = url.searchParams.get('businessKey') || ''

  if (method === 'GET' && path.endsWith('/default')) {
    state.events.push('getDefault')
    await route.fulfill({
      json: rxOk(presets.find(preset =>
        preset.ownerId === userId &&
        preset.model === model &&
        preset.businessKey === businessKey &&
        preset.isDefault
      ) ?? null)
    })
    return
  }

  if (method === 'GET' && path.includes('/models/')) {
    await route.fulfill({
      json: rxOk(presets.filter(preset =>
        preset.ownerId === userId &&
        preset.model === model &&
        preset.businessKey === businessKey
      ))
    })
    return
  }

  if (method === 'POST' && path.includes('/models/')) {
    const body = request.postDataJSON() as Partial<PresetRecord>
    const now = new Date().toISOString()
    const preset: PresetRecord = {
      id: `preset-${presets.length + 1}`,
      model,
      businessKey,
      title: String(body.title),
      description: body.description,
      columns: body.columns ?? [],
      columnSettings: body.columnSettings ?? [],
      query: body.query ?? { slice: [], orderBy: [] },
      pageSize: body.pageSize,
      visibility: body.visibility ?? 'PRIVATE',
      ownerId: userId,
      isDefault: Boolean(body.isDefault),
      version: 1,
      createdAt: now,
      updatedAt: now
    }
    if (preset.isDefault) {
      clearDefault(presets, preset)
    }
    presets.unshift(preset)
    await route.fulfill({ json: rxOk(preset) })
    return
  }

  if (method === 'PUT' && presetId) {
    const index = presets.findIndex(preset => preset.id === presetId && preset.ownerId === userId)
    if (index < 0) {
      await route.fulfill({ status: 404, json: { code: 404, msg: '自定义查询不存在', data: null } })
      return
    }
    const body = request.postDataJSON() as Partial<PresetRecord>
    const existing = presets[index]
    const updated: PresetRecord = {
      ...existing,
      ...body,
      updatedAt: new Date().toISOString()
    }
    if (updated.isDefault) {
      clearDefault(presets, updated)
    }
    presets[index] = updated
    await route.fulfill({ json: rxOk(updated) })
    return
  }

  if (method === 'POST' && path.endsWith('/default') && presetId) {
    const preset = presets.find(item => item.id === presetId && item.ownerId === userId)
    if (!preset) {
      await route.fulfill({ status: 404, json: { code: 404, msg: '自定义查询不存在', data: null } })
      return
    }
    clearDefault(presets, preset)
    preset.isDefault = true
    preset.updatedAt = new Date().toISOString()
    await route.fulfill({ json: rxOk(preset) })
    return
  }

  if (method === 'DELETE' && presetId) {
    const index = presets.findIndex(preset => preset.id === presetId && preset.ownerId === userId)
    if (index < 0) {
      await route.fulfill({ status: 404, json: { code: 404, msg: '自定义查询不存在', data: null } })
      return
    }
    presets.splice(index, 1)
    await route.fulfill({ json: rxOk(null) })
    return
  }

  await route.fulfill({ status: 404, json: { code: 404, msg: 'not found', data: null } })
}

function clearDefault(presets: PresetRecord[], target: PresetRecord) {
  for (const preset of presets) {
    if (preset.ownerId === target.ownerId && preset.model === target.model && preset.businessKey === target.businessKey) {
      preset.isDefault = false
    }
  }
}

async function expectLatestMessage(page: Page, text: string) {
  await expect(page.locator('.el-message__content', { hasText: text }).last()).toBeVisible()
}

function rxOk<T>(data: T) {
  return {
    code: 200,
    msg: '',
    data
  }
}
