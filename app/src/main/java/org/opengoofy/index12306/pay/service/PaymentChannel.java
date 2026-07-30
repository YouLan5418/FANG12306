package org.opengoofy.index12306.pay.service;

/**
 * 支付渠道抽象（下单/退款）。重构版未接入真实支付宝 SDK，提供模拟实现便于联调与测试。
 */
public interface PaymentChannel {

    PayCreateResult createPay(String paySn, String orderSn, Integer totalAmount, String channel);

    RefundResult refund(String paySn, String orderSn, Integer refundAmount);

    record PayCreateResult(String payUrl, String tradeNo) {
    }

    record RefundResult(String tradeNo, String status) {
    }
}