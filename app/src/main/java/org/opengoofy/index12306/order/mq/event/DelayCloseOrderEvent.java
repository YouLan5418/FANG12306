package org.opengoofy.index12306.order.mq.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengoofy.index12306.order.dto.req.TicketOrderItemCreateReqDTO;

import java.util.List;

/**
 * 延迟关单事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelayCloseOrderEvent {

    private String orderSn;
    private String trainId;
    private String departure;
    private String arrival;
    private List<TicketOrderItemCreateReqDTO> ticketOrderItems;
}