package org.opengoofy.index12306.pay.service;

import org.opengoofy.index12306.pay.dto.req.PayCallbackReqDTO;
import org.opengoofy.index12306.pay.dto.req.PayCreateReqDTO;
import org.opengoofy.index12306.pay.dto.resp.PayInfoRespDTO;
import org.opengoofy.index12306.pay.dto.resp.PayRespDTO;

/**
 * 支付服务。
 */
public interface PayService {

    PayRespDTO createPay(PayCreateReqDTO requestParam);

    void callbackPay(PayCallbackReqDTO requestParam);

    PayInfoRespDTO getPayInfoByOrderSn(String orderSn);

    PayInfoRespDTO getPayInfoByPaySn(String paySn);
}