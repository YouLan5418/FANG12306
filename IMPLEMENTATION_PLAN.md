# 12306 重构版实施计划

## 实施原则

1. **最小可验证单元**：每个任务独立完成后可通过接口调用/测试用例/命令观测验证，不留半成品
2. **依赖驱动排序**：骨架 → 基础层 → 数据层 → 业务域(用户→订单→购票→支付) → 智能客服 → 前端 → 集成测试
3. **每阶段可交付**：每个 Phase 结束产出一个可演示的能力切片
4. **先打通主干再优化**：核心链路优先跑通，限流/缓存/加密等增强在主干验证后补齐

---

## 阶段总览

| Phase | 主题 | 任务数 | 里程碑 |
|-------|------|--------|--------|
| P0 | 工程骨架与环境 | 4 | 应用可启动、库表就绪 |
| P1 | framework 基础层 | 5 | 基础设施可用 |
| P2 | 数据访问层 | 4 | 实体/Mapper 可 CRUD |
| P3 | 用户域业务 | 5 | 注册/登录可用 |
| P4 | 订单域业务 | 3 | 建单/查询/状态流转可用 |
| P5 | 购票域业务 | 4 | 购票下单闭环 |
| P6 | 支付域业务 | 4 | 支付/回调/退款闭环 |
| P7 | 客服基础设施 | 4 | LLM/Embedding/Rerank 可调 |
| P8 | RAG 与数据清洗 | 5 | 知识库可入库可检索 |
| P9 | 客服 Agent 编排 | 5 | 智能客服可对话 |
| P10 | 前端整合 | 4 | 前后端联调通过 |
| P11 | 集成测试与收尾 | 4 | 端到端可演示 |

---

## 当前进度（2026-07-30 更新）

| Phase | 状态 | 说明 |
|-------|------|------|
| P0 工程骨架与环境 | ✅ 基本完成（P0-T4 CI 暂缓） | 骨架/连接/库表就绪；P0-T4 GitHub Actions 按用户要求暂不补 |
| P1 framework 基础层 | ✅ 完成 | convention/cache/idempotent/distributedid/common 全部编译通过，含 4 个单测 |
| P2 数据访问层 | ✅ 完成 | 四个域实体/Mapper 完成，含 pgvector VectorTypeHandler |
| P3 用户域业务 | ✅ 完成 | 注册/登录/乘车人/注销全链路，7 个用例通过。M2 里程碑达成 |
| P4 订单域业务 | ✅ 完成 | 建单(三表+延迟关单MQ)/查询分页/状态机流转，5 个用例通过 |
| P5 购票域业务 | ✅ 完成 | 查询/选座/购票/延迟关单回滚，4 个用例通过 |
| P6 支付域业务 | ✅ 完成 | 支付/回调/消费/退款+座位回滚缺口修复，含 4 个 MQ 消费者；验收测试 PayServiceFlowTests 通过 |
| P7-P11 | 🔴 未开始 | - |



**已通过自评审并修复**：清理 cache 模块死代码（CacheGetFilter/CacheGetIfAbsent 未使用的接口与类）；修复 SecurityProperties 空密钥覆盖默认值导致 JWT WeakKeyException 的问题（空值时回退默认密钥）。**P2-T1 验证结论**：MyBatis-Plus Wrapper 条件参数不经过 TypeHandler，加密字段（id_card/phone/mail/address）作查询条件时需在业务层先显式加密再入参。

---

## Phase 0：工程骨架与环境

| ID | 任务 | 前置 | 影响范围 | 验收标准 | 状态 |
|----|------|------|----------|----------|------|
| P0-T1 | 初始化 Maven 多模块工程（dependencies/framework/app/frontend/resources） | 无 | 全局工程结构 | `mvn clean compile` 通过；app 模块可启动空应用 | ✅ 完成 |
| P0-T2 | 基础设施连接配置（PG/Redis/RocketMQ）+ docker-compose | P0-T1 | application.yml、docker-compose.yml | `docker-compose up` 起 PG/Redis/RocketMQ；应用健康检查 200 | ✅ 完成 |
| P0-T3 | 执行建表脚本 + 种子数据 | P0-T2 | schema-postgresql.sql 落库 | psql `\dt` 显示 26 张表；kb_category 有 3 条种子；knowledge_chunk 有 hnsw 索引 | ✅ 完成 |
| P0-T4 | 轻量 CI（GitHub Actions：编译+单元测试） | P0-T1 | .github/workflows | push 后 CI 跑通编译（测试可先 skip） | ⏸ 暂缓（用户要求不补） |

