import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { acceptanceBackend } from './acceptance/backend'
export default defineConfig({
  plugins: [vue(), acceptanceBackend()],
  resolve: { dedupe: ['vue', 'element-plus', 'vxe-table', 'vxe-pc-ui'] },
  server: { host: '127.0.0.1', port: 53175, strictPort: true }
})
