package org.opengoofy.index12306.ticket.dto.req;

import lombok.Data;

/**
 * 取消订单请求（票务侧入口）。
 */
@Data
public class CancelTicketReqDTO {

    private String orderSn;
}