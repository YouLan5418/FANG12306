package org.opengoofy.index12306.order.dto.req;

import lombok.Data;

/**
 * 取消/关闭订单请求。
 */
@Data
public class CancelTicketOrderReqDTO {

    private String orderSn;
}