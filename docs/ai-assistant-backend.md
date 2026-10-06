# AI 助手后端修复与验证

后续核心链路重构、资源上限与深入追问改进见 [AI 助手架构说明](ai-assistant-architecture.md)。下方保留第一阶段修复与验证记录。

## 修复的原因与行为

- Java 原先调用 Python `/query`，等整段回答结束后再拼成 SSE；现在调用 `/stream`，逐事件写入并立即刷新。Python 在检索开始时发送状态，在长时间等待时每 10 秒发送心跳，模型正文直接逐块转发。
- 原先比赛搜索只读取当天全联盟记分牌；现在增加 ESPN 历史赛程与比赛详情来源，按双方球队、NBA 比赛日、赛季或年份检索。未指定日期的两队比赛问题选择最近一次可核实的直接交锋，并在回答中说明日期。夏季联赛与季后赛保留各自的范围。
- 本地比赛 SQL 改为双方直接交锋，应用日期与赛季条件，保留原有数据发布门槛。比赛统计问题不会再回退到联盟三分榜等无关数据。
- 追问由后端根据比赛日期、双方球队及已经取得的技术统计生成，最多三条；讨论过的主题会跳过，后续可继续比较罚球、助攻、禁区得分或检查来源。没有逐回合数据时不推荐无法证实的战术胜率或轮换结论。
- “这场比赛”等指代恢复到会话中已选的对阵和日期。明确的新日期优先于历史上下文。Java 读取用户自己的持久化会话，并为 Python 会话加入用户命名空间。
- 普通数据问答显式关闭 DeepSeek 思考模式，减少首字等待与内部思考耗尽正文预算的问题。分析与详细复盘使用较大的正文预算；流式截断或异常会发送 `error`，不会将不完整回答标记为完成。

历史来源验证赛事已经结束、双方 ID、主客角色、数字比分和日期；技术统计附带对应比赛的 ESPN 链接。未知赛事类型保持未知。当前数据库中未核实的记录不会因此被自动发布；统计相关性也不会被断言为战术因果。

## 接口约定

Java：`GET /api/rag/stream?question=...&session_id=...`，需要登录。

Python：`POST /stream`，JSON 包含 `question`、`top_k`、`session_id`、`conversation_history`。

SSE 事件依次包含：

1. `status`：准备与检索状态。
2. `sources`：来源、来源详情、查询与检索诊断。
3. `chunk`：增量正文，多次出现。
4. `done`：耗时与 `follow_ups`；发生异常则以 `error` 结束。

等待期间的 `: heartbeat` 是 SSE 注释。响应没有 `Content-Length`，并包含 `Cache-Control: no-cache, no-transform` 和 `X-Accel-Buffering: no`。前端按事件更新正文，完成时使用服务端追问；取消或异常时保留已收到的正文。

非流式 `/api/rag/query` 与 Python `/query` 同样返回 `follow_ups` 和 `sourceDetails`。

## 模型配置

Python 读取启动进程的环境变量：

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `RAG_LLM_THINKING` | `false` | DeepSeek 可选择开启思考模式 |
| `RAG_LLM_REASONING_EFFORT` | `low` | 开启思考时使用 `low`、`high` 或 `max` |
| `RAG_LLM_MAX_TOKENS` | `10240` | 请求输出总预算上限 |
| `RAG_LLM_TEMPERATURE` | `0.3` | 常规数据问答的默认采样温度 |

开启思考后，在正文预算之外预留 4096 个 token，仍受配置总上限限制。其他供应商不发送 DeepSeek 特有参数。相关参数参考 [DeepSeek 官方思考模式说明](https://api-docs.deepseek.com/guides/thinking_mode/)。

向量模型在后台加载；`/health` 表示 API 可用，`/ready` 表示向量检索就绪。历史比赛与 SQL 查询可在模型加载期间使用，连接会先收到状态事件。历史数据来源不可用且本地没有合格证据时，仍会诚实说明数据不足。

## 验证

2026-10-05 完成以下检查：

- Python 86 项相关回归测试通过，覆盖直接交锋、日期隔离、季后赛元数据、联网补查、无关榜单隔离、追问主题推进、上下文切换、流式截断、心跳与并发额度回收。
- Java 20 项相关测试通过，包括上游尚未完成时，下游已收到正文的真实 HTTP 转发测试，以及会话归属检查。
- 前端类型检查与 18 项相关测试通过，包括正文增量显示及服务端追问显示。
- 本地登录后的 Java → Python → 数据源 → 模型链路，验证普通分析、统计追问、2024-12-25 对阵和持久化会话中的“这场比赛”追问。已确认 2024-12-25 马刺客场 114:117 尼克斯，并获取该场技术统计。
- 最终验证包含四次流式问答与一次非流式问答，均通过，临时测试账户与会话已清理。服务预热且历史数据缓存命中后，四次流式请求的正文首块约 0.7–1.2 秒到达；普通分析收到 340 个正文片段，完整回答约 2.6 秒。首次启动的模型预热时间不包含在这组数据内。

运行命令：

```powershell
# backend/scripts
.\venv312\Scripts\python.exe -m pytest tests/test_ai_assistant_quality.py tests/test_game_archive.py tests/test_rag_stream_gate.py tests/test_rag_engine_regressions.py tests/test_search_system.py tests/test_index_provenance.py -q

# backend
.\mvnw.cmd -q '-Dtest=RAGControllerTest,RAGConversationServiceTest,RagTimeoutPropertiesTest' test

# frontend
npm.cmd run typecheck
npm.cmd run test:run -- src/views/SmartSearchView.test.ts src/utils/smartSearchHelpers.test.ts
```
