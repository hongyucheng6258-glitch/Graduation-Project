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

// 管理端 base 为 /admin/：把不带 /admin 前缀的访问（如 /login、/dashboard、/）
// 自动 302 重定向到 /admin + 原路径，避免 Vite 展示"基础路径为 /admin/"的英文提示页。
function adminBaseRedirectPlugin() {
  const redirect = (server) => {
    server.middlewares.use((req, res, next) => {
      const path = (req.url || '').split('?')[0]
      // 放行：已是 /admin 前缀、/api 代理接口、vite 内部资源、模块请求、带扩展名的静态资源
      if (
        path.startsWith('/admin') ||
        path.startsWith('/api') ||
        path.startsWith('/@') ||
        path.startsWith('/src') ||
        path.startsWith('/node_modules') ||
        /\.[a-zA-Z0-9]+$/.test(path)
      ) {
        next()
        return
      }
      const target = '/admin' + (path === '/' || path === '' ? '/' : path)
      res.statusCode = 302
      res.setHeader('Location', target)
      res.end()
    })
  }
  return {
    name: 'admin-base-redirect',
    configureServer: redirect,
    configurePreviewServer: redirect
  }
}

export default defineConfig({
  base: '/admin/',
  plugins: [vue(), unloadPolicyPlugin(), adminBaseRedirectPlugin()],
  optimizeDeps: {
    include: ['vue', 'vue-router', 'pinia', 'element-plus']
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router', 'pinia'],
          'vendor-element': ['element-plus', '@element-plus/icons-vue'],
          'vendor-chart': ['echarts']
        }
      }
    }
  },
  server: {
    port: 5174,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
