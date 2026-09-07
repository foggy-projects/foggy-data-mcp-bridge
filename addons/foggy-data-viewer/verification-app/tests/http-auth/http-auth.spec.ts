import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'

test('production vendor loads before host configuration; every API uses current auth', async ({ page }) => {
  const manifest = JSON.parse(readFileSync('dist-http-auth/.vite/manifest.json', 'utf8'))
  expect(Object.values(manifest).some((entry: any) => entry.file.includes('sdk-vendor'))).toBe(true)
  const headers: Record<string, string>[] = []
  let status = 200
  await page.route('**/data-viewer/api/**', async route => {
    headers.push(await route.request().allHeaders())
    await route.fulfill({ status, json: { code: status, data: { fields: {} } } })
  })
  await page.goto('/')
  await page.waitForFunction(() => Boolean((window as any).httpAuth))
  await page.evaluate(() => {
    ;(window as any).httpAuth.configure()
    ;(window as any).httpAuth.configure()
  })
  for (const token of ['A', 'B', null]) {
    const result = await page.evaluate(async token => {
      const harness = (window as any).httpAuth
      harness.setToken(token)
      return harness.run()
    }, token)
    expect(result).toEqual(Array(17).fill('ok'))
    expect(headers).toHaveLength(17)
    for (const request of headers.splice(0)) {
      expect(request.authorization).toBe(token ? `Bearer ${token}` : undefined)
      expect(request['x-ns']).toBe('tms-biz')
    }
  }
  status = 401
  expect(await page.evaluate(() => (window as any).httpAuth.run())).toEqual(Array(17).fill(401))
  const notifications = await page.evaluate(() => (window as any).httpAuth.notifications())
  expect(notifications).toHaveLength(17)
  for (const context of notifications) expect(Object.keys(context).sort()).toEqual(['method', 'path', 'status'])
})
