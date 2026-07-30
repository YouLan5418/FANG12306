package org.opengoofy.index12306.ticket.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 购票响应。
 */
@Data
public class TicketPurchaseRespDTO {

    private String orderSn;
    private List<TrainPurchaseTicketRespDTO> ticketOrderDetailResults;
}