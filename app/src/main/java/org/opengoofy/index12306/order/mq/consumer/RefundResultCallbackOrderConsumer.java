package org.opengoofy.index12306.order.mq.consumer;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.opengoofy.index12306.order.common.constant.OrderRocketMQConstant;
import org.opengoofy.index12306.order.mq.event.RefundResultCallbackOrderEvent;
import org.opengoofy.index12306.order.service.OrderService;
import org.springframework.stereotype.Component;

/**
 * 退款结果回写消费者（order 侧）：将订单由已支付/部分退款流转为部分/全部退款。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = OrderRocketMQConstant.REFUND_RESULT_CALLBACK_TOPIC,
        consumerGroup = OrderRocketMQConstant.REFUND_RESULT_CALLBACK_ORDER_CG)
public class RefundResultCallbackOrderConsumer implements RocketMQListener<String> {

    private final OrderService orderService;

    @Override
    public void onMessage(String message) {
        RefundResultCallbackOrderEvent event = JSON.parseObject(message, RefundResultCallbackOrderEvent.class);
        log.info("收到退款结果回写消息，orderSn={}, refundType={}", event.getOrderSn(), event.getRefundType());
        orderService.refundReversal(event.getOrderSn(), event.getRefundType(), event.getPartialRefundDetails());
    }
}