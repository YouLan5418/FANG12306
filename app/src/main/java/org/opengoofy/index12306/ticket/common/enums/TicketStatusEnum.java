package org.opengoofy.index12306.ticket.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 车票状态：0未支付 1已支付
 */
@Getter
@AllArgsConstructor
public enum TicketStatusEnum {

    UNPAID(0),
    PAID(1);

    private final int code;
}