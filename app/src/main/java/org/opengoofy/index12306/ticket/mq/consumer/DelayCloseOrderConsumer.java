package org.opengoofy.index12306.ticket.mq.consumer;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.opengoofy.index12306.order.common.constant.OrderRocketMQConstant;
import org.opengoofy.index12306.order.mq.event.DelayCloseOrderEvent;
import org.opengoofy.index12306.ticket.service.TicketService;
import org.springframework.stereotype.Component;

/**
 * 延迟关单消费者（ticket 侧）：超时未支付订单关闭并回滚座位/余票。
 * delayLevel=14 即 2 小时后投递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = OrderRocketMQConstant.ORDER_DELAY_CLOSE_TOPIC,
        consumerGroup = OrderRocketMQConstant.ORDER_DELAY_CLOSE_CONSUMER_GROUP)
public class DelayCloseOrderConsumer implements RocketMQListener<String> {

    private final TicketService ticketService;

    @Override
    public void onMessage(String message) {
        DelayCloseOrderEvent event = JSON.parseObject(message, DelayCloseOrderEvent.class);
        log.info("收到延迟关单消息，orderSn={}", event.getOrderSn());
        ticketService.closeOrderAndRollback(event.getOrderSn());
    }
}