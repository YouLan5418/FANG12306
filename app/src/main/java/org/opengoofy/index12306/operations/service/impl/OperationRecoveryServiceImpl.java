package org.opengoofy.index12306.operations.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.operations.dto.resp.OperationTaskRespDTO;
import org.opengoofy.index12306.operations.service.OperationRecoveryService;
import org.opengoofy.index12306.order.common.enums.OrderStatusEnum;
import org.opengoofy.index12306.order.dao.entity.OrderDO;
import org.opengoofy.index12306.order.dao.mapper.OrderMapper;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationRelationDO;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationRelationMapper;
import org.opengoofy.index12306.ticket.service.TicketService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperationRecoveryServiceImpl implements OperationRecoveryService {

    private final OrderMapper orderMapper;
    private final TrainStationRelationMapper trainStationRelationMapper;
    private final TicketService ticketService;

    @Override
    public OperationTaskRespDTO closeTimeoutOrders(int timeoutMinutes) {
        long deadline = System.currentTimeMillis() - Math.max(timeoutMinutes, 1) * 60_000L;
        List<OrderDO> timeoutOrders = orderMapper.selectList(Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getStatus, OrderStatusEnum.PENDING_PAYMENT.getStatus())
                .lt(OrderDO::getOrderTime, new Date(deadline)));
        int handled = 0;
        for (OrderDO order : timeoutOrders) {
            ticketService.closeOrderAndRollback(order.getOrderSn());
            handled++;
        }
        OperationTaskRespDTO result = OperationTaskRespDTO.of("closeTimeoutOrders", timeoutOrders.size(), handled);
        result.getDetails().put("timeoutMinutes", timeoutMinutes);
        return result;
    }

    @Override
    public OperationTaskRespDTO calibrateRemainingTickets() {
        List<TrainStationRelationDO> relations = trainStationRelationMapper.selectList(null);
        int handled = 0;
        Map<String, Object> details = new LinkedHashMap<>();
        for (TrainStationRelationDO relation : relations) {
            ticketService.refreshRemainingTicketCache(relation.getTrainId(), relation.getDeparture(), relation.getArrival());
            handled++;
        }
        details.put("relationCount", relations.size());
        return OperationTaskRespDTO.builder()
                .taskName("calibrateRemainingTickets")
                .scannedCount(relations.size())
                .handledCount(handled)
                .details(details)
                .build();
    }

    @Override
    public OperationTaskRespDTO compensateAbnormalOrders() {
        List<OrderDO> closedOrders = orderMapper.selectList(Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getStatus, OrderStatusEnum.CLOSED.getStatus()));
        int handled = 0;
        for (OrderDO order : closedOrders) {
            ticketService.rollbackOnOrderClose(order.getOrderSn());
            handled++;
        }
        return OperationTaskRespDTO.of("compensateAbnormalOrders", closedOrders.size(), handled);
    }
}
