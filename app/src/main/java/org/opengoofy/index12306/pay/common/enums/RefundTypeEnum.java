package org.opengoofy.index12306.pay.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 退款类型，code 对齐订单状态：11 部分退款 12 全部退款。
 */
@Getter
@AllArgsConstructor
public enum RefundTypeEnum {

    PARTIAL_REFUND(11),
    FULL_REFUND(12);

    private final int code;
}