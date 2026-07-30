package org.opengoofy.index12306.order.common.constant;

/**
 * 订单域 RocketMQ 常量。
 */
public final class OrderRocketMQConstant {

    /** 延迟关单主题（生产 delayLevel=14，即 2 小时后投递） */
    public static final String ORDER_DELAY_CLOSE_TOPIC = "index12306_order_delay-close-order_topic";

    /** 延迟关单消费组 */
    public static final String ORDER_DELAY_CLOSE_CONSUMER_GROUP = "index12306_order_delay-close-order_cg";

    /** 支付结果回调主题（P6 支付域发送，订单域消费） */
    public static final String PAY_RESULT_CALLBACK_TOPIC = "index12306_pay_pay-result-callback_topic";

    /** 支付结果回调-订单消费组 */
    public static final String PAY_RESULT_CALLBACK_ORDER_CG = "index12306_pay_pay-result-callback-order_cg";

    /** 支付结果回调-票务消费组（座位置 SOLD） */
    public static final String PAY_RESULT_CALLBACK_TICKET_CG = "index12306_pay_pay-result-callback-ticket_cg";

    /** 退款结果回调主题 */
    public static final String REFUND_RESULT_CALLBACK_TOPIC = "index12306_pay_refund-result-callback_topic";

    /** 退款结果回调-订单消费组 */
    public static final String REFUND_RESULT_CALLBACK_ORDER_CG = "index12306_pay_refund-result-callback-order_cg";

    /** 退款结果回调-票务消费组（座位/余票回滚） */
    public static final String REFUND_RESULT_CALLBACK_TICKET_CG = "index12306_pay_refund-result-callback-ticket_cg";
    private OrderRocketMQConstant() {
    }
}