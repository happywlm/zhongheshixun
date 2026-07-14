import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-vue-components/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'path'

export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入（自动导入组件 + 样式）
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5174,  // live（注：实际端口=5174，5175/5173 都是 typo）
    open: false,
    proxy: {
      // 业务接口 → 代理到 training-api 后端（9899）
      '/api': {
        target: 'http://localhost:9899',
        changeOrigin: true,
      },
      // 登录接口 → 代理到 training-admin 后端（9898，仅 /admin/login 用）
      '/admin': {
        target: 'http://localhost:9898',
        changeOrigin: true,
      },
    },
  },
})
