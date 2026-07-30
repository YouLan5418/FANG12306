package org.opengoofy.index12306.customerservice.tool;

import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.customerservice.chat.ToolDefinition;
import org.opengoofy.index12306.customerservice.fc.ToolExecutionContext;
import org.opengoofy.index12306.customerservice.fc.ToolExecutor;
import org.opengoofy.index12306.order.dto.resp.TicketOrderDetailRespDTO;
import org.opengoofy.index12306.order.service.OrderService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 查订单工具：按订单号查询当前用户的订单状态。
 * 归属校验：订单 userId 必须与当前登录用户一致，否则拒绝查询。
 * 结果截断为关键字段。
 */
@Component
@RequiredArgsConstructor
public class QueryOrderTool implements ToolExecutor {

    private final OrderService orderService;

    @Override
    public String name() {
        return "queryOrder";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("queryOrder")
                .description("查询当前用户的火车票订单状态，需要提供订单号")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "orderSn", Map.of("type", "string", "description", "订单号")
                        ),
                        "required", List.of("orderSn")
                ))
                .build();
    }

    @Override
    public String execute(Map<String, Object> args, ToolExecutionContext ctx) {
        String orderSn = (String) args.get("orderSn");
        if (orderSn == null || orderSn.isBlank()) {
            return "{\"error\":\"缺少必要参数: orderSn\"}";
        }

        TicketOrderDetailRespDTO order = orderService.queryTicketOrderByOrderSn(orderSn);
        if (order == null) {
            return "{\"error\":\"订单不存在: " + orderSn + "\"}";
        }

        // 归属校验：订单必须属于当前用户
        if (!Objects.equals(String.valueOf(order.getUserId()), ctx.getUserId())) {
            return "{\"error\":\"无权查询此订单\"}";
        }

        JSONObject result = new JSONObject();
        result.put("orderSn", order.getOrderSn());
        result.put("status", order.getStatus());
        result.put("trainNumber", order.getTrainNumber());
        result.put("departure", order.getDeparture());
        result.put("arrival", order.getArrival());
        return result.toJSONString();
    }
}