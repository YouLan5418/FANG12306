package org.opengoofy.index12306.order.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态：0待支付 10已支付 11部分退款 12全部退款 20已完成 30已取消/关闭
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    PENDING_PAYMENT(0),
    ALREADY_PAID(10),
    PARTIAL_REFUND(11),
    FULL_REFUND(12),
    COMPLETED(20),
    CLOSED(30);

    private final int status;
}