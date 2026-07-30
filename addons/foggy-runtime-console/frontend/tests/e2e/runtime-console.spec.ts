import { expect, test, type Page, type Route } from '@playwright/test'

interface MockState {
  datasources: Array<Record<string, unknown>>
  namespaceBindings: Record<string, string>
  bundles: Array<Record<string, unknown>>
  namespaceHeaders: string[]
  refreshScopes: string[]
  requests: Array<{
    path: string
    namespace: string
    body: Record<string, unknown>
  }>
  delayNextDefaultModels: boolean
}

const acceptedToken = 'e2e-runtime-token'
const mockStates = new WeakMap<Page, MockState>()

function envelope(data: unknown) {
  return {
    success: true,
    engine: 'java',
    runtimeApiVersion: 'foggy-runtime-api/v1',
    data
  }
}

async function jsonBody(route: Route): Promise<Record<string, unknown>> {
  try {
    return route.request().postDataJSON() as Record<string, unknown>
  } catch {
    return {}
  }
}

async function mockRuntime(page: Page): Promise<MockState> {
  const state: MockState = {
    datasources: [{
      name: 'analytics',
      type: 'mysql',
      jdbcUrl: 'jdbc:mysql://db.internal:3306/analytics',
      enabled: true,
      source: 'runtime',
      status: 'READY',
      canUpdate: true,
      canRemove: true,
      canTest: true
    }],
    namespaceBindings: { default: 'analytics', finance: 'analytics' },
    bundles: [
      {
        name: 'runtime-console-demo',
        namespace: 'default',
        path: '/runtime/models/demo',
        watch: true,
        enabled: true,
        source: 'runtime-registry',
        status: 'active',
        canUpdate: true,
        canRemove: true
      },
      {
        name: 'finance-models',
        namespace: 'finance',
        path: '/runtime/models/finance',
        watch: false,
        enabled: true,
        source: 'runtime-registry',
        status: 'ready',
        canUpdate: true,
        canRemove: true
      }
    ],
    namespaceHeaders: [],
    refreshScopes: [],
    requests: [],
    delayNextDefaultModels: false
  }

  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^.*\/api\/v1\//, '')
    const token = request.headers()['x-foggy-runtime-code']

    if (path === 'access/check') {
      if (token !== acceptedToken) {
        await route.fulfill({
          status: 401,
          contentType: 'application/json',
          body: JSON.stringify({
            success: false,
            error: { code: 'RUNTIME_AUTH_REQUIRED', message: 'Runtime Token 无效。' }
          })
        })
        return
      }
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify(envelope({
          authenticated: true,
          authScope: 'management-all',
          runtimeApiVersion: 'foggy-runtime-api/v1'
        }))
      })
      return
    }

    if (token !== acceptedToken) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          error: { code: 'RUNTIME_AUTH_REQUIRED', message: '需要 Runtime Token。' }
        })
      })
      return
    }
    const requestNamespace = request.headers()['x-ns'] || ''
    const requestBody = request.method() === 'GET' ? {} : await jsonBody(route)
    state.namespaceHeaders.push(requestNamespace)
    state.requests.push({
      path,
      namespace: requestNamespace,
      body: requestBody
    })

    let data: unknown = {}
    if (path === 'capabilities') {
      data = {
        engine: 'java',
        runtimeApiVersion: 'foggy-runtime-api/v1',
        schemaVersion: 'v1',
        enabled: true,
        securityMode: 'auth-code',
        capabilities: {
          'runtime.accessCheck': 'available',
          'query.execute': 'available',
          'datasources.manage': 'available'
        },
        warnings: []
      }
    } else if (path === 'datasources/diagnostics') {
      data = {
        datasources: state.datasources,
        registryEnabled: true,
        registryExists: true,
        managedDatasourceCount: state.datasources.length,
        namespaceBindings: state.namespaceBindings
      }
    } else if (path === 'datasources' && request.method() === 'GET') {
      data = { datasources: state.datasources, warnings: [] }
    } else if (path === 'datasources' && request.method() === 'POST') {
      const body = requestBody
      state.datasources.push({
        ...body,
        enabled: true,
        source: 'runtime',
        status: 'READY',
        canUpdate: true,
        canRemove: true,
        canTest: true
      })
      data = { name: body.name, created: true }
    } else if (/^namespaces\/[^/]+\/datasource$/.test(path) && request.method() === 'GET') {
      const namespace = decodeURIComponent(path.split('/')[1] || '')
      data = { namespace, dataSource: state.namespaceBindings[namespace] }
    } else if (/^namespaces\/[^/]+\/datasource$/.test(path) && request.method() === 'PUT') {
      const body = requestBody
      const namespace = decodeURIComponent(path.split('/')[1] || '')
      state.namespaceBindings[namespace] = String(body.dataSource || '')
      data = { namespace, dataSource: state.namespaceBindings[namespace] }
    } else if (path === 'models') {
      if (requestNamespace === 'default' && state.delayNextDefaultModels) {
        state.delayNextDefaultModels = false
        await new Promise(resolve => setTimeout(resolve, 350))
      }
      const modelName = requestNamespace === 'finance'
        ? 'FinanceModel'
        : requestNamespace
          ? 'OrderModel'
          : 'EmptySpaceModel'
      data = {
        format: 'json',
        content: '{}',
        data: {
          models: [modelName],
          count: 1,
          items: [{
            model: modelName,
            caption: requestNamespace === 'finance' ? '财务分析' : '订单分析',
            description: requestNamespace === 'finance' ? '用于财务汇总分析。' : '用于订单趋势与履约分析。',
            namespace: requestNamespace,
            sourceKnown: true,
            bundleName: requestNamespace === 'finance' ? 'finance-models' : 'runtime-console-demo',
            sourceNamespace: requestNamespace,
            resourceIdentity: `qm:${modelName}`,
            physicalTables: [requestNamespace === 'finance' ? 'finance.invoices' : 'public.orders'],
            fieldCount: 12,
            primaryTimeField: 'createdAt'
          }]
        }
      }
    } else if (/^models\/[^/]+\/describe$/.test(path)) {
      data = {
        format: 'json',
        content: '{}',
        data: {
          version: 'v3',
          models: {
            OrderModel: {
              name: '订单分析',
              factTable: 'orders',
              purpose: '订单经营分析',
              scenarios: ['趋势分析', '履约监控']
            }
          },
          fields: {
            customer: {
              name: '客户',
              fieldName: 'customer',
              type: 'TEXT',
              measure: false,
              filterable: true,
              sourceColumn: 'customer_name',
              models: {
                OrderModel: {
                  description: '客户显示名称',
                  usage: '用于筛选与分组'
                }
              }
            },
            amount: {
              name: '订单金额',
              fieldName: 'amount',
              type: 'DECIMAL',
              measure: true,
              aggregation: 'SUM',
              sourceColumn: 'pay_amount',
              models: {
                OrderModel: { description: '订单实付金额' }
              }
            },
            margin: {
              name: '订单毛利',
              fieldName: 'margin',
              type: 'DECIMAL',
              calculated: true,
              description: '收入减去成本'
            }
          },
          physicalTables: [{ table: 'public.orders', role: 'fact' }],
          examples: [{ columns: ['customer', 'amount'] }],
          modelSource: {
            known: true,
            bundleName: requestNamespace === 'finance' ? 'finance-models' : 'runtime-console-demo',
            namespace: requestNamespace,
            resourceIdentity: 'qm:OrderModel'
          }
        }
      }
    } else if (path === 'models/refresh') {
      const body = requestBody
      const models = Array.isArray(body.models) ? body.models : []
      state.refreshScopes.push(models.length ? 'selected' : 'all')
      data = {
        catalogState: 'PUBLISHED',
        beforeCatalogGeneration: 'g-1',
        afterCatalogGeneration: 'g-2',
        refreshedCount: models.length || 1,
        failedCount: 0,
        warnings: []
      }
    } else if (path === 'models/validate') {
      data = {
        valid: true,
        catalogState: 'CANDIDATE_VALID',
        validFiles: 2,
        invalidFiles: 0,
        warnings: []
      }
    } else if (path === 'resources/export') {
      data = {
        resources: [{ path: 'models/orders.qm', sha256: 'mock-sha256' }]
      }
    } else if (/^query\/[^/]+\/execute$/.test(path)) {
      data = {
        items: requestNamespace === 'finance'
          ? [{ ledger: 'Revenue', amount: 512 }]
          : requestNamespace
            ? [
                { customer: 'Alice', amount: 128.5 },
                { customer: 'Bob', amount: 96 }
              ]
            : [{ scope: 'empty', amount: 0 }],
        total: requestNamespace === 'default' ? 2 : 1,
        hasNext: false,
        warnings: []
      }
    } else if (/^query\/[^/]+\/validate$/.test(path)) {
      data = { items: [], warnings: [], execution: { status: 'PLAN_READY' } }
    } else if (path === 'tables/list') {
      data = {
        dataSource: requestNamespace === 'finance' ? 'analytics' : 'analytics',
        tables: requestNamespace === 'finance'
          ? [{ schema: 'finance', name: 'invoices', type: 'TABLE' }]
          : requestNamespace
            ? [{ schema: 'public', name: 'orders', type: 'TABLE' }]
            : [{ schema: 'system', name: 'health', type: 'VIEW' }],
        warnings: []
      }
    } else if (path === 'tables/inspect') {
      data = {
        dataSource: requestBody.dataSource || 'analytics',
        schema: requestBody.schema,
        table: requestBody.table,
        tableType: 'TABLE',
        columns: [{ name: requestNamespace === 'finance' ? 'invoice_id' : 'order_id', type: 'BIGINT' }],
        primaryKey: [requestNamespace === 'finance' ? 'invoice_id' : 'order_id'],
        indexes: [],
        foreignKeys: []
      }
    } else if (path === 'sql/query') {
      data = {
        rows: [{ namespace: requestNamespace || 'empty', runtime_ok: 1 }],
        rowCount: 1,
        truncated: false,
        warnings: [],
        columns: ['namespace', 'runtime_ok']
      }
    } else if (/^compose\/(validate|preview|execute)$/.test(path)) {
      data = {
        valid: true,
        scriptKind: 'COMPOSE',
        mode: path.split('/')[1],
        value: [{ namespace: requestNamespace || 'empty', composed: true }],
        sql: 'SELECT 1',
        params: [],
        warnings: [],
        diagnostics: { namespace: requestNamespace || 'empty' }
      }
    } else if (path === 'fsscript/execute') {
      data = {
        valid: true,
        scriptKind: 'FSSCRIPT',
        mode: 'execute',
        value: [{ namespace: requestNamespace || 'empty', executed: true }],
        warnings: []
      }
    } else if (path === 'bundles' && request.method() === 'GET') {
      data = { bundles: state.bundles, warnings: [] }
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(data))
    })
  })
  return state
}

