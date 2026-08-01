# 仿12306 高并发购票与智能客服系统

这是一个面向简历展示和工程实践的 12306 购票系统重构项目。项目基于 Spring Boot 3、Java 17、PostgreSQL/pgvector、Redis、RocketMQ 和 Vue 3，围绕高并发购票、支付退款闭环、MQ 最终一致性、权限安全、智能客服 RAG 知识库等核心场景做了后端硬化和新版前端重构。

## 项目亮点

- 高并发购票：基于 Redis 余票令牌、数据库座位状态、延迟关单和支付回调，处理抢票、锁座、建单、支付、取消、退款等状态流转。
- 支付退款闭环：统一支付渠道、支付回调幂等、全额/部分退款校验、退款后座位和余票回滚，降低重复 MQ 消息导致的状态抖动。
- 权限安全：用户信息、乘车人、订单、客服业务工具均以当前登录态为准，避免信任前端传入用户名造成越权。
- 智能客服：支持 DeepSeek 聊天模型、本地 Ollama 向量模型、Function Calling 业务工具、知识库检索、历史会话、工具调用过程展示。
- RAG 知识库：支持 Markdown/Word/PDF/文本入库、结构化切片、pgvector 向量检索、关键词检索、上下文窗口扩展、相邻 chunk 去重、管理端检索测试。
- 管理后台：提供用户、订单、车次、知识库、运营兜底任务等后台能力，可支撑演示和排障。
- 新版前端：独立 `frontend-next/`，使用 Vue 3 + Element Plus，支持桌面端侧边栏、移动端底部导航和右下角智能客服浮窗。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3.2、Java 17、MyBatis-Plus、Redisson、RocketMQ |
| 数据 | PostgreSQL 16、pgvector、Redis 7、RustFS/S3 兼容对象存储 |
| AI 客服 | DeepSeek OpenAI-compatible API、Ollama、qwen3-embedding、Function Calling、RAG |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、ECharts |
| 测试/压测 | JUnit 5、Mockito、JMeter、Playwright/Edge 截图验收 |

## 目录结构

```text
D:\12306
+-- app/                  Spring Boot 主应用
+-- framework/            通用基础设施模块：缓存、幂等、统一返回、分布式 ID
+-- dependencies/         Maven 依赖版本管理
+-- frontend-next/        新版 Vue 3 前端，当前主前端
+-- frontend/             旧版迁移前端，仅保留对照
+-- config/               可热更新配置，例如客服系统提示词
+-- docker/               Docker 组件配置
+-- jmeter/               压测脚本、用户数据和压测记录
+-- scripts/              本地验证脚本
+-- docs/                 架构、计划、数据库、知识库测试数据和压测报告
+-- API.md                当前后端 API 契约总文档
+-- docker-compose.yml    本地中间件编排
+-- pom.xml               Maven 多模块入口
```

更完整的目录职责见 [docs/项目目录说明.md](docs/项目目录说明.md)。

## 快速启动

### 1. 准备环境

- JDK 17 或更高版本，推荐 Java 21
- Maven 3.9+
- Node.js 18+
- Docker Desktop
- PostgreSQL 客户端 `psql`
- Ollama，用于本地 embedding 模型

### 2. 启动中间件

```powershell
docker compose up -d
```

默认会启动 PostgreSQL、Redis、RocketMQ 和 RustFS。数据库连接默认值：

```text
PostgreSQL: localhost:5432 / index12306 / postgres / 12345678
Redis:      localhost:6379 / password 123456
RocketMQ:   localhost:9876
RustFS:     localhost:9000
```

### 3. 初始化数据库

```powershell
psql -h localhost -U postgres -d index12306 -f docs/database/schema-postgresql.sql
```

如果需要导入更大规模演示数据，可按实际情况使用 `resources/data/` 下的本地数据文件。

### 4. 配置 AI

复制示例环境变量文件：

```powershell
Copy-Item .env.example .env
```

在 `.env` 中配置：

```properties
DEEPSEEK_API_KEY=sk-your-deepseek-api-key
OLLAMA_MODELS=D:\.ollama\models
```

启动 Ollama，并确认 embedding 模型可用：

```powershell
ollama serve
ollama ls
```

当前默认配置：

- 聊天模型：`deepseek-v4-flash`
- 向量模型：`qwen3-embedding:0.6b`
- 系统提示词：`config/customer-service-system-prompt.md`

AI 详细说明见 [docs/setup/AI_LOCAL_OLLAMA.md](docs/setup/AI_LOCAL_OLLAMA.md)。

### 5. 启动后端

```powershell
mvn -pl app spring-boot:run
```

后端默认端口：

```text
http://127.0.0.1:8080
```

### 6. 启动前端

```powershell
cd frontend-next
npm install
npm run dev -- --host 127.0.0.1 --port 5173
```

前端地址：

```text
http://127.0.0.1:5173/tickets
```

## 核心业务模块

| 模块 | 职责 |
| --- | --- |
| 用户服务 | 注册登录、JWT 登录态、当前用户信息、乘车人管理、敏感信息脱敏/加密。 |
| 票务服务 | 车站查询、车票查询、购票、指定座位/自动分配、锁座、取消回滚。 |
| 订单服务 | 创建订单、订单分页、订单详情、支付状态流转、延迟关单、退款状态流转。 |
| 支付服务 | 支付单创建、支付查询、模拟支付回调、全额/部分退款、支付退款 MQ 通知。 |
| 管理后台 | 用户、订单、车次、知识库、运营兜底任务管理。 |
| 智能客服 | SSE 流式对话、历史会话、业务工具调用、RAG 知识库检索和来源展示。 |

完整接口见 [API.md](API.md)。

## 测试与验证

后端单元测试：

```powershell
mvn -pl app test
```

智能客服关键测试：

```powershell
mvn -pl app "-Dtest=ChatClientTests,RagRetrieverTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

前端构建：

```powershell
cd frontend-next
npm run build
```

AI/Ollama 连通性脚本：

```powershell
.\scripts\verify-ollama-ai.ps1
```

JMeter 压测脚本位于 `jmeter/`，压测记录见 [jmeter/压测记录.md](jmeter/压测记录.md) 和 [docs/智能客服压测报告.md](docs/智能客服压测报告.md)。

## API 与文档索引

- [API.md](API.md)：后端接口总文档
- [docs/项目目录说明.md](docs/项目目录说明.md)：项目目录职责
- [docs/design/ARCHITECTURE.md](docs/design/ARCHITECTURE.md)：总体架构设计
- [docs/design/CUSTOMER_SERVICE_DESIGN.md](docs/design/CUSTOMER_SERVICE_DESIGN.md)：智能客服设计
- [docs/plans/IMPLEMENTATION_PLAN.md](docs/plans/IMPLEMENTATION_PLAN.md)：实施计划
- [docs/plans/前端计划.md](docs/plans/前端计划.md)：新版前端计划
- [docs/database/schema-postgresql.sql](docs/database/schema-postgresql.sql)：数据库结构
- [docs/knowledge-base-test/](docs/knowledge-base-test/)：知识库入库测试数据

## 演示建议

1. 先展示车票查询、下单、模拟支付、我的订单、退款闭环。
2. 再展示管理后台的订单、车次、运营兜底和知识库管理。
3. 最后展示右下角智能客服浮窗：普通规则问答、订单查询、知识库检索、历史会话和工具调用结果。
4. 简历描述重点放在库存一致性、MQ 幂等、支付退款最终一致性、权限安全、RAG 知识库和 AI 客服并发隔离。

## 注意事项
- `.env` 存放真实密钥，已加入 `.gitignore`，不要提交。
