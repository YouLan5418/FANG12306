# 12306 重构版 API 接口契约

## 通用约定

**统一返回结构**（SSE 接口除外）
```json
{ "code": "0", "message": "success", "data": <T> }
```
- `code="0"` 成功；非 0 为错误码
- 分页响应 `data` 为 `{ records[], total, current, size }`

**鉴权方式**
- 需登录：请求头 `Authorization: Bearer {accessToken}`，或 query 参数 `accessToken`
- 公开：无需鉴权
- 内部：单体内部调用（不对外暴露，路径保留便于识别）

**路径前缀**：保持原 `/api/{domain}/...` 风格，单体下所有域同应用。

---

## 一、用户域 `/api/user-service`

### 认证

```
POST /api/user-service/v1/login              [公开]
请求: { usernameOrMailOrPhone: string, password: string }
响应: { userId: string, username: string, realName: string, accessToken: string }
```

```
GET  /api/user-service/check-login           [公开]
参数: accessToken
响应: { userId, username, realName, accessToken }  // 从 Redis 读取登录态
```

```
GET  /api/user-service/logout                 [公开]
参数: accessToken
响应: void  // 删除 Redis 登录态
```

### 用户信息

```
POST /api/user-service/register               [公开]
请求: { username, password, realName, idType, idCard, phone, mail, region? }
响应: { username }
说明: 布隆过滤器判重 + 责任链校验 + Redisson 锁 + 多表写入
```

```
GET  /api/user-service/has-username           [公开]
参数: username
响应: boolean  // 布隆过滤器 + 复用集合双重判断
```

```
GET  /api/user-service/query                  [需登录/内部]
参数: username
响应: { id, username, realName, idType, phone(脱敏), mail(脱敏), ... }
```

```
GET  /api/user-service/actual/query           [内部]
参数: username
响应: { id, username, realName, idType, idCard, phone, mail, address, ... }  // 含敏感原文
```

```
POST /api/user-service/update                 [需登录]
请求: { username, realName?, phone?, mail?, ... }
响应: void
```

```
POST /api/user-service/deletion               [需登录]
请求: { username }
响应: void
说明: 注销流程，写 t_user_deletion/t_user_reuse，username 可重用
```

### 乘车人

```
GET  /api/user-service/passenger/query        [需登录]
响应: [ { id, realName, idType, idCard(脱敏), discountType, phone(脱敏), verifyStatus } ]
```

```
GET  /api/user-service/inner/passenger/actual/query/ids   [内部]
参数: username, ids[]
响应: [ { id, realName, idType, idCard, phone, discountType, ... } ]
```

```
POST /api/user-service/passenger/save         [需登录]
请求: { realName, idType, idCard, discountType, phone }
响应: void
```

```
POST /api/user-service/passenger/update       [需登录]
请求: { id, realName?, idType?, idCard?, discountType?, phone? }
响应: void
```

```
POST /api/user-service/passenger/remove       [需登录]
请求: { id }
响应: void
```

---

## 二、购票域 `/api/ticket-service`

### 车票查询

```
GET  /api/ticket-service/ticket/query         [公开]
参数: { fromStation?, toStation?, departureDate(yyyy-MM-dd), departure?, arrival?, current, size }
响应: { trainList: [ { trainId, trainNumber, trainType, departure, arrival, departureTime, arrivalTime,
                       seatClassList: [ { seatType, price, quantity, tickets } ] } ], current, size, total }
说明: 责任链校验 + Redis 余票缓存
```

### 购票

```
POST /api/ticket-service/ticket/purchase      [需登录]   // V2 实现
请求: { trainId: string, passengers: [ { passengerId: string, seatType: int } ],
        chooseSeats?: string[], departure: string, arrival: string }
响应: { orderSn: string, ticketOrderDetailResults: [ { amount, carriageNumber, seatNumber, seatType,
                       idCard, idType, phone, realName, ticketType } ] }
说明: 令牌桶 + 本地锁 + 分布式公平锁 + 选座锁座 + 存 t_ticket + 调 OrderService 建单
幂等: @Idempotent SPEL(key=username)
```

