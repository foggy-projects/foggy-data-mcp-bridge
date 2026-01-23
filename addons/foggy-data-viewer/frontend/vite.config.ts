import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // 库模式配置
  if (mode === 'lib') {
    return {
      plugins: [vue()],
      resolve: {
        alias: {
          '@': resolve(__dirname, 'src')
        }
      },
      build: {
        lib: {
          entry: resolve(__dirname, 'src/index.ts'),
          name: 'FoggyDataViewer',
          fileName: (format) => `index.${format === 'es' ? 'js' : format}`
        },
        rollupOptions: {
          // 确保外部化依赖，不打包进库
          external: ['vue', 'vxe-table', 'xe-utils', 'axios', 'element-plus'],
          output: {
            globals: {
              vue: 'Vue',
              'vxe-table': 'VxeTable',
              'xe-utils': 'XEUtils',
              'axios': 'axios',
              'element-plus': 'ElementPlus'
            },
            // 为每个外部化的依赖提供一个全局变量
            assetFileNames: (assetInfo) => {
              if (assetInfo.name === 'style.css') return 'style.css'
              return assetInfo.name || ''
            }
          }
        },
        outDir: 'dist',
        emptyOutDir: true
      }
    }
  }

  // 应用模式配置（默认）
  return {
    plugins: [vue()],
    base: '/data-viewer/',
    build: {
      outDir: resolve(__dirname, '../src/main/resources/static/data-viewer'),
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
      port: 5193,
      proxy: {
        '/data-viewer/api': {
          target: 'http://localhost:7108',
          changeOrigin: true
        }
      }
    },
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      }
    }
  }
})
