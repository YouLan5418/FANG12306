package org.opengoofy.index12306.ticket.dto.req;

import lombok.Data;

/**
 * 购票乘车人明细。
 */
@Data
public class PurchaseTicketPassengerDetailDTO {

    private String passengerId;
    private Integer seatType;
}