import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 允许文档使用 unload/pagehide 事件，避免新版 Chrome 控制台输出
// "[Violation] Permissions policy violation: unload is not allowed in this document."
// （vue-router 的滚动位置保存会注册 pagehide 监听器）
function unloadPolicyPlugin() {
  return {
    name: 'permissions-policy-unload',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        res.setHeader('Permissions-Policy', 'unload=(self)')
        next()
      })
    },
    configurePreviewServer(server) {
      server.middlewares.use((req, res, next) => {
        res.setHeader('Permissions-Policy', 'unload=(self)')
        next()
      })
    }
  }
}

export default defineConfig({
  plugins: [vue(), unloadPolicyPlugin()],
  optimizeDeps: {
    include: ['vue', 'vue-router', 'pinia', 'element-plus', 'highlight.js']
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router', 'pinia'],
          'vendor-element': ['element-plus', '@element-plus/icons-vue'],
          'vendor-markdown': ['markdown-it', 'highlight.js']
        }
      }
    }
  },
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
