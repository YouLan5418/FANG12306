package org.opengoofy.index12306.ticket.dto.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.framework.convention.page.PageRequest;

import java.time.LocalDate;

/**
 * 车票查询请求。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TicketPageQueryReqDTO extends PageRequest {

    private String fromStation;
    private String toStation;
    private LocalDate departureDate;
    private String departure;
    private String arrival;
}