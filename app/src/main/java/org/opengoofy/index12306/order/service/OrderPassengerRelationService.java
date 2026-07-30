package org.opengoofy.index12306.order.service;

import org.opengoofy.index12306.order.dao.entity.OrderItemPassengerDO;

import java.util.List;

/**
 * 订单乘车人关系服务。
 */
public interface OrderPassengerRelationService {

    void saveBatch(List<OrderItemPassengerDO> relations);

    List<OrderItemPassengerDO> listByIdCard(String idCard);
}