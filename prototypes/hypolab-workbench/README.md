# HypoLab 科研假设智能生成与方法知识库管理系统 V1.0.1

V1.0.1 是 V1.0 的接口修复版，修复了中文编码损坏导致“生成科学假设”按钮无响应的问题。

## 已实现功能

- 科研方法知识库的新增、搜索和删除
- 研究问题、已有证据和数据条件的结构化录入
- 通过团队统一后端或通义千问生成 1–5 条候选科学假设
- 接收上游结构化 `EvidenceItem`，不在本模块重复解析图片或论文
- 展示生成依据、证据 ID、验证方案、创新性、可行性和置信度
- 假设历史记录与研究方案导出
- Cloudflare D1 持久化；保存失败返回明确错误，不生成模拟 ID
- 桌面端、平板和手机响应式界面

## 假设生成服务配置

优先配置团队统一接口：

```text
TEAM_HYPOTHESIS_API_URL=https://团队接口/api/v1/hypotheses/generate
TEAM_HYPOTHESIS_API_TOKEN=可选的Bearer令牌
```

未配置团队接口时，可以由服务端调用通义千问：

```text
QWEN_API_KEY=服务端密钥
QWEN_MODEL=qwen-plus
QWEN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
```

密钥只能配置在服务端运行环境，不得提交到仓库或传给浏览器。两种服务均未配置时，接口返回 `503 GENERATOR_NOT_CONFIGURED`，不会退回固定模板。

## 接口说明

`POST /api/hypotheses` 接收：`researchQuestion`、`researchGap`、`evidenceItems`、`dataConditions`、`constraints`、`candidateCount`。每个 `EvidenceItem` 至少包含唯一 `id`、`type` 和 `content`。

成功响应的每条假设包含 `statement`、`rationale`、`citedEvidenceIds`、`novelty`、`feasibility`、`confidence` 和 `validationPlan`。服务会拒绝不存在于请求中的证据 ID。

错误码：

- `400 INVALID_REQUEST`：输入缺失或格式错误
- `422 INVALID_MODEL_OUTPUT`：模型输出不完整或虚构证据 ID
- `502 AGENT_SERVICE_FAILED`：上游 Agent/模型调用失败
- `503 GENERATOR_NOT_CONFIGURED`：未配置真实生成服务
- `500 HYPOTHESIS_PERSIST_FAILED`：生成成功但 D1 保存失败，未创建记录

## 本次修复

- 修复 `app/api/hypotheses/route.ts` 的中文编码与语法错误
- 修复方法库接口、数据库 schema 和迁移文件中的中文乱码
- 增加无效 JSON、空研究问题和数据库不可用时的处理
- 修复 Windows 下 `npm run dev`、`npm run build` 的启动脚本
- 移除前端和后端的固定假设模板与模拟成功兜底
- 补充 Cloudflare Worker/D1 类型声明及 `npm run typecheck`

## 本地运行

要求 Node.js 22.13 或更高版本：

```powershell
npm install
npm run dev
```

打开终端显示的本地地址，通常为 `http://localhost:3000`。

生产构建：

```powershell
npm run build
npm run typecheck
npm run test:agent
```

## 目录说明

- `app/research-workbench.tsx`：主要界面与交互逻辑
- `app/api/methods/route.ts`：方法知识库接口
- `app/api/hypotheses/route.ts`：假设生成与历史接口
- `lib/hypothesis-agent.ts`：团队后端/通义千问适配器
- `lib/hypothesis-contract.ts`：输入输出契约与证据引用校验
- `db/schema.ts`：D1 数据结构
- `drizzle/0000_hypolab.sql`：初始化迁移
- `.openai/hosting.json`：D1 逻辑绑定声明

当前版本不再包含假设模板；只有真实生成服务成功返回且 D1 保存成功时，前端才会显示新记录。
