package org.opengoofy.index12306.pay.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.framework.convention.exception.ServiceException;
import org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil;
import org.opengoofy.index12306.order.dao.entity.OrderItemDO;
import org.opengoofy.index12306.order.dto.resp.TicketOrderDetailRespDTO;
import org.opengoofy.index12306.order.mq.event.RefundResultCallbackOrderEvent;
import org.opengoofy.index12306.order.service.OrderItemService;
import org.opengoofy.index12306.order.service.OrderService;
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

import java.util.List;

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

        // 创建退款明细记录
        List<RefundDetailReqDTO> details = requestParam.getRefundDetailReqDTOList();
        if (details == null || details.isEmpty()) {
            // 全部退款：按订单明细生成退款记录
            for (OrderItemDO item : orderItems) {
                insertRefund(payDO, orderDetail, item);
            }
        } else {
            for (RefundDetailReqDTO d : details) {
                RefundDO refund = buildRefund(payDO, orderDetail, d.getSeatType(), d.getIdType(), d.getIdCard(), d.getRealName());
                refundMapper.insert(refund);
            }
        }

        // 调渠道退款
        PaymentChannel.RefundResult refundResult = paymentChannel.refund(
                payDO.getPaySn(), requestParam.getOrderSn(), requestParam.getRefundAmount());

        // 更新 t_pay
        int updatedPay = payMapper.update(null, Wrappers.lambdaUpdate(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn())
                .set(PayDO::getStatus, refundResult.status())
                .set(PayDO::getPayAmount, payDO.getTotalAmount() - requestParam.getRefundAmount()));
        if (updatedPay <= 0) {
            throw new ServiceException("修改支付单退款结果失败");
        }
        // 更新 t_refund
        refundMapper.update(null, Wrappers.lambdaUpdate(RefundDO.class)
                .eq(RefundDO::getOrderSn, requestParam.getOrderSn())
                .set(RefundDO::getTradeNo, refundResult.tradeNo())
                .set(RefundDO::getStatus, 0));

        // 发退款结果 MQ（订单状态流转 + 票务座位/余票回滚）
        RefundResultCallbackOrderEvent event = RefundResultCallbackOrderEvent.builder()
                .orderSn(requestParam.getOrderSn())
                .refundType(requestParam.getRefundType())
                .partialRefundDetails(details)
                .build();
        refundResultCallbackOrderSendProduce.sendMessage(event);

        RefundRespDTO resp = new RefundRespDTO();
        resp.setOrderSn(requestParam.getOrderSn());
        resp.setRefundAmount(requestParam.getRefundAmount());
        resp.setStatus(refundResult.status());
        return resp;
    }

    private void insertRefund(PayDO payDO, TicketOrderDetailRespDTO orderDetail, OrderItemDO item) {
        RefundDO refund = new RefundDO();
        refund.setId(SnowflakeIdUtil.nextId());
        refund.setPaySn(payDO.getPaySn());
        refund.setOrderSn(payDO.getOrderSn());
        refund.setAmount(item.getAmount());
        refund.setTrainId(item.getTrainId());
        refund.setTrainNumber(orderDetail.getTrainNumber());
        refund.setDeparture(orderDetail.getDeparture());
        refund.setArrival(orderDetail.getArrival());
        refund.setSeatType(item.getSeatType());
        refund.setIdType(item.getIdType());
        refund.setIdCard(item.getIdCard());
        refund.setRealName(item.getRealName());
        refund.setStatus(0);
        refundMapper.insert(refund);
    }

    private RefundDO buildRefund(PayDO payDO, TicketOrderDetailRespDTO orderDetail,
                                 Integer seatType, Integer idType, String idCard, String realName) {
        RefundDO refund = new RefundDO();
        refund.setId(SnowflakeIdUtil.nextId());
        refund.setPaySn(payDO.getPaySn());
        refund.setOrderSn(payDO.getOrderSn());
        refund.setTrainNumber(orderDetail.getTrainNumber());
        refund.setDeparture(orderDetail.getDeparture());
        refund.setArrival(orderDetail.getArrival());
        refund.setSeatType(seatType);
        refund.setIdType(idType);
        refund.setIdCard(idCard);
        refund.setRealName(realName);
        refund.setStatus(0);
        return refund;
    }
}