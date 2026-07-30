package org.opengoofy.index12306.pay.dto.resp;

import lombok.Data;

/**
 * 退款响应。
 */
@Data
public class RefundRespDTO {

    private String refundSn;
    private String orderSn;
    private Integer refundAmount;
    private String status;
}