async function login(page: Page): Promise<void> {
  await page.goto('/console/')
  await page.getByLabel('Runtime API Token').fill(acceptedToken)
  await page.getByRole('button', { name: '校验并进入 Console' }).click()
  await expect(page.getByRole('heading', { name: '运行概览' })).toBeVisible()
}

async function switchNamespace(page: Page, namespace: string): Promise<void> {
  const input = page.getByLabel('当前数据与模型空间')
  await input.fill(namespace)
  await input.press('Enter')
  await input.blur()
  await expect(input).toHaveValue(namespace)
}

test.beforeEach(async ({ page }) => {
  mockStates.set(page, await mockRuntime(page))
})

test('invalid login, valid login, reload revalidation and logout', async ({ page }) => {
  const errors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error' && !message.text().includes('401 (Unauthorized)')) {
      errors.push(message.text())
    }
  })

  await page.goto('/console/')
  await page.getByLabel('Runtime API Token').fill('invalid-candidate')
  await page.getByRole('button', { name: '校验并进入 Console' }).click()
  await expect(page.getByRole('alert')).toContainText('Runtime Token 无效')
  await expect(page.getByLabel('Runtime API Token')).toHaveValue('')

  await page.getByLabel('Runtime API Token').fill(acceptedToken)
  await page.getByRole('button', { name: '校验并进入 Console' }).click()
  await expect(page.getByRole('heading', { name: '运行概览' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '运行概览' })).toBeVisible()

  await page.getByRole('button', { name: '退出 Console' }).click()
  await expect(page.getByRole('heading', { name: '连接 Runtime' })).toBeVisible()
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('foggy.runtime-console.token'))).toBeNull()
  expect(errors).toEqual([])
})

