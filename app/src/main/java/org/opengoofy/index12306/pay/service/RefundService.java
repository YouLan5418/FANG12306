package org.opengoofy.index12306.pay.service;

import org.opengoofy.index12306.pay.dto.req.RefundReqDTO;
import org.opengoofy.index12306.pay.dto.resp.RefundRespDTO;

/**
 * 退款服务。
 */
public interface RefundService {

    RefundRespDTO commonRefund(RefundReqDTO requestParam);
}