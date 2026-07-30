package org.opengoofy.index12306.pay.dto.resp;

import lombok.Data;

import java.util.Date;

/**
 * 支付单查询响应。
 */
@Data
public class PayInfoRespDTO {

    private String paySn;
    private String orderSn;
    private Integer totalAmount;
    private Integer payAmount;
    private String status;
    private Date gmtPayment;
    private String channel;
    private String tradeType;
}