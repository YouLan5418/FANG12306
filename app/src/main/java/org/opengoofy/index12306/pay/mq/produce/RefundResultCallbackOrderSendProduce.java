package org.opengoofy.index12306.pay.mq.produce;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.opengoofy.index12306.order.common.constant.OrderRocketMQConstant;
import org.opengoofy.index12306.order.mq.event.RefundResultCallbackOrderEvent;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 退款结果回调消息投递。
 */
@Component
@RequiredArgsConstructor
public class RefundResultCallbackOrderSendProduce {

    private final RocketMQTemplate rocketMQTemplate;

    public SendResult sendMessage(RefundResultCallbackOrderEvent event) {
        return rocketMQTemplate.syncSend(
                OrderRocketMQConstant.REFUND_RESULT_CALLBACK_TOPIC,
                MessageBuilder.withPayload(com.alibaba.fastjson2.JSON.toJSONString(event)).build());
    }
}