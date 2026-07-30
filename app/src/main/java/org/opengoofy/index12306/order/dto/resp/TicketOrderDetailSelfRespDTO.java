package org.opengoofy.index12306.order.dto.resp;

import lombok.Data;

import java.time.LocalDate;

/**
 * 用户自己的订单明细响应（按乘车人证件查询，含座位信息）。
 */
@Data
public class TicketOrderDetailSelfRespDTO {

    private String orderSn;
    private String trainNumber;
    private String departure;
    private String arrival;
    private Integer status;
    private LocalDate ridingDate;
    private String seatNumber;
    private String carriageNumber;
    private Integer seatType;
    private Integer amount;
}