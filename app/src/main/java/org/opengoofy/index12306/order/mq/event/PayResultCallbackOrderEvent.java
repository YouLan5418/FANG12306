package org.opengoofy.index12306.order.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 支付结果回调事件（P6 支付域发送，订单域消费以回写支付信息并流转状态）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayResultCallbackOrderEvent {

    private String orderSn;
    private String paySn;
    private String channel;
    private Date gmtPayment;
    private Integer payAmount;
    private String status;
}