package org.opengoofy.index12306.order.dto.req;

import lombok.Data;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 创建车票订单请求。
 */
@Data
public class TicketOrderCreateReqDTO {

    /** 可空，后端生成 */
    private String orderSn;
    private Long userId;
    private String username;
    private Long trainId;
    private String trainNumber;
    private String departure;
    private String arrival;
    private Date departureTime;
    private Date arrivalTime;
    private LocalDate ridingDate;
    private Integer source;
    private Date orderTime;
    private List<TicketOrderItemCreateReqDTO> ticketOrderItems;
}