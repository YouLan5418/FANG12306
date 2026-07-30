package org.opengoofy.index12306.pay.controller;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.opengoofy.index12306.pay.dto.req.PayCreateReqDTO;
import org.opengoofy.index12306.pay.dto.resp.PayInfoRespDTO;
import org.opengoofy.index12306.pay.dto.resp.PayRespDTO;
import org.opengoofy.index12306.pay.service.PayService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付控制层。
 */
@RestController
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    @PostMapping("/api/pay-service/pay/create")
    public Result<PayRespDTO> createPay(@RequestBody PayCreateReqDTO requestParam) {
        return Results.success(payService.createPay(requestParam));
    }

    @GetMapping("/api/pay-service/pay/query/order-sn")
    public Result<PayInfoRespDTO> queryByOrderSn(@RequestParam("orderSn") String orderSn) {
        return Results.success(payService.getPayInfoByOrderSn(orderSn));
    }

    @GetMapping("/api/pay-service/pay/query/pay-sn")
    public Result<PayInfoRespDTO> queryByPaySn(@RequestParam("paySn") String paySn) {
        return Results.success(payService.getPayInfoByPaySn(paySn));
    }
}