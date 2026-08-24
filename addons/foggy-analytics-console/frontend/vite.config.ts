import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  base: '/analytics-console/',
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    proxy: {
      '/analytics-console/api': 'http://127.0.0.1:8080'
    }
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts']
  }
})
