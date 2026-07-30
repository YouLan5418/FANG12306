package org.opengoofy.index12306.order.mq.consumer;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.opengoofy.index12306.order.common.constant.OrderRocketMQConstant;
import org.opengoofy.index12306.order.dto.req.CancelTicketOrderReqDTO;
import org.opengoofy.index12306.order.mq.event.DelayCloseOrderEvent;
import org.opengoofy.index12306.order.service.OrderService;
import org.springframework.stereotype.Component;

/**
 * 延迟关单消费者：超时未支付订单自动关闭（delayLevel=14 即 2 小时后投递）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = OrderRocketMQConstant.ORDER_DELAY_CLOSE_TOPIC,
        consumerGroup = OrderRocketMQConstant.ORDER_DELAY_CLOSE_CONSUMER_GROUP)
public class DelayCloseOrderConsumer implements RocketMQListener<String> {

    private final OrderService orderService;

    @Override
    public void onMessage(String message) {
        DelayCloseOrderEvent event = JSON.parseObject(message, DelayCloseOrderEvent.class);
        log.info("收到延迟关单消息，orderSn={}", event.getOrderSn());
        CancelTicketOrderReqDTO req = new CancelTicketOrderReqDTO();
        req.setOrderSn(event.getOrderSn());
        boolean closed = orderService.closeTickOrder(req);
        if (!closed) {
            log.info("订单已支付或不存在，跳过关单: {}", event.getOrderSn());
        }
    }
}