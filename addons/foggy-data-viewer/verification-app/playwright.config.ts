import { defineConfig, devices } from '@playwright/test'

const port = Number(process.env.FOGGY_VIEWER_E2E_PORT || 53174)

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 30_000,
  expect: {
    timeout: 8_000
  },
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    trace: 'retain-on-failure'
  },
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${port} --strictPort`,
    url: `http://127.0.0.1:${port}`,
    reuseExistingServer: false,
    timeout: 120_000
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        ...(process.env.FOGGY_VIEWER_E2E_CHANNEL === 'msedge' ? { channel: 'msedge' as const } : {})
      }
    }
  ]
})
