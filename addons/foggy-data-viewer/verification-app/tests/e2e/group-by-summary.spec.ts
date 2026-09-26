import { expect, test } from '@playwright/test'
import { readFile } from 'node:fs/promises'

const rows = Array.from({ length: 205 }, (_, index) => ({
  'salesDate$year': 2025,
  'salesDate$month': index % 3 === 0 ? 3 : 4,
  'store$caption': `门店 ${String(index + 1).padStart(3, '0')}`,
  totalAmount: (index + 1) * 100,
  orderCount: index + 1
}))

test('grouped summary renders real browser pagination and exports every matching group', async ({ page }) => {
  const requests: Array<Record<string, any>> = []
  await page.route('**/data-viewer/api/query/direct/FactSalesQueryModel', async route => {
    const request = route.request().postDataJSON() as Record<string, any>
    requests.push(request)
    const selected = rows.filter(row => (request.having ?? []).every((condition: Record<string, any>) => {
      const value = row[condition.field as keyof typeof row]
      if (condition.op === '=') return String(value) === String(condition.value)
      if (condition.op === '>') return Number(value) > Number(condition.value)
      if (condition.op === '>=') return Number(value) >= Number(condition.value)
      if (condition.op === '[]') return Number(value) >= Number(condition.value[0]) && Number(value) <= Number(condition.value[1])
      if (condition.op === 'right_like') return String(value).startsWith(String(condition.value).replace(/%/g, ''))
      if (condition.op === 'like') return String(value).includes(String(condition.value).replace(/%/g, ''))
      return true
    }))
    const start = Number(request.start ?? 0)
    const limit = Number(request.limit ?? 50)
    await route.fulfill({ json: {
      code: 200,
      data: {
        success: true,
        items: selected.slice(start, start + limit),
        total: -1,
        hasNext: start + limit < selected.length,
        start,
        limit
      }
    } })
  })

  await page.goto('/')
  await page.getByText('固定分组汇总表', { exact: true }).first().click()
  await expect(page.getByRole('heading', { name: '固定分组汇总表' })).toBeVisible()
  await expect(page.getByText('门店 001')).toBeVisible()
  expect(requests[0]).toMatchObject({
    groupBy: [{ field: 'salesDate$year' }, { field: 'salesDate$month' }, { field: 'store$caption' }],
    returnTotal: false,
    start: 0,
    limit: 20
  })
  expect(requests[0].slice).toEqual([])
  expect(requests[0].columns).toContain('sum(salesAmount) as totalAmount')
  expect(requests[0].orderBy).toEqual([
    { field: 'salesDate$year', dir: 'asc' },
    { field: 'salesDate$month', dir: 'asc' },
    { field: 'store$caption', dir: 'asc' }
  ])
  await expect(page.getByText('第 1 页')).toBeVisible()
  await expect(page.getByText(/合计/)).toHaveCount(0)
  await expect(page.getByRole('button', { name: '导出 CSV' })).toBeVisible()
  await expect(page.locator('.data-table-simple-pager')).toContainText('20 条/页')

  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page.getByText('第 2 页')).toBeVisible()
  expect(requests.at(-1)).toMatchObject({ start: 20, limit: 20, returnTotal: false })

  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出 CSV' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('group-by-summary.csv')
  const csv = await readFile(await download.path(), 'utf8')
  expect(csv.split('\r\n')).toHaveLength(206)
  await expect(page.getByRole('status')).toHaveText('已导出 205 个分组')
  expect(requests.filter(request => request.limit === 100).map(request => request.start)).toEqual([0, 100, 200])

  const monthRange = page.locator('.column-filter .filter-number-range').nth(1)
  await monthRange.locator('input').first().fill('3')
  await monthRange.locator('input').last().fill('3')
  await monthRange.locator('input').last().press('Enter')
  await expect(page.getByText('第 1 页')).toBeVisible()
  await expect.poll(() => requests.at(-1)?.having).toEqual([
    { field: 'salesDate$month', op: '[]', value: [3, 3] }
  ])
  expect(requests.at(-1)?.slice).toEqual([])

  const amountMin = page.locator('.column-filter .filter-number-range').nth(2).locator('input').first()
  await amountMin.fill('10000')
  await amountMin.press('Enter')
  await expect.poll(() => requests.at(-1)?.having).toEqual([
    { field: 'salesDate$month', op: '[]', value: [3, 3] },
    { field: 'totalAmount', op: '>=', value: 10000 }
  ])
  await expect(page.getByText('门店 100')).toBeVisible()
  await page.screenshot({ path: 'test-results/group-by-summary.png', fullPage: true })
})
