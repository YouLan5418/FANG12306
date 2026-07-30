package org.opengoofy.index12306.ticket.mq.consumer;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.opengoofy.index12306.order.common.constant.OrderRocketMQConstant;
import org.opengoofy.index12306.order.mq.event.RefundResultCallbackOrderEvent;
import org.opengoofy.index12306.ticket.service.TicketService;
import org.springframework.stereotype.Component;

/**
 * 退款结果回写消费者（ticket 侧）：座位置可用、余票恢复、t_ticket 回滚（修复原项目缺口）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = OrderRocketMQConstant.REFUND_RESULT_CALLBACK_TOPIC,
        consumerGroup = OrderRocketMQConstant.REFUND_RESULT_CALLBACK_TICKET_CG)
public class RefundResultCallbackTicketConsumer implements RocketMQListener<String> {

    private final TicketService ticketService;

    @Override
    public void onMessage(String message) {
        RefundResultCallbackOrderEvent event = JSON.parseObject(message, RefundResultCallbackOrderEvent.class);
        log.info("收到退款结果回写消息（票务侧），orderSn={}", event.getOrderSn());
        ticketService.rollbackRefund(event.getOrderSn(), event.getPartialRefundDetails());
    }
}