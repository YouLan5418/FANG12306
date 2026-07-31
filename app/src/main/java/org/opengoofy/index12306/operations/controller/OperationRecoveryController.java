package org.opengoofy.index12306.operations.controller;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.opengoofy.index12306.operations.dto.resp.OperationTaskRespDTO;
import org.opengoofy.index12306.operations.service.OperationRecoveryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OperationRecoveryController {

    private final OperationRecoveryService operationRecoveryService;

    @PostMapping("/api/admin/ops/timeout-orders/close")
    public Result<OperationTaskRespDTO> closeTimeoutOrders(@RequestParam(defaultValue = "30") int timeoutMinutes) {
        return Results.success(operationRecoveryService.closeTimeoutOrders(timeoutMinutes));
    }

    @PostMapping("/api/admin/ops/remaining-ticket/calibrate")
    public Result<OperationTaskRespDTO> calibrateRemainingTickets() {
        return Results.success(operationRecoveryService.calibrateRemainingTickets());
    }

    @PostMapping("/api/admin/ops/order/compensate")
    public Result<OperationTaskRespDTO> compensateAbnormalOrders() {
        return Results.success(operationRecoveryService.compensateAbnormalOrders());
    }
}
