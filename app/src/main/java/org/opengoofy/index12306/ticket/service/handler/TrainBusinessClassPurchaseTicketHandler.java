package org.opengoofy.index12306.ticket.service.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 商务座选座策略（seatType=0）。
 */
@Component
public class TrainBusinessClassPurchaseTicketHandler extends AbstractTrainPurchaseTicketTemplate {

    public TrainBusinessClassPurchaseTicketHandler(
            org.opengoofy.index12306.ticket.service.SeatService seatService,
            org.opengoofy.index12306.ticket.dao.mapper.TrainStationPriceMapper trainStationPriceMapper) {
        super(seatService, trainStationPriceMapper);
    }

    @Override
    public Integer seatType() {
        return 0;
    }
}