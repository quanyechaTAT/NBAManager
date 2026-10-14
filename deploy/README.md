# 本地预览与生产代理

先在 `frontend` 执行 `npm run build`。创建 `deploy/logs`，再运行 `nginx -p <项目绝对路径>/deploy/ -c nginx.conf`（路径用 `/` 并保留末尾 `/`）。默认只监听本机 `127.0.0.1:18080`，前端由 `frontend/dist` 提供，API 转发到 8080。执行 `nginx -t` 时使用同样的 `-p`、`-c` 参数。

上线前按部署环境配置域名、监听端口、TLS 和后端地址。`nginx.conf` 不含凭据，也不启动 Vite。发布时保留上一构建的 `assets/` 文件，至少覆盖旧页面可能继续访问的时间，随后再清理；HTML 始终重新验证，带内容 hash 的资源可以缓存一年。个人接口继续遵从后端 `no-store`，这里没有启用 API 共享缓存。

只有 RAG 的 `/api/rag/stream` 使用 600 秒读取预算，关闭响应缓冲和 gzip。普通 API 使用 30 秒预算，WebSocket/SockJS 转发升级头。gzip 对大 JSON 和静态资源生效；其 CPU 成本仍需在实际服务器、网络与混合负载下测量。[Nginx 流代理说明](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_buffering)

运行 `python scripts/verify-proxy.py --nginx <nginx可执行文件绝对路径>` 可验证当前配置。探针使用随机本地端口和可控上游，检查 ETag/304、hash 资源、缺失资源、鉴权透传、JSON 压缩及分段 SSE；它不会调用模型或执行数据同步。结果保存于 Git 忽略目录 `test-evidence/performance/phase3-proxy.json`，临时代理会退出。

Java 单独提供打包前端时也使用相同的 HTML/hash 缓存策略，无需依赖 Nginx 才能生效。修改代码后重建 jar 才会更新 Java 中的资源与配置。
