import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
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
              proxyRes.headers['cache-control'] = 'no-cache';
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
        manualChunks: {
          'echarts': ['echarts', 'vue-echarts'],
          'element-plus': ['element-plus'],
        }
      }
    }
  }
})
