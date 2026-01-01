import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  base: '/data-viewer/',
  build: {
    outDir: resolve(__dirname, '../src/main/resources/static'),
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks: {
          'vxe-table': ['vxe-table', 'xe-utils'],
          'vue': ['vue']
        }
      }
    }
  },
  server: {
    proxy: {
      '/data-viewer/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  }
})
