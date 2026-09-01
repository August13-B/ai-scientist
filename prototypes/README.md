# 交互原型

本目录保存与主系统隔离的产品验证原型。原型用于验证交互闭环和演示方案，不替换 `frontend/`、`backend/`、`ai-service/` 或 `data/` 中的正式实现。

## HypoLab Workbench V1.0.1

路径：`prototypes/hypolab-workbench/`

该版本提供：

- 研究问题、已有证据与数据条件的结构化录入；
- 科研方法知识库的新增、搜索和删除；
- 一次生成 3–5 条候选科学假设；
- 推理依据、技术细节、实验方法和三维评分；
- 假设记录与研究方案导出；
- Cloudflare D1 持久化声明；无数据库绑定时自动返回演示结果。

### 本地运行

要求 Node.js 22.13 或更高版本：

```bash
cd prototypes/hypolab-workbench
npm install
npm run dev
```

浏览器打开终端显示的本地地址，通常为 `http://localhost:3000`。

### 与主系统的边界

- 主系统技术栈为 Vue 3、Spring Boot、LangChain4j、MySQL 与向量数据库；
- 本原型技术栈为 React、Next.js/Vinext、Cloudflare Workers 与 D1；
- 两者架构不同，因此采用独立目录保留，避免覆盖主系统前端或固化尚未定稿的后端接口；
- 原型中的生成逻辑用于无密钥演示，不等同于七 Agent 管线的正式科研推理结果。
