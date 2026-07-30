package org.opengoofy.index12306.order.dto.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单状态流转 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusReversalDTO {

    private String orderSn;
    private Integer orderStatus;
    private Integer orderItemStatus;
}