package org.opengoofy.index12306.order.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.order.dao.entity.OrderItemDO;
import org.opengoofy.index12306.order.dao.mapper.OrderItemMapper;
import org.opengoofy.index12306.order.service.OrderItemService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单明细服务实现。
 */
@Service
@RequiredArgsConstructor
public class OrderItemServiceImpl implements OrderItemService {

    private final OrderItemMapper orderItemMapper;

    @Override
    public void saveBatch(List<OrderItemDO> orderItems) {
        orderItems.forEach(orderItemMapper::insert);
    }

    @Override
    public List<OrderItemDO> listByOrderSn(String orderSn) {
        return orderItemMapper.selectList(Wrappers.lambdaQuery(OrderItemDO.class)
                .eq(OrderItemDO::getOrderSn, orderSn));
    }

    @Override
    public List<OrderItemDO> listByOrderSnAndIds(String orderSn, List<String> ids) {
        return orderItemMapper.selectList(Wrappers.lambdaQuery(OrderItemDO.class)
                .eq(OrderItemDO::getOrderSn, orderSn)
                .in(ids != null && !ids.isEmpty(), OrderItemDO::getId, ids));
    }

    @Override
    public int updateStatusByIds(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return orderItemMapper.update(null, Wrappers.lambdaUpdate(OrderItemDO.class)
                .in(OrderItemDO::getId, ids)
                .set(OrderItemDO::getStatus, status));
    }

    @Override
    public int updateStatusByOrderSn(String orderSn, Integer status) {
        return orderItemMapper.update(null, Wrappers.lambdaUpdate(OrderItemDO.class)
                .eq(OrderItemDO::getOrderSn, orderSn)
                .set(OrderItemDO::getStatus, status));
    }
}