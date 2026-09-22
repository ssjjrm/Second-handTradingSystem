import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],

  resolve: {
    // 让 import xxx from '@/api/auth' 能解析到 src/api/auth
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },

  optimizeDeps: {
    // ⚠️ 必须显式指定，否则 Vite 会把 public/verify/*.html 也当成入口去扫描依赖，
    //    每次启动都刷一堆 "Failed to run dependency scan" 报错 ——
    //    那些验证页是纯静态 HTML，本来就不参与打包。
    entries: ['index.html']
  },

  server: {
    // 5174 避开参考项目的 5173，两个前端可以同时跑
    port: 5174,
    // 端口被占用就直接报错，不要偷偷换端口 ——
    // 否则你以为访问的是 5174，其实跑在别的地方
    strictPort: true,

    // 代理：浏览器请求 /api/xxx → 转发给后端 8081。
    // 配了它，浏览器眼里所有请求都是同源的，跨域问题根本不会出现。
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
