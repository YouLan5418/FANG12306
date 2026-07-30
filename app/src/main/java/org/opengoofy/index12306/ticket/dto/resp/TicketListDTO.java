package org.opengoofy.index12306.ticket.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 车次查询结果项。
 */
@Data
public class TicketListDTO {

    private String trainId;
    private String trainNumber;
    private Integer trainType;
    private String departure;
    private String arrival;
    private String departureTime;
    private String arrivalTime;
    private List<SeatClassDTO> seatClassList;
}