package org.opengoofy.index12306.order.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.common.toolkit.AESCryptUtil;
import org.opengoofy.index12306.order.dao.entity.OrderItemPassengerDO;
import org.opengoofy.index12306.order.dao.mapper.OrderItemPassengerMapper;
import org.opengoofy.index12306.order.service.OrderPassengerRelationService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单乘车人关系服务实现。按证件号查询需显式加密（Wrapper 参数不经过 TypeHandler）。
 */
@Service
@RequiredArgsConstructor
public class OrderPassengerRelationServiceImpl implements OrderPassengerRelationService {

    private final OrderItemPassengerMapper orderItemPassengerMapper;

    @Override
    public void saveBatch(List<OrderItemPassengerDO> relations) {
        relations.forEach(orderItemPassengerMapper::insert);
    }

    @Override
    public List<OrderItemPassengerDO> listByIdCard(String idCard) {
        return orderItemPassengerMapper.selectList(Wrappers.lambdaQuery(OrderItemPassengerDO.class)
                .eq(OrderItemPassengerDO::getIdCard, AESCryptUtil.encrypt(idCard)));
    }
}