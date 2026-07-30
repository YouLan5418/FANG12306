package org.opengoofy.index12306.order.dto.req;

import lombok.Data;

/**
 * 建单乘车人明细请求。
 */
@Data
public class TicketOrderItemCreateReqDTO {

    private Integer amount;
    private String carriageNumber;
    private String seatNumber;
    private String idCard;
    private Integer idType;
    private String phone;
    private Integer seatType;
    private Integer ticketType;
    private String realName;
}