### 订单操作（票务侧入口）

```
POST /api/ticket-service/ticket/cancel        [需登录]
请求: { orderSn }
响应: void
说明: 取消未支付订单，调 OrderService.cancelTicketOrder + 回滚余票
```

```
GET  /api/ticket-service/ticket/pay/query     [需登录]
参数: orderSn
响应: { paySn, orderSn, totalAmount, payAmount, status, ... }
```

### 退款

```
POST /api/ticket-service/ticket/refund        [需登录]
请求: { orderSn: string, type: int, subOrderRecordIdReqList?: string[] }
       // type: 0部分退款 1全部退款；部分退款需 subOrderRecordIdReqList
响应: { refundAmount, refundTime, ... }
说明: 调 PayService.commonRefund；退款后修复座位/余票回滚（原项目缺口已修复）
```

### 车站基础数据

```
GET  /api/ticket-service/region-station/query [公开]
参数: { region?, name? }
响应: [ { name, spell, region, ... } ]
```

```
GET  /api/ticket-service/station/all          [公开]
响应: [ { code, name, spell, region, regionName } ]
```

```
GET  /api/ticket-service/train-station/query  [公开]
参数: trainId
响应: [ { sequence, departure, arrival, departureTime, arrivalTime, stopoverTime } ]
```

---

## 三、订单域 `/api/order-service`

```
GET  /api/order-service/order/ticket/query            [内部]
参数: orderSn
响应: { orderSn, userId, trainNumber, departure, arrival, status, departureTime, arrivalTime,
        passengerDetails: [ { seatNumber, carriageNumber, seatType, realName, ... } ] }
```

```
GET  /api/order-service/order/item/ticket/query       [内部]
参数: { orderSn, orderItemRecordIds[] }
响应: [ { seatNumber, carriageNumber, seatType, realName, ... } ]
```

```
GET  /api/order-service/order/ticket/page             [需登录]
参数: { userId, statusList?, current, size }
响应: PageResponse<TicketOrderDetailRespDTO>
```

```
GET  /api/order-service/order/ticket/self/page        [需登录]
参数: { current, size }  // 从 UserContext 取 username
响应: PageResponse<TicketOrderDetailSelfRespDTO>
```

```
POST /api/order-service/order/ticket/create            [内部]
请求: { orderSn(可空,后端生成), userId, username, trainId, trainNumber, departure, arrival,
        departureTime, arrivalTime, ridingDate, source, orderTime,
        ticketOrderItems: [ { amount, carriageNumber, seatNumber, idCard, idType, phone,
                              seatType, ticketType, realName } ] }
响应: string(orderSn)
说明: 事务写入 t_order/t_order_item/t_order_item_passenger + 发延迟关单 MQ
```

```
POST /api/order-service/order/ticket/close             [内部]
请求: { orderSn }
响应: boolean  // true=确实关闭未支付订单；false=已支付不关单
```

```
POST /api/order-service/order/ticket/cancel            [内部]
请求: { orderSn }
响应: boolean
```

---

## 四、支付域 `/api/pay-service`

```
POST /api/pay-service/pay/create              [需登录]
请求: { orderSn, totalAmount, channel? }  // channel: ALI_PAY 等
响应: { paySn, payUrl, ... }  // 支付宝支付链接
说明: 调支付宝下单 + 存 t_pay(WAIT_BUYER_PAY) + 缓存
```

```
GET  /api/pay-service/pay/query/order-sn      [需登录]
参数: orderSn
响应: { paySn, orderSn, totalAmount, payAmount, status, gmtPayment }
```

```
GET  /api/pay-service/pay/query/pay-sn        [需登录]
参数: paySn
响应: 同上
```

```
POST /api/pay-service/callback/alipay         [公开-支付宝回调]
请求: form 表单参数（支付宝异步回调）
响应: "success" 字符串
说明: 更新 t_pay(TRADE_SUCCESS) + 发支付结果 MQ
```

