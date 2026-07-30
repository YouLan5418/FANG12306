package org.opengoofy.index12306.pay.controller;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.pay.dto.req.PayCallbackReqDTO;
import org.opengoofy.index12306.pay.service.PayService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付回调控制层（公开，模拟支付宝异步回调）。
 */
@RestController
@RequiredArgsConstructor
public class PayCallbackController {

    private final PayService payService;

    @PostMapping("/api/pay-service/callback/alipay")
    public String callbackPay(@RequestBody PayCallbackReqDTO requestParam) {
        payService.callbackPay(requestParam);
        return "success";
    }
}