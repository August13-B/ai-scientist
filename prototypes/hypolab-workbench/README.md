# HypoLab 科研假设智能生成与方法知识库管理系统 V1.0.1

V1.0.1 是 V1.0 的接口修复版，修复了中文编码损坏导致“生成科学假设”按钮无响应的问题。

## 已实现功能

- 科研方法知识库的新增、搜索和删除
- 研究问题、已有证据和数据条件的结构化录入
- 一次生成 3–5 条候选科学假设
- 自动形成推理依据、技术细节和实验方法
- 创新性、逻辑自洽性、可验证性三维评分
- 假设历史记录与研究方案导出
- Cloudflare D1 持久化；本地没有数据库绑定时自动返回演示结果
- 桌面端、平板和手机响应式界面

## 本次修复

- 修复 `app/api/hypotheses/route.ts` 的中文编码与语法错误
- 修复方法库接口、数据库 schema 和迁移文件中的中文乱码
- 增加无效 JSON、空研究问题和数据库不可用时的处理
- 修复 Windows 下 `npm run dev`、`npm run build` 的启动脚本

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
```

## 目录说明

- `app/research-workbench.tsx`：主要界面与交互逻辑
- `app/api/methods/route.ts`：方法知识库接口
- `app/api/hypotheses/route.ts`：假设生成与历史接口
- `db/schema.ts`：D1 数据结构
- `drizzle/0000_hypolab.sql`：初始化迁移
- `.openai/hosting.json`：D1 逻辑绑定声明

当前版本采用结构化演示规则生成科研假设，并未调用真实大模型。
