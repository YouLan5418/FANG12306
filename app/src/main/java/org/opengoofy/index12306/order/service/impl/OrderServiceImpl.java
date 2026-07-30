package org.opengoofy.index12306.order.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.opengoofy.index12306.common.core.UserContext;
import org.opengoofy.index12306.framework.convention.exception.ClientException;
import org.opengoofy.index12306.framework.convention.exception.ServiceException;
import org.opengoofy.index12306.framework.convention.page.PageResponse;
import org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil;
import org.opengoofy.index12306.order.common.enums.OrderCanalErrorCodeEnum;
import org.opengoofy.index12306.order.common.enums.OrderItemStatusEnum;
import org.opengoofy.index12306.order.common.enums.OrderStatusEnum;
import org.opengoofy.index12306.order.dao.entity.OrderDO;
import org.opengoofy.index12306.order.dao.entity.OrderItemDO;
import org.opengoofy.index12306.order.dao.entity.OrderItemPassengerDO;
import org.opengoofy.index12306.order.dao.mapper.OrderMapper;
import org.opengoofy.index12306.order.dto.domain.OrderStatusReversalDTO;
import org.opengoofy.index12306.order.dto.req.CancelTicketOrderReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderCreateReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderItemCreateReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderItemQueryReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderPageQueryReqDTO;
import org.opengoofy.index12306.order.dto.resp.TicketOrderDetailRespDTO;
import org.opengoofy.index12306.order.dto.resp.TicketOrderDetailSelfRespDTO;
import org.opengoofy.index12306.order.dto.resp.TicketOrderPassengerDetailRespDTO;
import org.opengoofy.index12306.order.mq.event.DelayCloseOrderEvent;
import org.opengoofy.index12306.order.mq.event.PayResultCallbackOrderEvent;
import org.opengoofy.index12306.order.mq.produce.DelayCloseOrderSendProduce;
import org.opengoofy.index12306.order.service.OrderItemService;
import org.opengoofy.index12306.order.service.OrderPassengerRelationService;
import org.opengoofy.index12306.order.service.OrderService;
import org.opengoofy.index12306.order.service.orderid.OrderIdGeneratorManager;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 订单服务实现：建单（三表写入 + 延迟关单 MQ）、查询分页、状态机流转。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String LOCK_CANCEL_PREFIX = "order:cancel:order_sn_";
    private static final String LOCK_STATUS_REVERSAL_PREFIX = "order:status-reversal:order_sn_";

    private final OrderMapper orderMapper;
    private final OrderItemService orderItemService;
    private final OrderPassengerRelationService orderPassengerRelationService;
    private final RedissonClient redissonClient;
    private final DelayCloseOrderSendProduce delayCloseOrderSendProduce;

    @Override
    public TicketOrderDetailRespDTO queryTicketOrderByOrderSn(String orderSn) {
        OrderDO orderDO = orderMapper.selectOne(Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, orderSn));
        if (orderDO == null) {
            throw new ClientException("订单不存在");
        }
        TicketOrderDetailRespDTO result = BeanUtil.copyProperties(orderDO, TicketOrderDetailRespDTO.class);
        List<OrderItemDO> items = orderItemService.listByOrderSn(orderSn);
        result.setPassengerDetails(BeanUtil.copyToList(items, TicketOrderPassengerDetailRespDTO.class));
        return result;
    }

    @Override
    public PageResponse<TicketOrderDetailRespDTO> pageTicketOrder(TicketOrderPageQueryReqDTO requestParam) {
        Page<OrderDO> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        IPage<OrderDO> orderPage = orderMapper.selectPage(page, Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getUserId, requestParam.getUserId())
                .in(OrderDO::getStatus, buildOrderStatusList(requestParam.getStatusType()))
                .orderByDesc(OrderDO::getOrderTime));
        List<TicketOrderDetailRespDTO> records = orderPage.getRecords().stream()
                .map(each -> {
                    TicketOrderDetailRespDTO result = BeanUtil.copyProperties(each, TicketOrderDetailRespDTO.class);
                    result.setPassengerDetails(BeanUtil.copyToList(orderItemService.listByOrderSn(each.getOrderSn()), TicketOrderPassengerDetailRespDTO.class));
                    return result;
                })
                .collect(Collectors.toList());
        PageResponse<TicketOrderDetailRespDTO> response = new PageResponse<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        response.setRecords(records);
        return response;
    }

    @Override
    public PageResponse<TicketOrderDetailSelfRespDTO> pageSelfTicketOrder(TicketOrderPageQueryReqDTO requestParam) {
        // 当前登录用户名查询自己的订单（按 userId 路由）
        Long userId = requestParam.getUserId();
        Page<OrderDO> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        IPage<OrderDO> orderPage = orderMapper.selectPage(page, Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getUserId, userId)
                .orderByDesc(OrderDO::getOrderTime));
        List<TicketOrderDetailSelfRespDTO> records = orderPage.getRecords().stream()
                .map(each -> {
                    List<OrderItemDO> items = orderItemService.listByOrderSn(each.getOrderSn());
                    TicketOrderDetailSelfRespDTO result = BeanUtil.copyProperties(each, TicketOrderDetailSelfRespDTO.class);
                    if (!items.isEmpty()) {
                        BeanUtil.copyProperties(items.get(0), result, "orderSn", "status");
                        result.setOrderSn(each.getOrderSn());
                        result.setStatus(each.getStatus());
                    }
                    return result;
                })
                .collect(Collectors.toList());
        PageResponse<TicketOrderDetailSelfRespDTO> response = new PageResponse<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        response.setRecords(records);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String createTicketOrder(TicketOrderCreateReqDTO requestParam) {
        String orderSn = Objects.toString(requestParam.getOrderSn(),
                OrderIdGeneratorManager.generateOrderSn(requestParam.getUserId()));
        OrderDO orderDO = BeanUtil.copyProperties(requestParam, OrderDO.class);
        orderDO.setId(SnowflakeIdUtil.nextId());
        orderDO.setOrderSn(orderSn);
        orderDO.setStatus(OrderStatusEnum.PENDING_PAYMENT.getStatus());
        orderMapper.insert(orderDO);

        List<TicketOrderItemCreateReqDTO> ticketOrderItems = requestParam.getTicketOrderItems();
        List<OrderItemDO> orderItemDOList = new ArrayList<>();
        List<OrderItemPassengerDO> orderPassengerRelationDOList = new ArrayList<>();
        for (TicketOrderItemCreateReqDTO each : ticketOrderItems) {
            OrderItemDO orderItemDO = BeanUtil.copyProperties(each, OrderItemDO.class);
            orderItemDO.setId(SnowflakeIdUtil.nextId());
            orderItemDO.setOrderSn(orderSn);
            orderItemDO.setUserId(requestParam.getUserId());
            orderItemDO.setUsername(requestParam.getUsername());
            orderItemDO.setTrainId(requestParam.getTrainId());
            orderItemDO.setStatus(OrderItemStatusEnum.PENDING_PAYMENT.getStatus());
            orderItemDOList.add(orderItemDO);

            OrderItemPassengerDO relation = new OrderItemPassengerDO();
            relation.setId(SnowflakeIdUtil.nextId());
            relation.setOrderSn(orderSn);
            relation.setIdType(each.getIdType());
            relation.setIdCard(each.getIdCard());
            orderPassengerRelationDOList.add(relation);
        }
        orderItemService.saveBatch(orderItemDOList);
        orderPassengerRelationService.saveBatch(orderPassengerRelationDOList);

        DelayCloseOrderEvent event = DelayCloseOrderEvent.builder()
                .orderSn(orderSn)
                .trainId(String.valueOf(requestParam.getTrainId()))
                .departure(requestParam.getDeparture())
                .arrival(requestParam.getArrival())
                .ticketOrderItems(requestParam.getTicketOrderItems())
                .build();
        SendResult sendResult = delayCloseOrderSendProduce.sendMessage(event);
        if (!Objects.equals(sendResult.getSendStatus(), SendStatus.SEND_OK)) {
            throw new ServiceException("投递延迟关闭订单消息队列失败");
        }
        return orderSn;
    }

    @Override
    public boolean closeTickOrder(CancelTicketOrderReqDTO requestParam) {
        OrderDO orderDO = orderMapper.selectOne(Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, requestParam.getOrderSn())
                .select(OrderDO::getStatus));
        if (orderDO == null || orderDO.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getStatus()) {
            return false;
        }
        cancelTickOrder(requestParam);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void cancelTickOrder(CancelTicketOrderReqDTO requestParam) {
        String orderSn = requestParam.getOrderSn();
        OrderDO orderDO = orderMapper.selectOne(Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, orderSn));
        if (orderDO == null) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_UNKNOWN_ERROR);
        }
        if (orderDO.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getStatus()) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_STATUS_ERROR);
        }
        RLock lock = redissonClient.getLock(LOCK_CANCEL_PREFIX + orderSn);
        if (!lock.tryLock()) {
            throw new ClientException(OrderCanalErrorCodeEnum.ORDER_CANAL_REPETITION_ERROR);
        }
        try {
            int updated = orderMapper.update(null, Wrappers.lambdaUpdate(OrderDO.class)
                    .eq(OrderDO::getOrderSn, orderSn)
                    .set(OrderDO::getStatus, OrderStatusEnum.CLOSED.getStatus()));
            if (updated <= 0) {
                throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_ERROR);
            }
            orderItemService.updateStatusByOrderSn(orderSn, OrderItemStatusEnum.CLOSED.getStatus());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void statusReversal(OrderStatusReversalDTO requestParam) {
        String orderSn = requestParam.getOrderSn();
        OrderDO orderDO = orderMapper.selectOne(Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, orderSn));
        if (orderDO == null) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_UNKNOWN_ERROR);
        }
        // 状态机校验：仅待支付订单可流转
        if (orderDO.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getStatus()) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_STATUS_REVERSAL_ERROR);
        }
        RLock lock = redissonClient.getLock(LOCK_STATUS_REVERSAL_PREFIX + orderSn);
        if (!lock.tryLock()) {
            throw new ClientException(OrderCanalErrorCodeEnum.ORDER_CANAL_REPETITION_ERROR);
        }
        try {
            int updated = orderMapper.update(null, Wrappers.lambdaUpdate(OrderDO.class)
                    .eq(OrderDO::getOrderSn, orderSn)
                    .set(OrderDO::getStatus, requestParam.getOrderStatus()));
            if (updated <= 0) {
                throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_STATUS_REVERSAL_ERROR);
            }
            orderItemService.updateStatusByOrderSn(orderSn, requestParam.getOrderItemStatus());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void payCallbackOrder(PayResultCallbackOrderEvent requestParam) {
        int updated = orderMapper.update(null, Wrappers.lambdaUpdate(OrderDO.class)
                .eq(OrderDO::getOrderSn, requestParam.getOrderSn())
                .set(OrderDO::getPayTime, requestParam.getGmtPayment())
                .set(OrderDO::getPayType, parsePayType(requestParam.getChannel())));
        if (updated <= 0) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_STATUS_REVERSAL_ERROR);
        }
    }

    @Override
    public List<TicketOrderPassengerDetailRespDTO> queryItemByOrderSnAndIds(TicketOrderItemQueryReqDTO requestParam) {
        List<OrderItemDO> items = orderItemService.listByOrderSnAndIds(
                requestParam.getOrderSn(), requestParam.getOrderItemRecordIds());
        return BeanUtil.copyToList(items, TicketOrderPassengerDetailRespDTO.class);
    }

    private List<Integer> buildOrderStatusList(Integer statusType) {
        if (statusType == null || statusType == 0) {
            return List.of(OrderStatusEnum.PENDING_PAYMENT.getStatus());
        }
        if (statusType == 1) {
            return List.of(OrderStatusEnum.ALREADY_PAID.getStatus(),
                    OrderStatusEnum.PARTIAL_REFUND.getStatus(),
                    OrderStatusEnum.FULL_REFUND.getStatus());
        }
        return List.of(OrderStatusEnum.COMPLETED.getStatus());
    }

    private Integer parsePayType(String channel) {
        // 0支付宝 1微信，缺省 0
        return "wechat".equalsIgnoreCase(channel) ? 1 : 0;
    }
}