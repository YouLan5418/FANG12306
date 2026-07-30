package org.opengoofy.index12306.order.mq.produce;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.opengoofy.index12306.order.common.constant.OrderRocketMQConstant;
import org.opengoofy.index12306.order.mq.event.DelayCloseOrderEvent;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 延迟关单消息投递：delayLevel=14（生产 2 小时；测试用短 level 加速）。
 */
@Component
@RequiredArgsConstructor
public class DelayCloseOrderSendProduce {

    /** 生产环境延迟级别 14 = 2 小时 */
    public static final int DELAY_LEVEL = 14;

    private final RocketMQTemplate rocketMQTemplate;

    public SendResult sendMessage(DelayCloseOrderEvent event) {
        return rocketMQTemplate.syncSend(
                OrderRocketMQConstant.ORDER_DELAY_CLOSE_TOPIC,
                MessageBuilder.withPayload(JSON.toJSONString(event)).build(),
                3000,
                DELAY_LEVEL);
    }
}