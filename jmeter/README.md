# index12306 JMeter 压测说明

## 推荐执行顺序

1. 打开 `D:\12306\jmeter\index12306-basic-test.jmx`。
2. 确认后端运行在 `http://127.0.0.1:8080`。
3. 先只运行默认开启的两个线程组：
   - `01 公开查询压测`
   - `02 登录态读接口压测`
4. 第一次运行只看 `Summary Report` 和 `View Results Tree` 是否全绿。
5. 稳定后再逐步提高线程数，比如公开查询从 20 -> 50 -> 100。

## 下单压测

`03 下单小流量冒烟 默认关闭` 默认禁用，因为它会真实创建订单、锁定座位、消耗余票。

启用前建议：

- 线程数先设为 `1`，循环 `1`。
- 确认 `users.csv` 里的用户都有乘车人。
- 确认请求体里的 `trainId/departure/arrival` 当前仍有二等座余票。
- 每轮下单后检查我的订单、座位状态、Redis 余票是否符合预期。

## 常用观察指标

- `Error %`：先要求 0%，压大以后再分析业务失败和系统失败。
- `Average / 90% Line / 95% Line / 99% Line`：接口响应时间。
- `Throughput`：吞吐量。
- 后端日志：重点看 ERROR、连接池耗尽、Redis 超时、RocketMQ 重试。
- PostgreSQL/Redis/Docker：观察 CPU、内存、连接数。
