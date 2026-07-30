package org.opengoofy.index12306.order.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengoofy.index12306.pay.dto.req.RefundDetailReqDTO;

import java.util.List;

/**
 * 退款结果回调事件（支付域发送，订单/票务消费）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResultCallbackOrderEvent {

    private String orderSn;
    /** 11 部分退款 12 全部退款 */
    private Integer refundType;
    /** 部分退款时指定要退的座位明细；全部退款为空 */
    private List<RefundDetailReqDTO> partialRefundDetails;
}