```
POST /api/pay-service/common/refund           [内部]
请求: { orderSn, refundTypeEnum, refundAmount, refundDetailReqDTOList: [ { ... } ] }
响应: { refundSn?, ... }  // 当前返回 null（待补完）
说明: 调支付宝退款 + 更新 t_pay/t_refund + 发退款结果 MQ
```

---

## 五、智能客服域 `/api/customer-service`（新增）

### 对话

```
POST /api/customer-service/chat               [需登录]  [SSE 流式]
请求: { sessionId?: string, message: string }
响应: text/event-stream
  data: {"type":"tool_call","tool":"queryTicket","args":{...}}
  data: {"type":"token","content":"北京"}
  data: {"type":"token","content":"到上海"}
  data: {"type":"done","sessionId":"..."}
说明: LLM 自主决策工具；工具结果回填后流式生成回复
```

### 会话管理

```
GET  /api/customer-service/session/list       [需登录]
响应: [ { sessionId, title, updateTime } ]
```

```
GET  /api/customer-service/session/{sessionId}/messages   [需登录]
响应: [ { role, content, toolName?, toolArgs?, toolResult?, createTime } ]
```

```
DELETE /api/customer-service/session/{sessionId}          [需登录]
响应: void
```

---

## 六、知识库后台 `/api/customer-service/kb`（新增）

### 分类管理（支持自添加）

```
POST   /api/customer-service/kb/category      [需登录]
请求: { name, description? }
响应: { id, name, description }
```

```
GET    /api/customer-service/kb/category      [需登录]
响应: [ { id, name, description, documentCount } ]
```

```
PUT    /api/customer-service/kb/category/{id} [需登录]
请求: { name?, description? }
响应: void
```

```
DELETE /api/customer-service/kb/category/{id} [需登录]
响应: void
说明: 检查分类下无文档才可删
```

### 文档管理

```
POST   /api/customer-service/kb/document/upload   [需登录]  [multipart]
参数: categoryId, file, parserType(MARKDOWN/TIKA/OCR), chunkMode(structure_aware/fixed_size)
响应: { documentId, taskId }
说明: 上传触发入库管道：解析->切片->embedding->存 pgvector
```

```
GET    /api/customer-service/kb/document       [需登录]
参数: categoryId?
响应: [ { id, categoryId, fileName, fileType, parserType, chunkMode, status, chunkCount } ]
```

```
DELETE /api/customer-service/kb/document/{id}  [需登录]
响应: void
说明: 删除文档 + 关联 knowledge_chunk + 向量
```

### 入库任务

```
GET    /api/customer-service/kb/task           [需登录]
参数: { status?, current, size }
响应: PageResponse<IngestionTaskVO>
  // status: 0待处理 1解析 2切片 3向量化 4完成 5失败
```

```
GET    /api/customer-service/kb/task/{id}      [需登录]
响应: { id, documentId, status, totalChunks, processedChunks, errorMsg, createTime, updateTime }
```

---

## 状态枚举对照

**订单状态 `t_order.status`**
- 0 待支付 / 10 已支付 / 11 部分退款 / 12 全部退款 / 20 已完成 / 30 已取消

**订单明细状态 `t_order_item.status`**
- 0 待支付 / 10 已支付 / 20 已进站 / 30 已取消 / 40 已退票 / 50 已改签

**座位状态 `t_seat.seat_status`**
- 0 可用 / 1 锁定 / 2 已售

**车票状态 `t_ticket.ticket_status`**
- 0 未支付 / 1 已支付

**支付状态 `t_pay.status`**
- WAIT_BUYER_PAY 待付款 / TRADE_SUCCESS 交易成功 / TRADE_CLOSED 交易关闭

**入库任务状态 `ingestion_task.status`**
- 0 待处理 / 1 解析 / 2 切片 / 3 向量化 / 4 完成 / 5 失败