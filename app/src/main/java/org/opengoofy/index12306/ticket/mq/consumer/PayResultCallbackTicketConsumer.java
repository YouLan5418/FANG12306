package org.opengoofy.index12306.ticket.mq.consumer;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.opengoofy.index12306.order.common.constant.OrderRocketMQConstant;
import org.opengoofy.index12306.order.mq.event.PayResultCallbackOrderEvent;
import org.opengoofy.index12306.ticket.service.TicketService;
import org.springframework.stereotype.Component;

/**
 * 支付结果回写消费者（ticket 侧）：锁定座位置为已售，t_ticket 置为已支付。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = OrderRocketMQConstant.PAY_RESULT_CALLBACK_TOPIC,
        consumerGroup = OrderRocketMQConstant.PAY_RESULT_CALLBACK_TICKET_CG)
public class PayResultCallbackTicketConsumer implements RocketMQListener<String> {

    private final TicketService ticketService;

    @Override
    public void onMessage(String message) {
        PayResultCallbackOrderEvent event = JSON.parseObject(message, PayResultCallbackOrderEvent.class);
        log.info("收到支付结果回写消息（票务侧），orderSn={}", event.getOrderSn());
        ticketService.markSeatsSold(event.getOrderSn());
    }
}