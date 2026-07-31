# 12306 后端 API 接口文档

## 通用约定

统一响应结构（SSE 和支付回调字符串响应除外）：

```json
{
  "code": "0",
  "message": "success",
  "data": {}
}
```

- `code = "0"` 表示成功，非 `0` 表示业务异常。
- 登录接口返回 `accessToken`，需要登录的接口请求头传 `Authorization: Bearer {accessToken}`，也兼容直接传 token。
- 分页响应一般为 `{ "records": [], "total": 0, "current": 1, "size": 10 }`。
- 管理端接口统一在 `/api/admin/**` 下，需要登录且 `userType = 1`。

## 用户服务 `/api/user-service`

### 登录注册

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/user-service/v1/login` | 公开 | 用户名/手机号/邮箱登录 |
| `GET` | `/api/user-service/check-login` | 公开 | 校验 token 登录态 |
| `GET` | `/api/user-service/logout` | 公开 | 删除 Redis 登录态 |
| `POST` | `/api/user-service/register` | 公开 | 用户注册 |
| `GET` | `/api/user-service/has-username` | 公开 | 判断用户名是否可用 |
| `POST` | `/api/user-service/deletion` | 登录 | 注销当前用户 |

`POST /api/user-service/v1/login`

```json
{
  "usernameOrMailOrPhone": "alice",
  "password": "123456"
}
```

返回：

```json
{
  "userId": "1",
  "username": "alice",
  "realName": "张三",
  "accessToken": "...",
  "role": 0
}
```

`POST /api/user-service/register`

```json
{
  "username": "alice",
  "password": "123456",
  "realName": "张三",
  "idType": 0,
  "idCard": "110101199001011234",
  "phone": "13900000000",
  "mail": "alice@example.com",
  "region": "0"
}
```

### 用户信息

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/user-service/query` | 登录 | 查询当前登录用户，忽略 `username` 参数 |
| `GET` | `/api/user-service/actual/query` | 登录 | 查询当前登录用户真实信息，忽略 `username` 参数 |
| `POST` | `/api/user-service/update` | 登录 | 只更新当前登录用户 |

`POST /api/user-service/update`

```json
{
  "realName": "张三",
  "phone": "13900000001",
  "mail": "new@example.com",
  "region": "北京",
  "postCode": "100000",
  "address": "北京市海淀区"
}
```

前端“我的信息”页面使用：

- 桌面端侧边栏入口：我的信息。
- 手机端底边栏入口：我的。
- 未登录访问 `/profile` 会被路由守卫跳转到登录页。
- 页面详情读取 `/api/user-service/actual/query`，保存修改调用 `/api/user-service/update`。

### 乘车人

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/user-service/passenger/query` | 登录 | 查询当前用户乘车人列表 |
| `GET` | `/api/user-service/inner/passenger/actual/query/ids` | 内部 | 按用户名和乘车人 ID 查询真实信息 |
| `POST` | `/api/user-service/passenger/save` | 登录 | 新增乘车人 |
| `POST` | `/api/user-service/passenger/update` | 登录 | 修改当前用户乘车人 |
| `POST` | `/api/user-service/passenger/remove` | 登录 | 删除当前用户乘车人 |

`POST /api/user-service/passenger/save`

```json
{
  "realName": "李四",
  "idType": 0,
  "idCard": "110101199001011235",
  "discountType": 0,
  "phone": "13800000000"
}
```

`POST /api/user-service/passenger/remove`

```json
{
  "ids": ["2083093199123472384"]
}
```

## 票务服务 `/api/ticket-service`

### 查票和站点

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/ticket-service/ticket/query` | 公开 | 按出发站/到达站查询车票 |
| `GET` | `/api/ticket-service/station/all` | 公开 | 查询全部车站 |
| `GET` | `/api/ticket-service/station/regions` | 公开 | 查询全部车站区域 |
| `GET` | `/api/ticket-service/region-station/query` | 公开 | 按区域或名称查询车站 |
| `GET` | `/api/ticket-service/train-station/query` | 公开 | 查询车次经停站 |

说明：

- `/station/all`、`/station/regions`、`/region-station/query` 使用 Redis 缓存，缓存时间 24 小时。
- `/region-station/query` 的 `region` 同时匹配 `region` 和 `regionName`，`name` 支持站名、拼音、车站编码模糊查询。
- 前端出发地/到达地建议使用 `/station/all` 拉取站点后按 `regionName` 分组，用户选择后向查票接口传站名。

