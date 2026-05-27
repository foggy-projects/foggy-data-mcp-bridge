import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      'foggy-data-viewer/style.css': resolve(__dirname, '../frontend/dist/style.css')
    }
  },
  server: {
    host: '0.0.0.0', // 允许所有 IP 访问
    port: 5173,      // 默认端口
    strictPort: false, // 端口被占用时自动尝试下一个端口
    proxy: {
      '/data-viewer/api': {
        target: 'http://localhost:7108',
        changeOrigin: true
      }
    }
  }
})
