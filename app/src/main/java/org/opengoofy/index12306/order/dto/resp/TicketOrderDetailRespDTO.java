package org.opengoofy.index12306.order.dto.resp;

import lombok.Data;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 订单详情响应。
 */
@Data
public class TicketOrderDetailRespDTO {

    private String orderSn;
    private Long userId;
    private String trainNumber;
    private String departure;
    private String arrival;
    private Integer status;
    private Date departureTime;
    private Date arrivalTime;
    private LocalDate ridingDate;
    private Date orderTime;
    private Date payTime;
    private List<TicketOrderPassengerDetailRespDTO> passengerDetails;
}