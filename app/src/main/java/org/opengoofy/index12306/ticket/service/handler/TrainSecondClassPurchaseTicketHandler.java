package org.opengoofy.index12306.ticket.service.handler;

import org.springframework.stereotype.Component;

/**
 * 二等座选座策略（seatType=2）。
 */
@Component
public class TrainSecondClassPurchaseTicketHandler extends AbstractTrainPurchaseTicketTemplate {

    public TrainSecondClassPurchaseTicketHandler(
            org.opengoofy.index12306.ticket.service.SeatService seatService,
            org.opengoofy.index12306.ticket.dao.mapper.TrainStationPriceMapper trainStationPriceMapper) {
        super(seatService, trainStationPriceMapper);
    }

    @Override
    public Integer seatType() {
        return 2;
    }
}