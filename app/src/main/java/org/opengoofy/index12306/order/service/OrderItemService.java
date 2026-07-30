package org.opengoofy.index12306.order.service;

import org.opengoofy.index12306.order.dao.entity.OrderItemDO;

import java.util.List;

/**
 * 订单明细服务。
 */
public interface OrderItemService {

    void saveBatch(List<OrderItemDO> orderItems);

    List<OrderItemDO> listByOrderSn(String orderSn);

    List<OrderItemDO> listByOrderSnAndIds(String orderSn, List<String> ids);

    int updateStatusByOrderSn(String orderSn, Integer status);
}