package org.opengoofy.index12306.pay.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.framework.convention.exception.ServiceException;
import org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil;
import org.opengoofy.index12306.order.common.enums.OrderItemStatusEnum;
import org.opengoofy.index12306.order.common.enums.OrderStatusEnum;
import org.opengoofy.index12306.order.dao.entity.OrderItemDO;
import org.opengoofy.index12306.order.dto.resp.TicketOrderDetailRespDTO;
import org.opengoofy.index12306.order.mq.event.RefundResultCallbackOrderEvent;
import org.opengoofy.index12306.order.service.OrderItemService;
import org.opengoofy.index12306.order.service.OrderService;
import org.opengoofy.index12306.pay.common.enums.RefundTypeEnum;
import org.opengoofy.index12306.pay.common.enums.TradeStatusEnum;
import org.opengoofy.index12306.pay.dao.entity.PayDO;
import org.opengoofy.index12306.pay.dao.entity.RefundDO;
import org.opengoofy.index12306.pay.dao.mapper.PayMapper;
import org.opengoofy.index12306.pay.dao.mapper.RefundMapper;
import org.opengoofy.index12306.pay.dto.req.RefundDetailReqDTO;
import org.opengoofy.index12306.pay.dto.req.RefundReqDTO;
import org.opengoofy.index12306.pay.dto.resp.RefundRespDTO;
import org.opengoofy.index12306.pay.mq.produce.RefundResultCallbackOrderSendProduce;
import org.opengoofy.index12306.pay.service.PaymentChannel;
import org.opengoofy.index12306.pay.service.RefundService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 退款服务实现：创建退款单 -> 调渠道退款 -> 更新 t_pay/t_refund -> 发退款结果 MQ。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final PayMapper payMapper;
    private final RefundMapper refundMapper;
    private final OrderService orderService;
    private final OrderItemService orderItemService;
    private final PaymentChannel paymentChannel;
    private final RefundResultCallbackOrderSendProduce refundResultCallbackOrderSendProduce;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public RefundRespDTO commonRefund(RefundReqDTO requestParam) {
        PayDO payDO = payMapper.selectOne(Wrappers.lambdaQuery(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn()));
        if (payDO == null) {
            throw new ServiceException("支付单不存在");
        }
        TicketOrderDetailRespDTO orderDetail = orderService.queryTicketOrderByOrderSn(requestParam.getOrderSn());
        if (orderDetail == null) {
            throw new ServiceException("车票订单不存在");
        }
        List<OrderItemDO> orderItems = orderItemService.listByOrderSn(requestParam.getOrderSn());
        int refundType = resolveRefundType(requestParam);
        validateRefundable(payDO, orderDetail, refundType);

        List<RefundDetailReqDTO> details = requestParam.getRefundDetailReqDTOList();
        List<OrderItemDO> refundItems = resolveRefundItems(orderItems, details, refundType);
        int refundAmount = refundItems.stream().mapToInt(OrderItemDO::getAmount).sum();
        if (refundAmount <= 0) {
            throw new ServiceException("无可退款订单明细");
        }
        if (requestParam.getRefundAmount() != null && !Objects.equals(requestParam.getRefundAmount(), refundAmount)) {
            throw new ServiceException("退款金额与订单明细金额不一致");
        }

        List<Long> refundIds = new ArrayList<>();
        for (OrderItemDO item : refundItems) {
            RefundDO refund = buildRefund(payDO, orderDetail, item);
            refundMapper.insert(refund);
            refundIds.add(refund.getId());
        }

        // 调渠道退款
        PaymentChannel.RefundResult refundResult = paymentChannel.refund(
                payDO.getPaySn(), requestParam.getOrderSn(), refundAmount);

        // 更新 t_pay
        int paidAmount = payDO.getPayAmount() == null ? payDO.getTotalAmount() : payDO.getPayAmount();
        int remainingAmount = Math.max(0, paidAmount - refundAmount);
        int updatedPay = payMapper.update(null, Wrappers.lambdaUpdate(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn())
                .set(PayDO::getStatus, refundResult.status())
                .set(PayDO::getPayAmount, remainingAmount));
        if (updatedPay <= 0) {
            throw new ServiceException("修改支付单退款结果失败");
        }
        // 更新 t_refund
        refundMapper.update(null, Wrappers.lambdaUpdate(RefundDO.class)
                .in(RefundDO::getId, refundIds)
                .set(RefundDO::getTradeNo, refundResult.tradeNo())
                .set(RefundDO::getStatus, 0));

        // 发退款结果 MQ（订单状态流转 + 票务座位/余票回滚）
        RefundResultCallbackOrderEvent event = RefundResultCallbackOrderEvent.builder()
                .orderSn(requestParam.getOrderSn())
                .refundType(refundType)
                .partialRefundDetails(toRefundDetails(refundItems))
                .build();
        refundResultCallbackOrderSendProduce.sendMessage(event);

        RefundRespDTO resp = new RefundRespDTO();
        resp.setOrderSn(requestParam.getOrderSn());
        resp.setRefundAmount(refundAmount);
        resp.setStatus(refundResult.status());
        return resp;
    }

    private RefundDO buildRefund(PayDO payDO, TicketOrderDetailRespDTO orderDetail, OrderItemDO item) {
        RefundDO refund = new RefundDO();
        refund.setId(SnowflakeIdUtil.nextId());
        refund.setPaySn(payDO.getPaySn());
        refund.setOrderSn(payDO.getOrderSn());
        refund.setAmount(item.getAmount());
        refund.setUserId(item.getUserId());
        refund.setUsername(item.getUsername());
        refund.setTrainId(item.getTrainId());
        refund.setTrainNumber(orderDetail.getTrainNumber());
        refund.setRidingDate(orderDetail.getRidingDate());
        refund.setDeparture(orderDetail.getDeparture());
        refund.setArrival(orderDetail.getArrival());
        refund.setDepartureTime(orderDetail.getDepartureTime());
        refund.setArrivalTime(orderDetail.getArrivalTime());
        refund.setSeatType(item.getSeatType());
        refund.setIdType(item.getIdType());
        refund.setIdCard(item.getIdCard());
        refund.setRealName(item.getRealName());
        refund.setStatus(0);
        return refund;
    }

    private int resolveRefundType(RefundReqDTO requestParam) {
        if (requestParam.getRefundType() != null) {
            return requestParam.getRefundType();
        }
        List<RefundDetailReqDTO> details = requestParam.getRefundDetailReqDTOList();
        return details == null || details.isEmpty()
                ? RefundTypeEnum.FULL_REFUND.getCode()
                : RefundTypeEnum.PARTIAL_REFUND.getCode();
    }

    private void validateRefundable(PayDO payDO, TicketOrderDetailRespDTO orderDetail, int refundType) {
        if (!Objects.equals(refundType, RefundTypeEnum.PARTIAL_REFUND.getCode())
                && !Objects.equals(refundType, RefundTypeEnum.FULL_REFUND.getCode())) {
            throw new ServiceException("不支持的退款类型: " + refundType);
        }
        Integer orderStatus = orderDetail.getStatus();
        if (!Objects.equals(orderStatus, OrderStatusEnum.ALREADY_PAID.getStatus())
                && !Objects.equals(orderStatus, OrderStatusEnum.PARTIAL_REFUND.getStatus())) {
            throw new ServiceException("当前订单状态不允许退款");
        }
        if (Objects.equals(orderStatus, OrderStatusEnum.ALREADY_PAID.getStatus())
                && !Objects.equals(payDO.getStatus(), TradeStatusEnum.TRADE_SUCCESS.getTradeCode())) {
            throw new ServiceException("当前支付状态不允许退款");
        }
        if (!Objects.equals(payDO.getStatus(), TradeStatusEnum.TRADE_SUCCESS.getTradeCode())
                && !Objects.equals(payDO.getStatus(), TradeStatusEnum.TRADE_CLOSED.getTradeCode())) {
            throw new ServiceException("当前支付状态不允许退款");
        }
    }

    private List<OrderItemDO> resolveRefundItems(List<OrderItemDO> orderItems, List<RefundDetailReqDTO> details,
                                                int refundType) {
        List<OrderItemDO> candidates = orderItems.stream()
                .filter(each -> !Objects.equals(each.getStatus(), OrderItemStatusEnum.REFUNDED.getStatus()))
                .collect(Collectors.toList());
        if (Objects.equals(refundType, RefundTypeEnum.FULL_REFUND.getCode())) {
            return candidates;
        }
        if (details == null || details.isEmpty()) {
            throw new ServiceException("部分退款必须指定退款明细");
        }
        List<OrderItemDO> matched = candidates.stream()
                .filter(item -> details.stream().anyMatch(detail -> sameSeat(item, detail)))
                .collect(Collectors.toList());
        if (matched.size() != details.size()) {
            throw new ServiceException("部分退款明细与订单不匹配或已退款");
        }
        return matched;
    }

    private boolean sameSeat(OrderItemDO item, RefundDetailReqDTO detail) {
        return Objects.equals(item.getCarriageNumber(), detail.getCarriageNumber())
                && Objects.equals(item.getSeatNumber(), detail.getSeatNumber());
    }

    private List<RefundDetailReqDTO> toRefundDetails(List<OrderItemDO> refundItems) {
        return refundItems.stream().map(item -> {
            RefundDetailReqDTO detail = new RefundDetailReqDTO();
            detail.setCarriageNumber(item.getCarriageNumber());
            detail.setSeatNumber(item.getSeatNumber());
            detail.setSeatType(item.getSeatType());
            detail.setRealName(item.getRealName());
            detail.setIdType(item.getIdType());
            detail.setIdCard(item.getIdCard());
            return detail;
        }).collect(Collectors.toList());
    }
}
