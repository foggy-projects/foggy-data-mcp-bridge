import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  base: './',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '127.0.0.1',
    port: 5177,
    proxy: {
      '/api/v1': {
        target: process.env.FOGGY_RUNTIME_PROXY || 'http://127.0.0.1:8080',
        changeOrigin: false
      }
    }
  },
  test: {
    environment: 'happy-dom',
    include: ['tests/unit/**/*.test.ts']
  }
})
