package org.opengoofy.index12306.operations.service;

import org.opengoofy.index12306.operations.dto.resp.OperationTaskRespDTO;

public interface OperationRecoveryService {

    OperationTaskRespDTO closeTimeoutOrders(int timeoutMinutes);

    OperationTaskRespDTO calibrateRemainingTickets();

    OperationTaskRespDTO compensateAbnormalOrders();
}