`GET /api/ticket-service/ticket/query`

查询参数：

```json
{
  "departure": "北京南",
  "arrival": "上海虹桥",
  "current": 1,
  "size": 10
}
```

返回核心字段：

```json
{
  "trainList": [
    {
      "trainId": "1",
      "trainNumber": "G1",
      "trainType": 0,
      "departure": "北京南",
      "arrival": "上海虹桥",
      "departureTime": "08:00",
      "arrivalTime": "12:30",
      "seatClassList": [
        { "seatType": 2, "price": 553.0, "quantity": 10 }
      ]
    }
  ],
  "current": 1,
  "size": 10,
  "total": 1
}
```

### 购票和取消

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/ticket-service/ticket/purchase` | 登录 | 购票、锁座、建单 |
| `POST` | `/api/ticket-service/ticket/cancel` | 登录 | 取消未支付订单并回滚座位/余票 |

`POST /api/ticket-service/ticket/purchase`

```json
{
  "trainId": "1",
  "departure": "北京南",
  "arrival": "上海虹桥",
  "chooseSeats": ["012A"],
  "passengers": [
    {
      "passengerId": "2083093199123472384",
      "seatType": 2,
      "ticketType": 0
    }
  ]
}
```

说明：

- `chooseSeats` 可选；传入则优先锁定指定座位，不传则自动分配。
- `ticketType` 可选，`0` 成人，`1` 儿童，`2` 学生，缺省成人。
- 后端会校验未发车、已开售、同一乘车人同日时间冲突、学生票资格、Redis 余票令牌。

返回：

```json
{
  "orderSn": "202607310001",
  "ticketOrderDetailResults": [
    {
      "passengerId": "2083093199123472384",
      "carriageNumber": "01",
      "seatNumber": "012A",
      "seatType": 2,
      "amount": 55300,
      "realName": "李四",
      "idType": 0,
      "idCard": "110101199001011235",
      "phone": "13800000000",
      "ticketType": 0
    }
  ]
}
```

`POST /api/ticket-service/ticket/cancel`

```json
{
  "orderSn": "202607310001"
}
```

## 订单服务 `/api/order-service`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/order-service/order/ticket/query` | 登录/内部 | 按订单号查询订单详情 |
| `GET` | `/api/order-service/order/item/ticket/query` | 内部 | 按订单号和明细 ID 查询订单明细 |
| `GET` | `/api/order-service/order/ticket/page` | 登录 | 分页查询订单 |
| `GET` | `/api/order-service/order/ticket/self/page` | 登录 | 查询当前用户订单 |
| `POST` | `/api/order-service/order/ticket/create` | 内部 | 创建车票订单 |
| `POST` | `/api/order-service/order/ticket/close` | 内部 | 关闭未支付订单 |
| `POST` | `/api/order-service/order/ticket/cancel` | 内部 | 取消订单 |

`GET /api/order-service/order/ticket/self/page`

查询参数：

```json
{
  "current": 1,
  "size": 10,
  "statusType": 0
}
```

`POST /api/order-service/order/ticket/create`

```json
{
  "userId": 1,
  "username": "alice",
  "trainId": 1,
  "trainNumber": "G1",
  "departure": "北京南",
  "arrival": "上海虹桥",
  "departureTime": "2026-08-01T08:00:00",
  "arrivalTime": "2026-08-01T12:30:00",
  "ridingDate": "2026-08-01",
  "source": 0,
  "orderTime": "2026-07-31T15:00:00",
  "ticketOrderItems": [
    {
      "amount": 55300,
      "carriageNumber": "01",
      "seatNumber": "012A",
      "idCard": "110101199001011235",
      "idType": 0,
      "phone": "13800000000",
      "seatType": 2,
      "ticketType": 0,
      "realName": "李四"
    }
  ]
}
```

## 支付服务 `/api/pay-service`

### 支付

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/pay-service/pay/create` | 登录 | 创建支付单 |
| `GET` | `/api/pay-service/pay/query/order-sn` | 登录 | 按订单号查支付单 |
| `GET` | `/api/pay-service/pay/query/pay-sn` | 登录 | 按支付单号查支付单 |
| `POST` | `/api/pay-service/callback/alipay` | 公开回调 | 支付成功回调 |

`POST /api/pay-service/pay/create`

```json
{
  "orderSn": "202607310001",
  "totalAmount": 55300,
  "channel": "ALI_PAY"
}
```

说明：

- `channel` 支持 `ALI_PAY`、`WECHAT_PAY`，缺省为 `ALI_PAY`。
- 重复支付成功回调按幂等成功处理。
- 已关闭订单收到支付成功回调不会被错误推进为已支付。

`POST /api/pay-service/callback/alipay`

```json
{
  "orderSn": "202607310001",
  "tradeNo": "TRADE_NO",
  "status": "TRADE_SUCCESS",
  "payAmount": 55300,
  "gmtPayment": "2026-07-31T15:00:00"
}
```

返回字符串：

```text
success
```

### 退款

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/pay-service/common/refund` | 登录/内部 | 全额或部分退款 |

