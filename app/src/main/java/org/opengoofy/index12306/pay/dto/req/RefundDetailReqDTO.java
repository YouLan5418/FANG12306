package org.opengoofy.index12306.pay.dto.req;

import lombok.Data;

/**
 * 退款明细（部分退款时指定要退的座位）。
 */
@Data
public class RefundDetailReqDTO {

    private String carriageNumber;
    private String seatNumber;
    private Integer seatType;
    private String realName;
    private Integer idType;
    private String idCard;
}