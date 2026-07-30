package org.opengoofy.index12306.order.common.enums;

import lombok.AllArgsConstructor;
import org.opengoofy.index12306.framework.convention.errorcode.IErrorCode;

/**
 * 订单状态流转错误码。
 */
@AllArgsConstructor
public enum OrderCanalErrorCodeEnum implements IErrorCode {

    ORDER_CANAL_UNKNOWN_ERROR("B000200", "订单不存在"),
    ORDER_CANAL_STATUS_ERROR("B000201", "订单状态非法，仅待支付订单可关闭/取消"),
    ORDER_CANAL_REPETITION_ERROR("B000202", "订单正在处理中，请勿重复提交"),
    ORDER_CANAL_ERROR("B000203", "订单关闭/取消失败"),
    ORDER_STATUS_REVERSAL_ERROR("B000204", "订单状态流转失败，非待支付状态不可流转");

    private final String code;
    private final String message;

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}