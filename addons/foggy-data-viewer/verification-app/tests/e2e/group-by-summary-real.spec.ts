import { expect, test } from '@playwright/test'
import { readFile } from 'node:fs/promises'

test('grouped page talks to the local Foggy Viewer and query engine', async ({ page }) => {
  test.skip(process.env.FOGGY_VIEWER_REAL_BACKEND !== '1', 'Requires the local Foggy demo backend on port 7108')

  await page.goto('/')
  const firstResponse = page.waitForResponse(response => response.url().includes('/query/direct/FactSalesQueryModel'))
  await page.getByText('固定分组汇总表', { exact: true }).first().click()
  const firstHttp = await firstResponse
  const firstRequest = firstHttp.request().postDataJSON()
  const first = await firstHttp.json()
  expect(firstRequest.returnTotal).toBe(false)
  expect(firstRequest.groupBy).toEqual([
    { field: 'salesDate$year' },
    { field: 'salesDate$month' },
    { field: 'store$caption' }
  ])
  expect(first.code).toBe(200)
  expect(first.data.total).toBe(-1)
  expect(first.data.items).toHaveLength(20)
  expect(first.data.hasNext).toBe(true)
  expect(first.data.items[0]).toHaveProperty('salesDate$year')
  expect(first.data.items[0]).toHaveProperty('totalAmount')
  await expect(page.getByText('第 1 页')).toBeVisible()

  const nextResponse = page.waitForResponse(response => response.url().includes('/query/direct/FactSalesQueryModel'))
  await page.getByRole('button', { name: '下一页' }).click()
  expect((await (await nextResponse).json()).data.items).toHaveLength(20)
  await expect(page.getByText('第 2 页')).toBeVisible()

  const monthRange = page.locator('.column-filter .filter-number-range').nth(1)
  await monthRange.locator('input').first().fill('3')
  await monthRange.locator('input').last().fill('3')
  const filteredResponse = page.waitForResponse(response =>
    response.url().includes('/query/direct/FactSalesQueryModel') &&
    response.request().postDataJSON()?.having?.some((condition: Record<string, unknown>) => condition.op === '[]')
  )
  await monthRange.locator('input').last().press('Enter')
  const filteredHttp = await filteredResponse
  const filteredRequest = filteredHttp.request().postDataJSON()
  const filtered = await filteredHttp.json()
  expect(filteredRequest.having).toEqual([{ field: 'salesDate$month', op: '[]', value: [3, 3] }])
  expect(filteredRequest.slice).toEqual([])
  expect(filtered.code).toBe(200)
  expect(filtered.data.items.length).toBeGreaterThan(0)
  expect(filtered.data.items.every((row: Record<string, unknown>) => row['salesDate$month'] === 3)).toBe(true)
  await expect(page.getByText('第 1 页')).toBeVisible()

  const amountMin = page.locator('.column-filter .filter-number-range').nth(2).locator('input').first()
  await amountMin.fill('10000')
  const measureResponse = page.waitForResponse(response =>
    response.url().includes('/query/direct/FactSalesQueryModel') &&
    response.request().postDataJSON()?.having?.some((condition: Record<string, unknown>) => condition.field === 'totalAmount')
  )
  await amountMin.press('Enter')
  const measuredHttp = await measureResponse
  const measuredRequest = measuredHttp.request().postDataJSON()
  const measured = await measuredHttp.json()
  expect(measuredRequest.having).toEqual([
    { field: 'salesDate$month', op: '[]', value: [3, 3] },
    { field: 'totalAmount', op: '>=', value: 10000 }
  ])
  expect(measured.code).toBe(200)
  expect(measured.data.items.every((row: Record<string, unknown>) => Number(row.totalAmount) >= 10000)).toBe(true)

  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出 CSV' }).click()
  const download = await downloadPromise
  const csv = await readFile(await download.path(), 'utf8')
  expect(csv).toContain('salesDate$year,salesDate$month,store$caption,totalAmount,orderCount')
  expect(csv.split('\r\n').length).toBeGreaterThan(3)
  await expect(page.getByRole('status')).toContainText('已导出')
  await page.screenshot({ path: 'test-results/group-by-summary-real.png', fullPage: true })
})
