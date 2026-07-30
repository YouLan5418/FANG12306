package org.opengoofy.index12306.pay.dto.resp;

import lombok.Data;

/**
 * 创建支付单响应。
 */
@Data
public class PayRespDTO {

    private String paySn;
    private String payUrl;
    private String orderSn;
    private Integer totalAmount;
    private String status;
}