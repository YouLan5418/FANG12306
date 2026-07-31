package org.opengoofy.index12306.operations.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.operations.service.OperationRecoveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OperationRecoveryJob {

    private final OperationRecoveryService operationRecoveryService;

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void closeTimeoutOrders() {
        var result = operationRecoveryService.closeTimeoutOrders(30);
        if (result.getHandledCount() > 0) {
            log.info("超时订单兜底关单完成: {}", result);
        }
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 120_000L)
    public void compensateAbnormalOrders() {
        var result = operationRecoveryService.compensateAbnormalOrders();
        if (result.getHandledCount() > 0) {
            log.info("异常订单补偿完成: {}", result);
        }
    }
}
