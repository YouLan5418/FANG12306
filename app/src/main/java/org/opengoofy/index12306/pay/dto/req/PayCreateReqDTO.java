package org.opengoofy.index12306.pay.dto.req;

import lombok.Data;

/**
 * 创建支付单请求。
 */
@Data
public class PayCreateReqDTO {

    private String orderSn;
    /** 单位：分 */
    private Integer totalAmount;
    /** ALI_PAY / WECHAT_PAY，缺省 ALI_PAY */
    private String channel;
}