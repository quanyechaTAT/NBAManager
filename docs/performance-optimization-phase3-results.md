# NBAManager 第三轮性能优化与三轮汇总

日期：2026-10-07。第三轮完成热点读取合并、共享比赛缓存版本、同步后台预算、RAG 增量编码与快照发布，以及 HTTP 缓存和流式代理验证。本文汇总前两轮有用结果，替代此前两份实施报告；总体设计保留在 `performance-optimization-plan.md`。

## 第三轮改动与缓存含义

**热点读取合并。** `CoalescingCacheManager` 包装已有 Redis 缓存，只覆盖四类公开比赛 DTO 和球员列表。相同 key 的并发未命中共享一次查库；默认每个缓存最多 128 个同时回源任务，跟随请求最多等待 10 秒。即使清空缓存，尚未完成的旧回源仍计入预算，避免失效风暴绕过上限。清空或删除后，旧查询不能重新填入缓存。失败和 null 不缓存，后续可重试。Redis 故障时回落数据库，短暂退避 2 秒，避免同一请求反复等待 Redis 超时。[Spring 缓存同步说明](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html)

这减少同一服务进程中的重复 SQL，并保持原有 Redis TTL、序列化和事务失效语义。它不提供跨 JVM 的分布式回源锁，其他公开或个人接口没有被自动纳入共享合并。

**比赛版本共享。** 比赛缓存 key 使用 Redis 中的共享 UUID。数据库事务提交后更新版本；另一后端实例的下一次读取使用新 key，旧请求的迟到结果即使写入 Redis，也不会被新版本读取。Redis 故障时使用隔离的本地版本；恢复后先发布故障期间未能发布的写入。每次 key 解析增加一次 Redis 读取。技术统计、首页等其他缓存仍依靠主动失效和短 TTL，未扩展成全项目强一致缓存。

**后台同步预算。** 手动、定时、登录补偿作业共用已有隔离执行器，默认仍为核心 2、最大 4 个线程、16 个排队任务。新增环境参数，并限制最大 8 个线程、128 个队列任务；饱和仍沿用 `SYNC_QUEUE_FULL`，不会在 HTTP 线程执行同步。这里没有凭空提高并发数。同步中心隐藏时停止定时刷新并中止当前概览读取，恢复时立即读取状态和历史；迟到响应不会覆盖新状态，提交/取消命令仍保留。

**RAG 增量编码与发布。** 构建完整的新 Chroma 快照，但仅对文本或模型配置版本变化的文档运行 embedding；未变化文本及只有元数据变化的记录复用旧向量。首次升级没有模型标记的旧向量会重新编码一次。新快照完成 upsert、去除过时已发布文档并校验数量后，原子替换发布指针，同时推进检索缓存版本。重建期间继续查询旧快照；SQL、编码、写入失败都保留旧数据。`clear_first` 不再提前清空服务中的索引；`--no-clear` 保留不参与检索的旧契约记录，同 ID 的核实数据正确替换旧记录。

同一索引目录使用操作系统文件锁拒绝并发写者，崩溃会释放锁。读取进程在下一次访问时刷新 collection，已在执行的读取保留旧句柄。保留上一快照；更旧的、属于本 collection 的快照经过 10 分钟宽限后，在后续成功构建时回收。读写进程需共同升级；此机制面向同机或共享目录，不是跨机索引复制。绕过项目 VectorStore 直接修改 Chroma，仍需同步管理发布版本。

**HTTP 与代理。** Java 对实际存在、名称带构建 hash 的资源返回一年 `immutable` 缓存；入口 HTML 使用 `no-cache` 和内容 ETag，可返回 304。入口变化后浏览器重新获取 HTML，新资源 URL 随 hash 更新。API 和用户上传保留原策略，缺失资源不返回 SPA 页面，也不获得长期缓存。[Cache-Control 语义](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Cache-Control)

新增 `deploy/nginx.conf` 和使用说明：静态站点、大 JSON gzip、WebSocket 转发；RAG SSE 关闭缓冲与 gzip，保留 `no-cache, no-transform` 和 600 秒读取预算。Vite 代理也保留后端的流缓存头。默认仅监听本机 18080，上线需按环境配置 TLS、域名和端口，并保留上一构建的 hash 资源一段时间。该配置已经本地验证，没有宣称完成生产发布。[Nginx 流代理说明](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_buffering)

## 本轮实测

