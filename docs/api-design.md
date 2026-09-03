# 接口设计（Interface Design）

> 更新时间：2026-08-05（初版）
> 本文档只固化**接口清单与职责边界**；各接口的请求/响应字段、错误码、鉴权方案由后端组（任怡名）与 AI 组（丁贾峻、张睿）设计时确定。

## 1. 接口总览

### 1.1 业务后端（backend，端口 8080）—— 对外接口

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/tasks` | 提交科研任务（科研问题 + 参数 + 可选文件上传：PDF 文献/CSV 数据） |
| GET | `/api/tasks` | 查询任务列表（分页） |
| GET | `/api/tasks/{id}` | 查询任务详情与状态 |
| GET | `/api/tasks/{id}/report` | 获取生成的研究计划（10 字段 JSON / PDF） |
| GET | `/api/tasks/{id}/stream` | SSE 流：Agent 思考过程实时推送 |
| POST | `/api/tasks/{id}/intervene` | 人在回路：提交人类审阅意见/修改参数，恢复管线 |
| DELETE | `/api/tasks/{id}` | 删除任务记录 |
| POST | `/api/upload` | 文献/数据文件上传 |

### 1.2 多智能体服务（ai-service，端口 8081）—— 内部接口

> 状态：✅ 已实现（`PipelineController`，ai-service 2026-09-02）

| 方法 | 路径 | 用途 | 状态 |
|---|---|---|---|
| POST | `/pipeline/run` | 启动七 Agent 管线（业务后端调用），返回 runId 异步执行 | ✅ |
| POST | `/pipeline/{runId}/resume` | 人在回路恢复点：提交审阅意见/修改后候选假设，继续执行 | ✅ |
| GET | `/pipeline/{runId}/stream` | SSE 流：Agent 状态事件（业务后端转发），支持历史重放 | ✅ |
| POST | `/rag/search` | 四库混合检索接口（papers/methods/datasets/evidence） | ✅ |
| GET | `/pipeline/{runId}/state` | 查询管线 State（各阶段产物快照） | ✅ |

> backend 转发（`/api/tasks/{id}/stream`、`/api/tasks/{id}/intervene`）由后端组实现。

### 1.3 前端（frontend，端口 5173）—— 页面路由（骨架）

| 路由 | 页面 | 说明 |
|---|---|---|
| `/` | 首页 | 项目介绍、任务入口 |
| `/pipeline` | 流水线工作台 | Vue Flow 思维链可视化 + 人在回路交互 |
| `/reports` | 历史报告 | 已生成研究计划列表与详情 |
| `/agents` | Agent 监控（可选） | 各 Agent 状态与日志 |

## 2. SSE 事件类型（见 architecture.md）

`agent.start` / `agent.thinking` / `agent.result` / `pipeline.pause` / `pipeline.resume` / `pipeline.done` / `pipeline.error`

## 3. 设计约定（由团队细化）

- 统一响应包装与错误码规范
- SSE 断线重连机制（EventSource 自动重连 + 心跳）
- 文件上传格式与大小限制（PDF、CSV）
- 接口鉴权（赛期可先内网直连，后续补充 Token）
- OpenAPI/Swagger 文档自动生成

## 4. Agent 执行追踪（trace 规范，2026-09-02）

> 状态：✅ 已实现（ai-service，调试可视化用；前端 Vue Flow 可消费同一 JSON）

| 端点 | 用途 | 说明 |
|---|---|---|
| GET `/pipeline/runs` | 已启动 run 列表 | `[{runId, question, done}]` |
| GET `/pipeline/{runId}/trace` | Agent 级执行追踪 JSON | 全量 input/output |
| GET `/pipeline/{runId}/debug` | 内嵌 HTML 调试页 | 轮询渲染 Agent 卡片（绿红状态/耗时/输入输出折叠），不依赖前端 |

**trace 元素结构**（`AgentTraceRecord`，每次 Agent 执行一条）：

```json
{
  "stage": "HYPOTHESIS",          // AgentStage.name()
  "agent": "HypothesisGenerationStage",
  "startTimeMillis": 1756800000000,
  "durationMillis": 8420,          // 执行耗时
  "status": "SUCCESS",             // SUCCESS | FAILED
  "errorMessage": null,            // FAILED 时的错误消息
  "input": { "knowledgeDiscovery": {...}, "literature": {...} },  // 按阶段契约的输入字段快照
  "output": { "hypotheses": [...] }                                // 阶段产物（失败为 null）
}
```

**input 契约**（框架按 AgentStage 自动取，Agent 无需感知）：① `question`；② `question+questionQuery`；
③ `question+questionQuery`（自足 RAG）；④ `knowledgeDiscovery+literature`；⑤ `hypothesis+humanFeedback`；
⑥ `evaluation`；⑦ `evaluation+experiment`。均为 null 字段省略。
