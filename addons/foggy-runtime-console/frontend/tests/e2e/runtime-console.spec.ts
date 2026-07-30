import { expect, test, type Page, type Route } from '@playwright/test'

interface MockState {
  datasources: Array<Record<string, unknown>>
}

const acceptedToken = 'e2e-runtime-token'

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
    }]
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
        namespaceBindings: { default: 'analytics' }
      }
    } else if (path === 'datasources' && request.method() === 'GET') {
      data = { datasources: state.datasources, warnings: [] }
    } else if (path === 'datasources' && request.method() === 'POST') {
      const body = await jsonBody(route)
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
    } else if (path === 'models') {
      data = {
        format: 'json',
        content: '{}',
        data: {
          models: ['OrderModel'],
          count: 1,
          items: [{
            model: 'OrderModel',
            caption: '订单分析',
            description: '用于订单趋势与履约分析。',
            sourceKnown: true,
            bundleName: 'sales',
            fieldCount: 12,
            primaryTimeField: 'createdAt'
          }]
        }
      }
    } else if (path === 'query/OrderModel/execute') {
      data = {
        items: [
          { customer: 'Alice', amount: 128.5 },
          { customer: 'Bob', amount: 96 }
        ],
        total: 2,
        hasNext: false,
        warnings: []
      }
    } else if (path === 'query/OrderModel/validate') {
      data = { items: [], warnings: [], execution: { status: 'PLAN_READY' } }
    } else if (path === 'tables/list') {
      data = {
        dataSource: 'analytics',
        tables: [{ schema: 'public', name: 'orders', type: 'TABLE' }],
        warnings: []
      }
    } else if (path === 'bundles') {
      data = { bundles: [], warnings: [] }
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

test.beforeEach(async ({ page }) => {
  await mockRuntime(page)
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
      .getByRole('button', { name: /数据源与命名空间/ })
      .click()
  } else {
    await page.getByRole('complementary', { name: 'Runtime Console 主导航' })
      .getByRole('navigation')
      .getByRole('button', { name: /数据源与命名空间/ })
      .click()
  }
  await expect(page.getByRole('heading', { name: '数据源与命名空间' })).toBeVisible()

  await page.getByRole('button', { name: '新增数据源' }).click()
  const dialog = page.getByRole('dialog', { name: '新增数据源' })
  await dialog.locator('input').nth(0).fill('warehouse')
  await dialog.locator('input').nth(1).fill('jdbc:mysql://db.internal:3306/warehouse')
  await dialog.getByRole('button', { name: '保存数据源' }).click()
  await expect(page.getByRole('table').getByText('warehouse', { exact: true })).toBeVisible()

  await page.goto('/console/#/query')
  await expect(page.getByRole('heading', { name: '查询 DSL' })).toBeVisible()
  await page.getByLabel('QM 模型').fill('OrderModel')
  await page.getByLabel('查询 DSL JSON').fill('{"columns":["customer","amount"]}')
  await page.getByRole('button', { name: '运行查询' }).click()
  await expect(page.getByText('Alice', { exact: true })).toBeVisible()
  await expect(page.getByText('Bob', { exact: true })).toBeVisible()
})