## Phase 1：framework 基础层

| ID | 任务 | 前置 | 影响范围 | 验收标准 | 状态 |
|----|------|------|----------|----------|------|
| P1-T1 | convention：统一 Result 返回 + `@RestControllerAdvice` 全局异常 | P0-T1 | framework/convention | 接口返回 `{code,msg,data}`；抛异常被全局捕获返回统一结构 | ✅ 完成 |
| P1-T2 | cache：Spring Cache + Redisson 封装（StringRedisTemplateProxy 精简版：get/put/safeGet/delete） | P1-T1 | framework/cache | 单测：put 后 get 命中；过期失效；safeGet 回源 DB | ✅ 完成（单测待 P2 有实体后补） |
| P1-T3 | idempotent：注解 + AOP + Redis（RESTAPI/MQ 两场景，SPEL key） | P1-T2 | framework/idempotent | 重复请求第二次返回"正在处理"；MQ 重复消费只执行一次 | ✅ 完成（单测待业务接入后补） |
| P1-T4 | distributedid：雪花算法（OrderIdGeneratorManager/PayIdGeneratorManager） | P1-T1 | framework/distributedid | 并发生成 1 万 ID 无重复 | ✅ 完成（10 线程 1 万 ID 无重复单测通过） |
| P1-T5 | 通用基础设施：UserContext + JWT(jjwt0.11+，实际用 0.12) 工具 + 登录拦截器 + MyBatis-Plus 配置 + MetaObjectHandler + AES 加密 TypeHandler | P1-T1 | framework/common、app/common | JWT 生成/解析通过；拦截器校验 token；插入记录自动填充 create_time；加密字段存密文 | ✅ 完成（JWT 单测通过；common 实际放 app/common） |

## Phase 2：数据访问层

| ID | 任务 | 前置 | 影响范围 | 验收标准 | 状态 |
|----|------|------|----------|----------|------|
| P2-T1 | 用户域实体 + Mapper（User/Phone/Mail/Passenger/Deletion/Reuse） | P1-T5 | app/user/dao | Mapper 单测：insert+selectOne 走对应分区；加密字段读写一致 | ✅ 完成（4 个 Mapper 单测通过） |
| P2-T2 | 订单支付域实体 + Mapper（Order/OrderItem/OrderItemPassenger/Pay/Refund） | P1-T5 | app/order/dao、app/pay/dao | Mapper 单测：按 order_sn 路由到正确分区 | ✅ 完成（5 个 Mapper 单测通过） |
| P2-T3 | 票务域实体 + Mapper（Ticket/Seat/Train/Station/Carriage/TrainStation/Relation/Price/Region） | P1-T5 | app/ticket/dao | Mapper 单测：t_seat 按 train_id 路由；t_ticket 按 username 路由 | ✅ 完成（9 个 Mapper 单测通过） |
| P2-T4 | 客服域实体 + Mapper（KbCategory/KbDocument/KnowledgeChunk/IngestionTask/ChatSession/ChatMessage） | P1-T5 | app/customerservice/dao | Mapper 单测：knowledge_chunk 插入 vector 类型成功 | ✅ 完成（5 个 Mapper 单测通过） |

## Phase 3：用户域业务

