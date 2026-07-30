package org.opengoofy.index12306.ticket.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 购票请求。
 */
@Data
public class PurchaseTicketReqDTO {

    private String trainId;
    private String departure;
    private String arrival;
    private List<PurchaseTicketPassengerDetailDTO> passengers;
    /** 可选，指定座位号 */
    private List<String> chooseSeats;
}