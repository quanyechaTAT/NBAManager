import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import ElementPlus from 'unplugin-element-plus/vite'

export default defineConfig({
  plugins: [
    vue(),
    Components({ dirs: [], dts: false, resolvers: [ElementPlusResolver()] }),
    // Include styles for explicitly imported services such as ElMessage and ElMessageBox.
    ElementPlus(),
  ],
  optimizeDeps: {
    // Prepare export dependencies before lazy route navigation can trigger a new scan.
    include: ['xlsx', 'file-saver'],
  },
  resolve: {
    alias: {
      '@': '/src',
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
    watch: {
      // WSL 访问 Windows 挂载盘（/mnt/*）时 inotify 收不到变更事件，需要轮询
      usePolling: process.env.VITE_POLLING === '1',
      interval: 600,
    },
    proxy: {
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // SSE 流式响应需要禁用缓冲
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq, req) => {
            // 对 SSE 请求设置特殊头部
            if (req.url?.includes('/stream')) {
              proxyReq.setHeader('Accept', 'text/event-stream');
              proxyReq.setHeader('Cache-Control', 'no-cache');
              proxyReq.setHeader('Connection', 'keep-alive');
            }
          });
          proxy.on('proxyRes', (proxyRes, req) => {
            // 对 SSE 响应禁用缓冲
            if (req.url?.includes('/stream')) {
              proxyRes.headers['cache-control'] = proxyRes.headers['cache-control'] || 'no-cache, no-transform';
              proxyRes.headers['x-accel-buffering'] = 'no';
              // 强制刷新
              proxyRes.headers['connection'] = 'keep-alive';
            }
          });
        },
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    // 安全报告 §十五 M7：生产构建不产出 source map（默认本就为 false，这里显式钉死，
    // 防止未来有人打开后把前端源码带进部署产物）
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks(id) {
          // Assign chart internals only. Including vue-echarts also pulls shared Vue
          // runtime into this chunk and makes the entry preload charts unnecessarily.
          const normalized = id.replace(/\\/g, '/')
          if (normalized.includes('/node_modules/echarts/') || normalized.includes('/node_modules/zrender/')) {
            return 'echarts'
          }
        },
      }
    }
  }
})