| 验证 | 结果 | 范围 |
| --- | --- | --- |
| 同 key 冷读 | 32 个并发共享 1 次加载 | 自动化缓存测试，另覆盖故障、超时、清空和容量 |
| Java 实际读取 | 20 个并发响应一致、0 错误 | 隔离随机筛选 key 的空球员页，不清理现有缓存 |
| 实际 Redis 策略 | 球员列表 TTL 60 秒，比赛共享版本存在 | 同时验证近期完赛 TTL 上限；缓存生命周期测试验证指定 TTL |
| 增量索引 CPU 样本 | 首次 21,412.30 ms；未变化 252.21 ms | 100 条真实 SQL 文档、模型已加载、隔离 Chroma |
| 上述模型加载 | 24,676.38 ms | 独立记录，不计入两次构建比较 |
| 索引编码数量 | 首次 100，第二次 0；复用 100 | SQL 源当时为 1,013 条，仅抽样，不替换服务索引 |
| Nginx SSE | 三段约 3.52 / 303.77 / 604.10 ms 到达 | 可控上游每 300 ms 发一段，不调用外部模型 |
| Nginx 传输 | 入口 304；hash 缓存；大 JSON gzip；个人接口 no-store | 缺失资源/API 404、未鉴权 403、SSE 无压缩 |
| Java 静态服务 | HTML 304，hash 资源 immutable | 真实打包后端，API 鉴权和错误状态保留 |

这些是功能与局部性能证据，不能推算生产容量、完整 AI 首字耗时或所有索引构建的加速比例。仍需真实网络、混合访问及同步压力下的 P95、CPU、数据库等待测量；当前没有可用浏览器性能追踪工具，因此未报告 LCP、INP、CLS。

## 前两轮保留结果

| 范围 | 已实施与验证 |
| --- | --- |
| 首屏与交互 | Element Plus 按需、详情按 gameId、重型标签按需加载、共享有界查询与取消、隐藏页停止比赛刷新、403 保留登录 |
| 资源样本 | HTML 直接引用 JS gzip 从 363.46 降至约 82.77 KiB，CSS 从 62.46 降至约 17.41 KiB；是本地估算，不是整个路由流量 |
| 单场读取 | 从全部 105 场约 39,047 B 改为单场约 477 B；未开赛比分仍为 null，缺失比赛 404 |
| 数据库 | 首页榜单取前 9，COUNT 统计；得分榜先按发布规则过滤再分页，稳定排序；混合方向索引已应用，EXPLAIN 验证无 filesort |
| RAG 查询缓存 | embedding 512 条/1 小时，检索证据 256 条/60 秒；范围、日期、模型和索引版本参与 key，不缓存私人回答、空证据或失败 |
| 检索样本 | 旧轮在 2,204 条索引上，模型加载后的一个检索计划为 3,602.93 ms，两次命中为 1.61 / 1.60 ms；未调用完整 LLM 回答 |
| 长回放 | 1,551 条真实 PARTIAL 事件仅渲染约 12–14 行，选中定位偏差约 0.5 px；保留键盘焦点、筛选和数据质量 |
| 流式文本 | 首段立即显示，后续 40 ms 批量刷新，完成/错误/取消提交尾段，忽略旧流迟到消息 |

## 回归与复测

- 前端：38 个文件、191 项测试通过，类型检查与生产构建通过。
- Python：197 项测试与 3 个子测试通过；快照测试使用临时真实 Chroma，覆盖失败保留、读者切换、模型变化、元数据变化、同 ID 旧数据升级和写者互斥。
- Java：16 组相关测试、74 项通过；`package -DskipTests` 成功。覆盖原有 TTL/失效、同步作业、并发合并、过载及 HTML 缓存。
- 本地后端已更新并启动，RAG 已加载新代码，模型就绪、索引 ready，保留原 2,204 条服务索引。浏览器同步中心可刷新概览和历史，AI 页面可打开；页面中的既有上游任务失败记录继续如实显示。
- 原有 ECharts 大 chunk 提醒、模拟 Logo/Happy DOM 网络告警、Starlette 弃用提醒仍存在，未导致测试失败。

在项目根目录使用工作区 Python 执行 `scripts/verify-runtime.py`、`scripts/measure-rag-index.py --sample-size 100`。Nginx 验证执行 `scripts/verify-proxy.py --nginx <官方 nginx 可执行文件路径>`；前端体积使用 `node scripts/measure-frontend-bundle.mjs`。测量脚本不打印凭据，不触发模型回答或手动同步。

调节变量已写入 `.env.example`：`CACHE_MAX_CONCURRENT_LOADS`、`CACHE_LOAD_WAIT_MILLIS`、`SYNC_EXECUTOR_CORE_SIZE`、`SYNC_EXECUTOR_MAX_SIZE`、`SYNC_EXECUTOR_QUEUE_CAPACITY`；前两轮的 RAG 缓存容量/TTL 和模型缓存版本变量继续有效。

原始证据位于 Git 忽略目录 `test-evidence/performance/`，包括 `phase3-proxy.json`、`phase3-runtime.json`、`phase3-index.json`、`phase3-bundle.json` 与 `phase3-sync.jpg`。此前有用的测量 JSON/截图保留。本轮一次性下载与隔离索引已移出项目，暂存于 `C:/Users/86139/AppData/Local/Temp/nbamanager-phase3-measurements-20261007`；自动审批拒绝永久删除，因此采用可恢复的清理。前两轮报告已由本文替代并删除。
