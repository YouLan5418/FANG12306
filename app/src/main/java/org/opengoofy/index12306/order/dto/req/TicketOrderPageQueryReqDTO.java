package org.opengoofy.index12306.order.dto.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.framework.convention.page.PageRequest;

/**
 * 订单分页查询请求。
 * statusType：0待支付 1已支付(含退款) 2已完成
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TicketOrderPageQueryReqDTO extends PageRequest {

    private Long userId;
    private Integer statusType;
}