test('navigation, datasource creation and query result rendering', async ({ page }, testInfo) => {
  await login(page)

  if (testInfo.project.name.includes('mobile')) {
    await page.getByRole('button', { name: '打开主导航' }).click()
    await page.getByRole('navigation', { name: '移动端 Runtime Console 主导航' })
      .getByRole('button', { name: /数据源/ })
      .click()
  } else {
    await page.getByRole('navigation', { name: 'Runtime Console 主导航' })
      .getByRole('button', { name: /数据源/ })
      .click()
  }
  await expect(page.getByRole('heading', { name: '数据源', exact: true })).toBeVisible()
  if (!testInfo.project.name.includes('mobile')) {
    const contextRail = page.getByRole('complementary', { name: '当前页面资源导航' })
    await expect(contextRail).toContainText('Datasource List')
  }

  await page.getByRole('button', { name: '新增数据源' }).click()
  const dialog = page.getByRole('dialog', { name: '新增数据源' })
  await dialog.locator('input').nth(0).fill('warehouse')
  await dialog.locator('input').nth(1).fill('jdbc:mysql://db.internal:3306/warehouse')
  await dialog.getByRole('button', { name: '保存数据源' }).click()
  await expect(page.getByRole('table').getByText('warehouse', { exact: true })).toBeVisible()

  if (testInfo.project.name.includes('mobile')) {
    await page.getByRole('button', { name: '打开主导航' }).click()
    await page.getByRole('navigation', { name: '移动端 Runtime Console 主导航' })
      .getByRole('button', { name: /数据与模型空间/ })
      .click()
  } else {
    const topNavigation = page.getByRole('navigation', { name: 'Runtime Console 主导航' })
    await expect(topNavigation.getByRole('button')).toHaveCount(6)
    await topNavigation.getByRole('button', { name: /数据与模型空间/ }).click()
  }
  await expect(page.getByRole('heading', { name: '数据与模型空间 · default' })).toBeVisible()
  if (testInfo.project.name.includes('mobile')) {
    await page.getByRole('button', { name: /资源列表 空间索引/ }).click()
    const resourceDrawer = page.getByRole('dialog', { name: '空间索引' })
    await expect(resourceDrawer.getByRole('button', { name: /default.*1 Bundle.*analytics.*CURRENT/ })).toBeVisible()
    await resourceDrawer.getByRole('button', { name: /default.*1 Bundle.*analytics.*CURRENT/ }).click()
  }
  await page.getByRole('button', { name: /^04 空间设置$/ }).click()
  await expect(page.getByLabel('默认数据源')).toHaveValue('analytics')
  await page.getByRole('button', { name: '保存默认绑定' }).click()
  await page.getByRole('dialog', { name: '确认空间默认数据源' })
    .getByRole('button', { name: '保存绑定' })
    .click()
  await expect(page.locator('.el-message').filter({ hasText: '空间默认数据源已更新' })).toBeVisible()
  await page.getByRole('button', { name: /^03 Bundle 来源/ }).click()
  await expect(page.getByRole('heading', { name: 'runtime-console-demo' })).toBeVisible()

  await page.goto('/console/#/query')
  await expect(page.getByRole('heading', { name: '查询 DSL' })).toBeVisible()
  await page.getByLabel('QM 模型').fill('OrderModel')
  await page.getByLabel('查询 DSL JSON').fill('{"columns":["customer","amount"]}')
  await page.getByRole('button', { name: '运行查询' }).click()
  await expect(page.getByText('Alice', { exact: true })).toBeVisible()
  await expect(page.getByText('Bob', { exact: true })).toBeVisible()

  await page.goto('/console/#/compose')
  await expect(page.getByRole('heading', { name: 'Compose / CTE' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: '执行工具类型' }).getByRole('button')).toHaveCount(2)
  await page.getByRole('navigation', { name: '执行工具类型' })
    .getByRole('button', { name: /FSScript/ })
    .click()
  await expect(page.getByRole('heading', { name: 'Fsscript', exact: true })).toBeVisible()
})