`POST /api/pay-service/common/refund`

```json
{
  "orderSn": "202607310001",
  "refundType": 12,
  "refundAmount": 55300,
  "refundDetailReqDTOList": [
    {
      "carriageNumber": "01",
      "seatNumber": "012A"
    }
  ]
}
```

说明：

- `refundType` 使用订单退款目标状态：`11` 部分退款，`12` 全额退款。
- 全额退款可不传 `refundAmount`，后端按订单明细计算。
- 部分退款必须传可定位订单明细的字段，后端反查金额和乘车人信息，不信任前端金额/身份字段。
- 重复退款消息不会重复增加余票。

## 智能客服 `/api/customer-service`

### 对话

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/customer-service/chat` | 登录 | SSE 流式智能客服 |
| `GET` | `/api/customer-service/session/list` | 登录 | 当前用户会话列表 |
| `GET` | `/api/customer-service/session/{sessionId}/messages` | 登录 | 会话消息 |
| `DELETE` | `/api/customer-service/session/{sessionId}` | 登录 | 删除会话 |

`POST /api/customer-service/chat`

```json
{
  "sessionId": "optional-session-id",
  "message": "帮我查一下订单"
}
```

响应类型：`text/event-stream`

```text
data: {"type":"token","content":"正在查询"}
data: {"type":"thinking","content":"正在调用业务工具：queryOrder"}
data: {"type":"tool_call","name":"queryOrder","args":{"orderSn":"..."}}
data: {"type":"tool_result","name":"queryOrder","args":{"orderSn":"..."},"result":{"orderSn":"...","status":10},"sources":[]}
data: {"type":"done","sessionId":"..."}
```

SSE 事件说明：

| type | 说明 |
| --- | --- |
| `token` | 助手自然语言回复片段 |
| `thinking` | 服务端业务处理进度，例如准备调用工具 |
| `tool_call` | Function Calling 工具调用参数 |
| `tool_result` | 工具执行结果；`searchKnowledge` 会额外返回 `sources` 信息来源 |
| `done` | 本轮完成，返回会话 ID |
| `error` | 本轮失败，返回错误信息 |

客服工具会基于当前登录用户做订单归属校验。

### 知识库后台

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/customer-service/kb/category` | 登录 | 新增分类 |
| `GET` | `/api/customer-service/kb/category` | 登录 | 分类列表 |
| `PUT` | `/api/customer-service/kb/category/{id}` | 登录 | 修改分类 |
| `DELETE` | `/api/customer-service/kb/category/{id}` | 登录 | 删除分类 |
| `POST` | `/api/customer-service/kb/document/upload` | 登录 | 上传文档并触发入库 |
| `GET` | `/api/customer-service/kb/document` | 登录 | 文档列表 |
| `DELETE` | `/api/customer-service/kb/document/{id}` | 登录 | 删除文档 |
| `GET` | `/api/customer-service/kb/task` | 登录 | 入库任务列表 |
| `GET` | `/api/customer-service/kb/task/{id}` | 登录 | 入库任务详情 |
| `POST` | `/api/customer-service/kb/task/{id}/retry` | 登录 | 重试入库任务 |

`POST /api/customer-service/kb/document/upload`

`multipart/form-data` 参数：

- `file`: 文件
- `categoryId`: 分类 ID
- `parserType`: 可选
- `chunkMode`: 可选

## 管理后台 `/api/admin`

### 仪表盘

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/admin/dashboard` | 管理员 | 仪表盘汇总 |
| `GET` | `/api/admin/stats/order-trend` | 管理员 | 订单趋势 |
| `GET` | `/api/admin/stats/revenue-trend` | 管理员 | 收入趋势 |

### 用户管理

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/admin/user/list` | 管理员 | 用户分页 |
| `GET` | `/api/admin/user/{id}` | 管理员 | 用户详情 |
| `PUT` | `/api/admin/user/{id}/status` | 管理员 | 切换普通用户/管理员 |
| `DELETE` | `/api/admin/user/{id}` | 管理员 | 逻辑删除用户 |
| `GET` | `/api/admin/user/stats` | 管理员 | 用户统计 |

