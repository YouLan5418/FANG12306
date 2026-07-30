package org.opengoofy.index12306.pay.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易状态（与支付渠道对齐，t_pay.status 字符串）。
 */
@Getter
@AllArgsConstructor
public enum TradeStatusEnum {

    WAIT_BUYER_PAY("WAIT_BUYER_PAY"),
    TRADE_SUCCESS("TRADE_SUCCESS"),
    TRADE_CLOSED("TRADE_CLOSED");

    private final String tradeCode;
}