test('namespace workspace keeps route, request scope, cards, drawers and keyboard focus aligned', async ({ page }, testInfo) => {
  testInfo.setTimeout(90_000)
  const state = mockStates.get(page)!
  const browserErrors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error') browserErrors.push(message.text())
  })
  page.on('pageerror', error => browserErrors.push(error.message))
  await login(page)

  if (testInfo.project.name.includes('mobile')) {
    await page.getByRole('button', { name: '打开主导航' }).click()
    await page.getByRole('navigation', { name: '移动端 Runtime Console 主导航' })
      .getByRole('button', { name: /数据与模型空间/ })
      .click()
  } else {
    await page.getByRole('navigation', { name: 'Runtime Console 主导航' })
      .getByRole('button', { name: /数据与模型空间/ })
      .click()
  }

  await expect(page).toHaveURL(/#\/namespaces\?ns=default$/)
  await expect(page.getByRole('heading', { name: '数据与模型空间 · default' })).toBeVisible()
  await expect(page.getByText('DEFAULT DATASOURCE').locator('..')).toContainText('analytics')
  await expect(page.getByText('BUNDLE SOURCES').locator('..')).toContainText('1')
  await expect(page.getByText('VISIBLE QM').locator('..')).toContainText('1')

  await page.getByRole('button', { name: /分析模型（QM）/ }).click()
  await expect(page).toHaveURL(/#\/namespaces\/models\?ns=default$/)
  const modelCard = page.getByRole('article').filter({ hasText: 'OrderModel' })
  await expect(modelCard).toContainText('订单分析')
  await expect(modelCard).toContainText('12 fields')
  await expect(modelCard).toContainText('runtime-console-demo')
  await expect(modelCard).toContainText('createdAt')
  await expect(modelCard).toContainText('SOURCE KNOWN')

  const detailButton = modelCard.getByRole('button', { name: '查看详情' })
  await detailButton.click()
  const detailDrawer = page.getByRole('dialog', { name: /模型详情/ })
  await expect(detailDrawer).toContainText('public.orders')
  await expect(detailDrawer).toContainText('当前 Runtime API 未提供 typed 模型依赖')
  await expect(detailDrawer).toContainText('"amount"')
  await expect(detailDrawer.getByLabel('模型详情摘要')).toContainText('3')
  await expect(detailDrawer.getByText('订单经营分析', { exact: true })).toBeVisible()
  await expect(detailDrawer.getByText('趋势分析', { exact: true })).toBeVisible()
  await expect(detailDrawer.getByText('fact', { exact: true })).toBeVisible()
  await detailDrawer.getByRole('button', { name: '度量', exact: true }).click()
  await expect(detailDrawer.getByText('订单金额', { exact: true })).toBeVisible()
  await expect(detailDrawer.getByText('客户', { exact: true })).toBeHidden()
  await detailDrawer.getByRole('button', { name: '全部', exact: true }).click()
  await detailDrawer.getByLabel('搜索字段').fill('毛利')
  await expect(detailDrawer.getByText('订单毛利', { exact: true })).toBeVisible()
  await expect(detailDrawer.getByText('订单金额', { exact: true })).toBeHidden()
  await expect(detailDrawer.getByText('Runtime 原始模型 JSON')).toBeVisible()
  if (testInfo.project.name.includes('mobile')) {
    const box = await detailDrawer.boundingBox()
    expect(box?.width).toBeLessThanOrEqual(420)
  }
  await page.screenshot({
    path: testInfo.outputPath(testInfo.project.name.includes('mobile')
      ? 'structured-model-detail-mobile.png'
      : 'structured-model-detail-desktop.png'),
    fullPage: true
  })
  await page.keyboard.press('Escape')
  await expect(detailDrawer).toBeHidden()
  await expect(detailButton).toBeFocused()

  await modelCard.getByLabel('选择 OrderModel').check()
  await page.getByRole('button', { name: '刷新已选' }).click()
  await page.getByRole('dialog', { name: '确认模型刷新' }).getByRole('button', { name: '确认刷新' }).click()
  await expect.poll(() => state.refreshScopes).toContain('selected')
  await page.getByRole('button', { name: '刷新全部' }).click()
  await page.getByRole('dialog', { name: '确认模型刷新' }).getByRole('button', { name: '确认刷新' }).click()
  await expect.poll(() => state.refreshScopes).toContain('all')

  await page.getByText('模型维护工具 · 路径校验与生命周期诊断').click()
  await page.getByLabel('模型路径').fill('/runtime/models/demo')
  await page.getByRole('button', { name: '校验路径' }).click()
  await expect(page.getByText('CANDIDATE_VALID')).toBeVisible()

  await page.getByRole('button', { name: /Bundle 来源/ }).click()
  const bundleCard = page.getByRole('article').filter({ hasText: 'runtime-console-demo' })
  await expect(bundleCard).toContainText('1 visible QM')
  await bundleCard.getByRole('button', { name: '高级操作' }).click()
  const advancedDrawer = page.getByRole('dialog', { name: /Bundle 高级操作/ })
  await expect(advancedDrawer.getByLabel('Bundle 原始请求 JSON')).toHaveValue(/"bundle": "runtime-console-demo"/)
  await advancedDrawer.getByRole('button', { name: '执行导出' }).click()
  await expect(advancedDrawer.getByText('models/orders.qm')).toBeVisible()
  await page.keyboard.press('Escape')

  const namespaceInput = page.getByLabel('当前数据与模型空间')
  await namespaceInput.fill('finance')
  await namespaceInput.press('Enter')
  await namespaceInput.blur()
  await expect(page).toHaveURL(/#\/namespaces\/bundles\?ns=finance$/)
  await expect(page.getByRole('heading', { name: '数据与模型空间 · finance' })).toBeVisible()
  await page.getByRole('button', { name: /分析模型（QM）/ }).click()
  await page.reload()
  await expect(page).toHaveURL(/#\/namespaces\/models\?ns=finance$/)
  await expect(page.getByRole('heading', { name: '数据与模型空间 · finance' })).toBeVisible()
  await expect.poll(() => state.namespaceHeaders.at(-1)).toBe('finance')

  await page.screenshot({
    path: testInfo.outputPath(testInfo.project.name.includes('mobile')
      ? 'namespace-workspace-mobile.png'
      : 'namespace-workspace-desktop.png'),
    fullPage: true
  })

  await page.goto('/console/#/models')
  await expect(page).toHaveURL(/#\/namespaces\/models\?ns=finance$/)
  expect(browserErrors).toEqual([])
})

test('namespace context reloads every workbench and rejects stale responses', async ({ page }, testInfo) => {
  testInfo.setTimeout(90_000)
  const state = mockStates.get(page)!
  const browserErrors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error') browserErrors.push(message.text())
  })
  page.on('pageerror', error => browserErrors.push(error.message))
  await login(page)

  await page.goto('/console/#/query')
  const queryModel = page.getByLabel('QM 模型')
  await expect(queryModel).toHaveValue('OrderModel')
  await page.getByLabel('查询 DSL JSON').fill('{"columns":["amount"]}')
  await page.getByRole('button', { name: '运行查询' }).click()
  await expect(page.getByText('Alice', { exact: true })).toBeVisible()

  await switchNamespace(page, 'finance')
  await expect(queryModel).toHaveValue('FinanceModel')
  await expect(page.getByText('Alice', { exact: true })).toBeHidden()
  await expect(page.getByLabel('当前空间 finance')).toBeVisible()
  await page.getByRole('button', { name: '运行查询' }).click()
  await expect(page.getByText('Revenue', { exact: true })).toBeVisible()
  await expect.poll(() => state.requests.some(item =>
    item.path === 'query/FinanceModel/execute' && item.namespace === 'finance'
  )).toBe(true)

  state.delayNextDefaultModels = true
  await switchNamespace(page, 'default')
  await switchNamespace(page, 'finance')
  await expect(queryModel).toHaveValue('FinanceModel')
  await page.waitForTimeout(450)
  await expect(queryModel).toHaveValue('FinanceModel')

  await page.goto('/console/#/tables')
  const tableCatalog = page.locator('#console-main .table-list')
  const tableInspector = page.locator('.split-grid > section').nth(1)
  await expect(tableCatalog.getByText('invoices', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '检查' }).click()
  await expect(tableInspector.getByText('invoice_id', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: '运行 SQL' }).click()
  const sqlResult = page.locator('.sql-panel .workbench-result')
  await expect(sqlResult.getByText('finance', { exact: true })).toBeVisible()

  await switchNamespace(page, 'default')
  await expect(tableCatalog.getByText('orders', { exact: true })).toBeVisible()
  await expect(tableInspector.getByText('invoice_id', { exact: true })).toBeHidden()
  await expect(sqlResult.getByText('finance', { exact: true })).toBeHidden()
  await expect(page.getByLabel('数据源')).toHaveValue('analytics')

  await page.goto('/console/#/compose')
  const composeScript = page.getByLabel('Compose 脚本')
  await composeScript.fill('query RetainedCompose { columns: ["id"] }')
  await page.getByRole('button', { name: '预览', exact: true }).click()
  await expect(page.getByText('"namespace": "default"')).toBeVisible()
  await switchNamespace(page, 'finance')
  await expect(composeScript).toHaveValue('query RetainedCompose { columns: ["id"] }')
  await expect(page.getByText('运行校验、预览或执行后显示结果。')).toBeVisible()
  await page.getByRole('button', { name: '预览', exact: true }).click()
  await expect(page.getByText('"namespace": "finance"')).toBeVisible()
  await expect.poll(() => state.requests.some(item =>
    item.path === 'compose/preview'
      && item.namespace === 'finance'
      && item.body.namespace === 'finance'
  )).toBe(true)

  await page.goto('/console/#/fsscript')
  await page.getByText('我已核对脚本来源').locator('input').check()
  await page.getByRole('button', { name: '展开高级工作台' }).click()
  const fsscript = page.getByLabel('Fsscript', { exact: true })
  await fsscript.fill('return { retained: true }')
  await page.getByRole('button', { name: '确认并执行' }).click()
  await page.getByRole('dialog', { name: '最终确认 Fsscript 执行' })
    .getByRole('button', { name: '确认执行' })
    .click()
  const fsscriptResult = page.locator('.fsscript-workbench .workbench-result')
  await expect(fsscriptResult.getByText('finance', { exact: true })).toBeVisible()

  await switchNamespace(page, '')
  await expect(fsscript).toHaveValue('return { retained: true }')
  await expect(page.getByText('暂无执行结果。')).toBeVisible()
  await page.getByRole('button', { name: '确认并执行' }).click()
  await page.getByRole('dialog', { name: '最终确认 Fsscript 执行' })
    .getByRole('button', { name: '确认执行' })
    .click()
  await expect(fsscriptResult.getByText('empty', { exact: true })).toBeVisible()
  await expect.poll(() => state.requests.some(item =>
    item.path === 'fsscript/execute'
      && item.namespace === ''
      && item.body.namespace === ''
  )).toBe(true)

  await page.reload()
  await expect(page.getByLabel('当前数据与模型空间')).toHaveValue('')
  await expect(page.getByRole('heading', { name: 'Fsscript', exact: true })).toBeVisible()
  expect(browserErrors).toEqual([])
})
