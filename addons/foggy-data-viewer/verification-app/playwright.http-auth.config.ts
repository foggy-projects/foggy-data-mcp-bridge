import { defineConfig, devices } from '@playwright/test'
export default defineConfig({
  testDir: './tests/http-auth',
  use: {
    baseURL: 'http://127.0.0.1:18175',
    ...devices['Desktop Chrome'],
    channel: process.env.PLAYWRIGHT_CHANNEL || undefined
  },
  webServer: {
    command: 'npx vite preview --config vite.http-auth.config.ts --host 127.0.0.1 --port 18175 --strictPort',
    url: 'http://127.0.0.1:18175',
    reuseExistingServer: false
  }
})