| ID | 任务 | 前置 | 影响范围 | 验收标准 | 状态 |
|----|------|------|----------|----------|------|
| P3-T1 | 用户注册：布隆过滤器初始化 + 责任链校验 + Redisson 锁 + 多表写入 + 回写布隆 | P2-T1、P1-T2、P1-T3 | app/user/service | POST /register 成功；重复用户名被拒；布隆过滤器含新用户 | ✅ 完成 |
| P3-T2 | 用户登录：phone/mail 反查 username + 查 t_user + JWT + Redis 登录态 | P3-T1、P1-T5 | app/user/service | 手机号/邮箱/用户名三种方式登录均返回 token；Redis 有 token key | ✅ 完成 |
| P3-T3 | 登录态校验/登出：checkLogin/logout | P3-T2 | app/user/controller | checkLogin 返回用户信息；logout 后 token 失效 | ✅ 完成 |
| P3-T4 | 乘车人 CRUD | P2-T1、P3-T2 | app/user/service | 增删改查通过；乘车人加密字段脱敏返回 | ✅ 完成 |
| P3-T5 | 用户信息查询/更新/注销（含 t_user_deletion/reuse） | P3-T2 | app/user/service | 注销后 username 可重用；读请求扩散走 phone/mail 索引表 | ✅ 完成 |

## Phase 4：订单域业务

| ID | 任务 | 前置 | 影响范围 | 验收标准 | 状态 |
|----|------|------|----------|----------|------|
| P4-T1 | 订单创建：订单号生成 + t_order/t_order_item/t_order_item_passenger 写入 + 发延迟关单 MQ | P2-T2、P1-T4、P0-T2 | app/order/service | createTicketOrder 返回 orderSn；三表有记录；RocketMQ 有延迟消息(delayLevel=14) | ✅ 完成 | ✅ 完成 |
| P4-T2 | 订单查询/分页 | P2-T2 | app/order/service | 按 order_sn 查详情；按 user_id 分页返回 | ✅ 完成 |
| P4-T3 | 订单状态流转：statusReversal + payCallbackOrder + closeTickOrder（含状态校验防重复） | P2-T2、P1-T2 | app/order/service | 状态机校验：非 PENDING_PAYMENT 状态流转被拒；Redisson 锁生效 | ✅ 完成 | ✅ 完成 |

## Phase 5：购票域业务

| ID | 任务 | 前置 | 影响范围 | 验收标准 | 状态 |
|----|------|------|----------|----------|------|
| P5-T1 | 车票查询：责任链校验 + Redis 余票缓存 | P2-T3、P1-T2 | app/ticket/service | 查询返回车次列表；二次查询命中缓存 | ✅ 完成 |
| P5-T2 | 选座算法：策略模式（First/Second/Business）+ 位图选座 | P2-T3 | app/ticket/handler | 按车型选座返回车厢号/座位号；余票不足抛异常 | ✅ 完成 |
| P5-T3 | 购票下单 V2：令牌桶 + 本地锁(Caffeine) + 分布式公平锁 + 选座 + 存 t_ticket + 调 OrderService 建单 | P5-T2、P4-T1、P1-T3 | app/ticket/service | POST /purchase 返回 orderSn；t_ticket/t_order 有记录；令牌桶余票递减 | ✅ 完成 |
| P5-T4 | 延迟关单消费：RocketMQ 延迟消息 + closeTickOrder + 座位/余票/令牌桶回滚 | P4-T3、P5-T3 | app/ticket/mq | 未支付订单 2h 后（测试用短 delayLevel）关单成功；座位状态回滚；余票恢复 | ✅ 完成 |

## Phase 6：支付域业务 ✅

| ID | 任务 | 前置 | 影响范围 | 验收标准 | 状态 |
|----|------|------|----------|----------|------|
| P6-T1 | 创建支付单：支付宝下单 API + 存 t_pay + 缓存支付结果 | P2-T2、P0-T2 | app/pay/service | POST /pay/create 返回支付宝支付链接；t_pay 有 WAIT_BUYER_PAY 记录 | ✅ |
| P6-T2 | 支付宝回调：callbackPay + 更新 t_pay + 发支付结果 MQ | P6-T1 | app/pay/controller、mq/produce | 回调后 t_pay.status=TRADE_SUCCESS；RocketMQ 发支付结果消息 | ✅ |
| P6-T3 | 支付结果消费：order 状态流转 ALREADY_PAID + ticket 座位 SOLD（两消费组） | P6-T2、P4-T3、P2-T3 | app/order/mq、app/ticket/mq | 支付后 t_order.status=10；t_seat.seat_status=2(SOLD) | ✅ |
| P6-T4 | 退款：commonRefund + 支付宝退款 + 发退款 MQ + 退款消费 + **修复座位回滚缺口** | P6-T3、P4-T3、P5-T2 | app/pay/service、app/order/mq | 退款后 t_order.status=11/12；**t_seat 座位状态回滚、余票恢复**（原项目缺口已修复） | ✅ |
## Phase 7：客服基础设施

