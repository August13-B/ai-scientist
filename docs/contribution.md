# 开发规范（Contribution Guide）

> 更新时间：2026-08-05（初版）

## 1. 分支策略

采用 **Git Flow 简化版**：

```
main        -- 发布主干（稳定版本，评审/提交用）
develop     -- 开发主干（所有功能汇聚）
feature/*   -- 功能分支（从 develop 派生）
bugfix/*    -- 缺陷修复分支
hotfix/*    -- 线上紧急修复分支（从 main 派生）
```

- **所有新分支从 `develop` 派生**，禁止直接从 main 创建
- 分支命名：`feature/xxx`、`bugfix/xxx`、`docs/xxx`

## 2. 提交规范

格式：`<类型>(<范围>): <中文描述>`

| 类型 | 用途 |
|---|---|
| feat | 新功能/新组件/新接口 |
| fix | 缺陷修复 |
| docs | 文档变更 |
| style | 代码格式调整（不影响逻辑） |
| refactor | 重构 |
| perf | 性能优化 |
| test | 测试相关 |
| build/chore | 构建/配置/依赖 |

示例：
```
feat(agent): 新增知识发现 Agent 检索接口
fix(rag): 修复混合检索排序权重异常
docs(api): 补充 SSE 断线重连说明
```

**小步提交**：完成一个独立小功能后立即提交，不积累。

## 3. 代码规范

### Java（backend / ai-service）
- Java 17 + Spring Boot 3.x
- 包结构：`com.aiscientist.{backend|ai}` 下按 controller/service/repository/entity/config 分层
- 注释使用中文说明职责；关键逻辑补充 Javadoc

### 前端（frontend）
- Vue 3 Composition API + TypeScript + Vite
- 组件命名 PascalCase，目录小写
- API 调用统一封装在 `src/api/`

### Python（data）
- Python 3.10+，依赖锁定精确版本（`requirements.txt` 用 `==`）

## 4. 环境与密钥

- **严禁提交密钥**：`.env` 已被 gitignore；API Key 只放本地 `.env`
- 环境变量模板见 `.env.example`
- 百炼 API 调用凭证截图留存（技术文档用），不入仓库

## 5. 文档要求

- 新模块/新接口落地后，同步更新 `docs/` 对应文档
- 标注「由团队确定」的待定项，定稿后移除该标注
- 重要决策在对应文档「更新时间」处刷新日期

## 6. 协作流程

1. `git checkout develop && git pull`
2. `git checkout -b feature/xxx`
3. 开发 + 小步提交
4. push 分支 → 创建 PR（base: develop）
5. 至少 1 人 Review 后合并
6. 合并后删除已合并分支

## 7. 提交材料规范（9 月 5 日前）

- 技术方案 PDF ≤ 20 页（由 `docs/tech-plan.md` 整理导出）
- 源代码（本仓库）
- 可选：可交互前端页面、10 分钟演示视频
- 压缩包命名：学校-姓名-作品名-联系电话
