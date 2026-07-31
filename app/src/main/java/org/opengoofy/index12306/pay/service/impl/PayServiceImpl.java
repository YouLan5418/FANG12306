package org.opengoofy.index12306.pay.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.framework.convention.exception.ServiceException;
import org.opengoofy.index12306.framework.idempotent.annotation.Idempotent;
import org.opengoofy.index12306.framework.idempotent.enums.IdempotentTypeEnum;
import org.opengoofy.index12306.order.mq.event.PayResultCallbackOrderEvent;
import org.opengoofy.index12306.pay.common.constant.PayCacheConstant;
import org.opengoofy.index12306.pay.common.enums.TradeStatusEnum;
import org.opengoofy.index12306.pay.dao.entity.PayDO;
import org.opengoofy.index12306.pay.dao.mapper.PayMapper;
import org.opengoofy.index12306.pay.dto.req.PayCallbackReqDTO;
import org.opengoofy.index12306.pay.dto.req.PayCreateReqDTO;
import org.opengoofy.index12306.pay.dto.resp.PayInfoRespDTO;
import org.opengoofy.index12306.pay.dto.resp.PayRespDTO;
import org.opengoofy.index12306.pay.mq.produce.PayResultCallbackOrderSendProduce;
import org.opengoofy.index12306.pay.service.PayService;
import org.opengoofy.index12306.pay.service.PaymentChannel;
import org.opengoofy.index12306.pay.service.payid.PayIdGeneratorManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 支付服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    private final PayMapper payMapper;
    private final PaymentChannel paymentChannel;
    private final PayResultCallbackOrderSendProduce payResultCallbackOrderSendProduce;
    private final StringRedisTemplate stringRedisTemplate;

    @Idempotent(
            uniqueKeyPrefix = "index12306-pay:lock_create_pay:",
            key = "#requestParam.orderSn",
            type = IdempotentTypeEnum.SPEL,
            message = "正在创建支付单，请稍后..."
    )
    @Transactional(rollbackFor = Exception.class)
    @Override
    public PayRespDTO createPay(PayCreateReqDTO requestParam) {
        String cacheKey = PayCacheConstant.PAY_CREATE_RESULT + requestParam.getOrderSn();
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseObject(cached, PayRespDTO.class);
        }
        String paySn = PayIdGeneratorManager.generatePaySn(requestParam.getOrderSn());
        String channel = normalizeChannel(requestParam.getChannel());
        PaymentChannel.PayCreateResult payResult = paymentChannel.createPay(
                paySn, requestParam.getOrderSn(), requestParam.getTotalAmount(), channel);

        PayDO payDO = new PayDO();
        payDO.setPaySn(paySn);
        payDO.setOrderSn(requestParam.getOrderSn());
        payDO.setChannel(channel);
        payDO.setTradeType("PAGE");
        payDO.setTotalAmount(requestParam.getTotalAmount());
        payDO.setStatus(TradeStatusEnum.WAIT_BUYER_PAY.getTradeCode());
        int inserted = payMapper.insert(payDO);
        if (inserted <= 0) {
            throw new ServiceException("支付单创建失败");
        }

        PayRespDTO resp = new PayRespDTO();
        resp.setPaySn(paySn);
        resp.setPayUrl(payResult.payUrl());
        resp.setOrderSn(requestParam.getOrderSn());
        resp.setTotalAmount(requestParam.getTotalAmount());
        resp.setStatus(TradeStatusEnum.WAIT_BUYER_PAY.getTradeCode());
        stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(resp), 30, TimeUnit.MINUTES);
        return resp;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void callbackPay(PayCallbackReqDTO requestParam) {
        PayDO payDO = payMapper.selectOne(Wrappers.lambdaQuery(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn()));
        if (payDO == null) {
            throw new ServiceException("支付单不存在");
        }
        if (Objects.equals(payDO.getStatus(), TradeStatusEnum.TRADE_SUCCESS.getTradeCode())) {
            log.info("支付回调重复通知，orderSn={} 已处理", requestParam.getOrderSn());
            return;
        }
        if (!Objects.equals(requestParam.getStatus(), TradeStatusEnum.TRADE_SUCCESS.getTradeCode())) {
            throw new ServiceException("不支持的支付回调状态: " + requestParam.getStatus());
        }
        int updated = payMapper.update(null, Wrappers.lambdaUpdate(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn())
                .set(PayDO::getTradeNo, requestParam.getTradeNo())
                .set(PayDO::getStatus, requestParam.getStatus())
                .set(PayDO::getPayAmount, requestParam.getPayAmount())
                .set(PayDO::getGmtPayment, requestParam.getGmtPayment()));
        if (updated <= 0) {
            throw new ServiceException("修改支付单支付结果失败");
        }
        PayResultCallbackOrderEvent event = PayResultCallbackOrderEvent.builder()
                .orderSn(payDO.getOrderSn())
                .paySn(payDO.getPaySn())
                .channel(payDO.getChannel())
                .gmtPayment(requestParam.getGmtPayment())
                .payAmount(requestParam.getPayAmount())
                .status(requestParam.getStatus())
                .build();
        payResultCallbackOrderSendProduce.sendMessage(event);
    }

    @Override
    public PayInfoRespDTO getPayInfoByOrderSn(String orderSn) {
        PayDO payDO = payMapper.selectOne(Wrappers.lambdaQuery(PayDO.class).eq(PayDO::getOrderSn, orderSn));
        return BeanUtil.copyProperties(payDO, PayInfoRespDTO.class);
    }

    @Override
    public PayInfoRespDTO getPayInfoByPaySn(String paySn) {
        PayDO payDO = payMapper.selectOne(Wrappers.lambdaQuery(PayDO.class).eq(PayDO::getPaySn, paySn));
        return BeanUtil.copyProperties(payDO, PayInfoRespDTO.class);
    }

    private String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return "ALI_PAY";
        }
        String normalized = channel.trim().toUpperCase();
        if (!Objects.equals(normalized, "ALI_PAY") && !Objects.equals(normalized, "WECHAT_PAY")) {
            throw new ServiceException("不支持的支付渠道: " + channel);
        }
        return normalized;
    }
}
