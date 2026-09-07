import { defineConfig } from 'vite'
import { resolve } from 'node:path'

export default defineConfig({
  root: resolve(__dirname, 'http-auth'),
  resolve: { alias: { 'foggy-data-viewer': resolve(__dirname, '../frontend/dist/index.js') } },
  build: {
    outDir: resolve(__dirname, 'dist-http-auth'),
    emptyOutDir: true,
    manifest: true,
    rollupOptions: { output: { manualChunks(id) {
      if (id.includes('/frontend/dist/') || id.includes('/node_modules/')) return 'sdk-vendor'
    } } }
  }
})
