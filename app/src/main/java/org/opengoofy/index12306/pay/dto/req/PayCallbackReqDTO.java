package org.opengoofy.index12306.pay.dto.req;

import lombok.Data;

import java.util.Date;

/**
 * 支付回调请求（模拟支付宝异步回调）。
 */
@Data
public class PayCallbackReqDTO {

    private String orderSn;
    private String tradeNo;
    private String status;
    private Integer payAmount;
    private Date gmtPayment;
}