| ID | 任务 | 前置 | 影响范围 | 验收标准 |
|----|------|------|----------|----------|
| P7-T1 | ChatClient 抽象 + OllamaChatClient + OpenAIStyleChatClient（同步+流式） | P0-T2、P1-T1 | app/customerservice/chat | 本地 qwen 与云端 API 均能返回回复；流式分片到达 |
| P7-T2 | EmbeddingClient 抽象 + 云端实现 | P0-T2 | app/customerservice/embedding | 输入文本返回 1024 维向量 |
| P7-T3 | RerankClient + 云端实现（借鉴 BaiLian） | P0-T2 | app/customerservice/rerank | 输入 query+候选 chunks 返回重排序结果 |
| P7-T4 | SSE 流式基础设施：SseEmitter + 流式回调封装 | P1-T1 | app/customerservice | SSE 端点能推送分片到前端 EventSource |

## Phase 8：RAG 与数据清洗

| ID | 任务 | 前置 | 影响范围 | 验收标准 |
|----|------|------|----------|----------|
| P8-T1 | 文档解析：MarkdownParser + TikaParser（PDF/Word）+ OcrParser 预留接口(Noop) | P2-T4 | app/customerservice/parser | Markdown/PDF/Word 解析出文本；OCR 接口存在返回未实现标记 |
| P8-T2 | 切片策略：StructureAware（按标题）+ FixedSize + Factory | P8-T1 | app/customerservice/chunk | Markdown 按标题切片；块大小在 min/target/max 范围 |
| P8-T3 | 入库管道：上传→解析→切片→embedding→存 pgvector + ingestion_task 状态 | P8-T2、P7-T2、P2-T4 | app/customerservice/ingestion | 上传一个 Markdown，knowledge_chunk 有向量记录；task.status=4(完成) |
| P8-T4 | 知识库后台 API：分类 CRUD（自添加）+ 文档上传 + 任务列表 | P8-T3 | app/customerservice/controller | 新增分类成功；上传文档触发入库；查任务列表返回状态 |
| P8-T5 | RAG 检索：pgvector 相似检索 + rerank 精排 | P8-T3、P7-T3 | app/customerservice/rag | 查询返回 topK 相关 chunks；rerank 后顺序优化 |

## Phase 9：客服 Agent 编排

| ID | 任务 | 前置 | 影响范围 | 验收标准 |
|----|------|------|----------|----------|
| P9-T1 | Function Calling 自实现：工具注册 + tool_calls 解析 + 执行 + 结果回填 | P7-T1 | app/customerservice/fc | LLM 返回 tool_calls 时能解析、执行、回填并二次生成 |
| P9-T2 | 业务工具定义：queryTicket/queryOrder/queryStation | P9-T1、P5-T1、P4-T2、P2-T3 | app/customerservice/tool | 问"北京到上海车票"→调 queryTicket 返回车次 |
| P9-T3 | searchKnowledge 工具：RAG 检索作为工具 | P9-T1、P8-T5 | app/customerservice/tool | 问"退票手续费"→调 searchKnowledge 返回规则 |
| P9-T4 | 客服对话编排：加载历史 + LLM 自主决策 + 工具执行 + 流式生成 | P9-T2、P9-T3、P7-T4 | app/customerservice/service | SSE 流式回复；知识类走 RAG、查询类走 FC |
| P9-T5 | 对话历史：Redis 缓存（按 sessionId）+ 持久化 chat_message | P9-T4、P1-T2、P2-T4 | app/customerservice | 多轮对话记住上下文；后台可查 chat_message 记录 |

