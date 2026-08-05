# backend — 业务后端（独立工程）

Spring Boot 3.x + Java 17，对外提供 RESTful API 与 SSE 流式转发，MySQL 持久化。

## 职责

- RESTful API：任务提交、任务查询、报告获取、文件上传
- SSE 流式转发：将 ai-service 的 Agent 状态事件实时转发给前端
- MySQL 持久化：用户、任务、历史报告、调用日志

## 快速开始

```bash
mvn spring-boot:run          # 默认 http://localhost:8080
```

依赖中间件（MySQL）由根目录 docker-compose 提供：

```bash
docker compose up -d mysql
```

## 目录结构

```
src/main/java/com/aiscientist/backend/
├── BackendApplication.java   # 启动类
├── controller/               # REST 控制器（接口清单见 docs/api-design.md）
├── service/                  # 业务服务
├── repository/               # 数据访问层
├── entity/                   # 实体（字段由后端组设计，见 docs/database.md）
└── config/                   # 配置（CORS / SSE 等）
```

## 接口清单（骨架）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/tasks` | 提交科研任务 |
| GET | `/api/tasks` | 任务列表 |
| GET | `/api/tasks/{id}` | 任务详情 |
| GET | `/api/tasks/{id}/report` | 获取研究计划 |
| GET | `/api/tasks/{id}/stream` | SSE 流 |
| POST | `/api/tasks/{id}/intervene` | 人在回路介入 |

> 请求/响应字段由后端组设计时确定；表结构见 [docs/database.md](../docs/database.md)（预留）。
