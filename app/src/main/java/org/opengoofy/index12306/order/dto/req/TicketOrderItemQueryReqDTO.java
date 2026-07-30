package org.opengoofy.index12306.order.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 按订单号+明细 ID 查询乘车人明细请求。
 */
@Data
public class TicketOrderItemQueryReqDTO {

    private String orderSn;
    private List<String> orderItemRecordIds;
}