# frontend — 前端交互层

Vue 3 + Vite + TypeScript + Element Plus + Vue Flow，实现 Agent 思维链可视化与人在回路交互。

## 技术栈

| 依赖 | 用途 |
|---|---|
| Vue 3 (Composition API) | 框架 |
| Vite + TypeScript | 构建与类型 |
| Element Plus | UI 组件库 |
| Vue Flow | Agent 思考链路流程图 |
| EventSource / fetch stream | SSE 流式接收 |

## 快速开始

```bash
npm install
npm run dev        # 开发（默认 http://localhost:5173）
npm run build      # 生产构建
```

## 目录结构

```
src/
├── main.ts             # 入口
├── App.vue             # 根组件（路由出口）
├── views/
│   └── PipelineView.vue    # 流水线工作台（占位）
├── components/
│   └── FlowCanvas.vue      # Vue Flow 思维链画布（占位）
└── api/
    └── sse.ts              # SSE 封装（占位）
```

## 关键功能（由前端组设计实现）

- [ ] SSE 流式接收 Agent 思考过程并渲染到 Vue Flow
- [ ] 人在回路介入按钮：评估后暂停、人类修改参数后继续
- [ ] 历史报告列表与详情页
- [ ] 文件上传（PDF 文献 / CSV 数据）

> 页面路由与接口字段定义见 [docs/api-design.md](../docs/api-design.md)，字段细节由后端组确定后联调。
