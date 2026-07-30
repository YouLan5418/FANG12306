package org.opengoofy.index12306.pay.controller;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.opengoofy.index12306.pay.dto.req.RefundReqDTO;
import org.opengoofy.index12306.pay.dto.resp.RefundRespDTO;
import org.opengoofy.index12306.pay.service.RefundService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退款控制层（内部）。
 */
@RestController
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping("/api/pay-service/common/refund")
    public Result<RefundRespDTO> commonRefund(@RequestBody RefundReqDTO requestParam) {
        return Results.success(refundService.commonRefund(requestParam));
    }
}