## Phase 10：前端整合

| ID | 任务 | 前置 | 影响范围 | 验收标准 |
|----|------|------|----------|----------|
| P10-T1 | 前端迁移 + 路由调整（去网关，直连后端） | P3-T2 | frontend | 前端可登录，请求直连后端端口 |
| P10-T2 | 购票/订单/乘车人页面联调 | P5-T3、P6-T1、P3-T4 | frontend/views | 购票→下单→支付链路前端可走通 |
| P10-T3 | 智能客服对话窗（SSE EventSource） | P9-T4 | frontend/views | 对话窗流式显示回复 |
| P10-T4 | 知识库后台页面（分类管理 + 文档上传 + 任务列表） | P8-T4 | frontend/views | 后台可新增分类、上传文档、查看入库状态 |

## Phase 11：集成测试与收尾

| ID | 任务 | 前置 | 影响范围 | 验收标准 |
|----|------|------|----------|----------|
| P11-T1 | 业务端到端测试：注册→登录→购票→支付→延迟关单/退款 | P6-T4、P5-T4 | 全链路 | 全链路状态正确；退款座位回滚验证通过 |
| P11-T2 | 智能客服端到端测试：知识问答 + 实时查询 + 多轮 | P9-T5、P8-T4 | 客服链路 | 退改规则问答准确；查订单/车票正确；多轮上下文连贯 |
| P11-T3 | README + 启动文档 + 简历亮点梳理 | P11-T1、P11-T2 | docs | 一键启动脚本可用；README 含架构图与亮点 |
| P11-T4 | 加密 TypeHandler 验证 + 数据脱敏序列化收尾 | P1-T5、P3-T4 | 全局 | DB 中敏感字段为密文；接口返回脱敏 |

---

## 关键依赖路径

```
P0(骨架) → P1(基础层) → P2(数据层)
                          ├→ P3(用户) → P4(订单) → P5(购票) → P6(支付) ──┐
                          │                                              ├→ P11(集成测试)
                          └→ P7(客服基建) → P8(RAG/清洗) → P9(Agent) ──┤
                                                                       │
P10(前端) 依赖 P3/P5/P6/P8/P9 ─────────────────────────────────────────┘
```

**关键串行依赖**：
- 购票下单(P5-T3) 依赖订单创建(P4-T1)：购票直接调用 OrderService 建单
- 延迟关单(P5-T4) 依赖订单状态流转(P4-T3)：消费时调 closeTickOrder
- 退款修复(P6-T4) 依赖支付(P6-T1~T3) + 选座(P5-T2)：需回滚座位
- 客服 Agent(P9) 依赖业务查询接口(P4-T2/P5-T1)：FC 工具调用业务 Service

**可并行阶段**：
- P7(客服基建) 与 P3~P6(业务) 无依赖，可并行开发
- P8(数据清洗) 依赖 P7，可与 P4~P6 并行

---

## 里程碑

| 里程碑 | 完成阶段 | 可演示能力 |
|--------|----------|-----------|
| M1 骨架就绪 | P0-P2 | 应用启动、库表就绪、Mapper 可用 |
| M2 用户闭环 | P3 | 注册/登录/乘车人可用 |
| M3 交易主干 | P4-P6 | 购票→支付→关单/退款全链路 |
| M4 客服闭环 | P7-P9 | 智能客服可问答（RAG+FC） |
| M5 全栈交付 | P10-P11 | 前后端联调、端到端可演示 |

---

## 风险与备注

- **退款座位回滚修复**（P6-T4）：原项目已知缺口，本次必须修复并作为简历亮点
- **本地 qwen FC 能力**：7b 小模型 tool calling 稳定性弱，开发可本地、演示建议切云端强模型
- **pgvector 维度**：knowledge_chunk.embedding 当前 1024（bge-m3），若换 embedding 模型需同步改列定义
- **PG 分区表主键约束**：所有分区表主键含分区键，MyBatis-Plus 实体需注意联合主键映射
- **延迟关单测试**：生产 delayLevel=14(2h)，测试时改短 delayLevel 加速验证
