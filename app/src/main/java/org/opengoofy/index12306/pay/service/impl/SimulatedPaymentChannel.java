package org.opengoofy.index12306.pay.service.impl;

import org.opengoofy.index12306.pay.common.enums.TradeStatusEnum;
import org.opengoofy.index12306.pay.service.PaymentChannel;
import org.springframework.stereotype.Component;

/**
 * 模拟支付渠道：返回伪造的支付链接与退款成功结果。
 * 生产环境替换为真实支付宝/微信实现即可。
 */
@Component
public class SimulatedPaymentChannel implements PaymentChannel {

    @Override
    public PayCreateResult createPay(String paySn, String orderSn, Integer totalAmount, String channel) {
        String payUrl = "https://pay.sim/alipay?paySn=" + paySn + "&orderSn=" + orderSn;
        return new PayCreateResult(payUrl, "SIMTRADE_" + System.currentTimeMillis());
    }

    @Override
    public RefundResult refund(String paySn, String orderSn, Integer refundAmount) {
        return new RefundResult("SIMREF_" + System.currentTimeMillis(), TradeStatusEnum.TRADE_CLOSED.getTradeCode());
    }
}