### 订单管理

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/admin/order/list` | 管理员 | 订单分页 |
| `POST` | `/api/admin/order/{orderSn}/cancel` | 管理员 | 管理员取消订单 |
| `GET` | `/api/admin/order/stats` | 管理员 | 订单统计 |

`GET /api/admin/order/list`

查询参数：

```json
{
  "page": 1,
  "size": 10,
  "status": 10,
  "keyword": "G1"
}
```

### 车次和票务管理

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/admin/train/list` | 管理员 | 车次分页 |
| `GET` | `/api/admin/train/{id}` | 管理员 | 车次详情 |
| `POST` | `/api/admin/train` | 管理员 | 新增基础车次 |
| `PUT` | `/api/admin/train/{id}` | 管理员 | 修改基础车次 |
| `DELETE` | `/api/admin/train/{id}` | 管理员 | 删除车次及关联站点/票价/座位 |
| `POST` | `/api/admin/train/publish` | 管理员 | 一站式发布车次 |
| `GET` | `/api/admin/ticket/list` | 管理员 | 车票分页 |

`POST /api/admin/train/publish`

```json
{
  "trainNumber": "G1001",
  "trainType": 0,
  "trainTag": "复兴号",
  "trainBrand": "CR400",
  "saleTime": "2026-07-31T15:00:00",
  "saleStatus": 0,
  "stations": [
    {
      "stationId": 1,
      "stationName": "北京南",
      "region": "北京",
      "arrivalTime": "2026-08-01T07:50:00",
      "departureTime": "2026-08-01T08:00:00",
      "stopoverTime": 10
    },
    {
      "stationId": 2,
      "stationName": "上海虹桥",
      "region": "上海",
      "arrivalTime": "2026-08-01T12:30:00",
      "departureTime": "2026-08-01T12:30:00",
      "stopoverTime": 0
    }
  ],
  "seatPrices": [
    { "seatType": 2, "price": 55300 }
  ],
  "carriages": [
    {
      "carriageNumber": "01",
      "carriageType": 2,
      "seatType": 2,
      "seatCount": 100
    }
  ]
}
```

说明：

- 发布接口会写入车次、经停站、可达区间、区间票价、车厢、座位。
- 发布后会按数据库可用座位刷新 Redis 余票缓存。
- 后端校验经停站数量、重复站名、时间顺序、价格和车厢配置。

## 运营兜底 `/api/admin/ops`

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/admin/ops/timeout-orders/close` | 管理员 | 扫描并关闭超时未支付订单 |
| `POST` | `/api/admin/ops/remaining-ticket/calibrate` | 管理员 | 按 DB 可用座位校准 Redis 余票 |
| `POST` | `/api/admin/ops/order/compensate` | 管理员 | 异常关单补偿 |

`POST /api/admin/ops/timeout-orders/close`

查询参数：

```json
{
  "timeoutMinutes": 30
}
```

返回：

```json
{
  "taskName": "closeTimeoutOrders",
  "scannedCount": 10,
  "handledCount": 8,
  "details": {
    "timeoutMinutes": 30
  }
}
```

## 状态枚举

### 订单状态 `t_order.status`

| 值 | 含义 |
| --- | --- |
| `0` | 待支付 |
| `10` | 已支付 |
| `11` | 部分退款 |
| `12` | 全部退款 |
| `20` | 已完成 |
| `30` | 已关闭/已取消 |

### 订单明细状态 `t_order_item.status`

| 值 | 含义 |
| --- | --- |
| `0` | 待支付 |
| `10` | 已支付 |
| `20` | 已进站 |
| `30` | 已取消 |
| `40` | 已退票 |
| `50` | 已改签 |

### 座位状态 `t_seat.seat_status`

| 值 | 含义 |
| --- | --- |
| `0` | 可用 |
| `1` | 锁定 |
| `2` | 已售 |

### 支付状态 `t_pay.status`

| 值 | 含义 |
| --- | --- |
| `WAIT_BUYER_PAY` | 待支付 |
| `TRADE_SUCCESS` | 支付成功 |
| `TRADE_CLOSED` | 交易关闭 |

### 票种 `ticketType`

| 值 | 含义 |
| --- | --- |
| `0` | 成人票 |
| `1` | 儿童票 |
| `2` | 学生票 |
