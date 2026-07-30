package org.opengoofy.index12306.ticket.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 座位等级与余票/价格。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatClassDTO {

    private Integer seatType;
    private Integer quantity;
    private BigDecimal price;
}