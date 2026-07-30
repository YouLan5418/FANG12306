package org.opengoofy.index12306.order.mq.consumer;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.opengoofy.index12306.order.common.constant.OrderRocketMQConstant;
import org.opengoofy.index12306.order.common.enums.OrderItemStatusEnum;
import org.opengoofy.index12306.order.common.enums.OrderStatusEnum;
import org.opengoofy.index12306.order.dto.domain.OrderStatusReversalDTO;
import org.opengoofy.index12306.order.mq.event.PayResultCallbackOrderEvent;
import org.opengoofy.index12306.order.service.OrderService;
import org.springframework.stereotype.Component;

/**
 * 支付结果回写消费者（order 侧）：回写支付时间/渠道，并将订单由待支付流转为已支付。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = OrderRocketMQConstant.PAY_RESULT_CALLBACK_TOPIC,
        consumerGroup = OrderRocketMQConstant.PAY_RESULT_CALLBACK_ORDER_CG)
public class PayResultCallbackOrderConsumer implements RocketMQListener<String> {

    private final OrderService orderService;

    @Override
    public void onMessage(String message) {
        PayResultCallbackOrderEvent event = JSON.parseObject(message, PayResultCallbackOrderEvent.class);
        log.info("收到支付结果回写消息，orderSn={}", event.getOrderSn());
        orderService.payCallbackOrder(event);
        orderService.statusReversal(OrderStatusReversalDTO.builder()
                .orderSn(event.getOrderSn())
                .orderStatus(OrderStatusEnum.ALREADY_PAID.getStatus())
                .orderItemStatus(OrderItemStatusEnum.ALREADY_PAID.getStatus())
                .build());
    }
}