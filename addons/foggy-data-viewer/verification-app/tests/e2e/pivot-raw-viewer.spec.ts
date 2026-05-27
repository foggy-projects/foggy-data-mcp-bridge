import { expect, test } from '@playwright/test'

test('pivot raw viewer renders SOA fixture with axis evidence', async ({ page }) => {
  await page.goto('/')
  await page.getByText('Pivot Raw Viewer（新）').click()

  await expect(page.getByRole('heading', { name: 'Pivot Raw Viewer 体验验证' })).toBeVisible()
  await expect(page.getByTestId('pivot-demo-page')).toBeVisible()
  await expect(page.getByText('TMS X6 SOA 应付核销候选透视表')).toBeVisible()
  await expect(page.locator('[data-view-mode="pivotTable"]')).toBeVisible()

  await expect(page.getByText('运输费').first()).toBeVisible()
  await expect(page.getByText('服务费').first()).toBeVisible()
  await expect(page.getByText('未核销金额').first()).toBeVisible()
  await expect(page.getByText('已核销金额').first()).toBeVisible()
  await expect(page.getByText('EO-001')).toBeVisible()
  await expect(page.getByText('1,200.00')).toBeVisible()

  await expect(page.locator('.pivot-axis-page[data-axis="rows"][data-field="orderId"]')).toContainText('0-50 / 2')
  await expect(page.locator('.pivot-axis-page[data-axis="columns"][data-field="subjectCode"]')).toContainText('0-10 / 2')
  await expect(page.locator('.pivot-evidence-panel')).toContainText('domainSliceEnabled')
  await expect(page.locator('.pivot-evidence-panel')).toContainText('global_slice_and_surviving_axes')
})

test('pivot raw viewer keeps table readable on narrow viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await page.getByText('Pivot Raw Viewer（新）').click()

  const demoViewer = page.getByTestId('pivot-demo-viewer')
  await expect(demoViewer).toBeVisible()
  await expect(demoViewer.locator('.pivot-viewer')).toBeVisible()
  await expect(demoViewer.getByText('运输费').first()).toBeVisible()
  await expect(demoViewer.getByText('计算证据')).toBeVisible()

  const overflowState = await demoViewer.evaluate(element => {
    return {
      scrollWidth: element.scrollWidth,
      clientWidth: element.clientWidth
    }
  })

  expect(overflowState.clientWidth).toBeGreaterThan(0)
  expect(overflowState.scrollWidth).toBeGreaterThanOrEqual(overflowState.clientWidth)
})
