package org.opengoofy.index12306.ticket.service.handler;

import org.springframework.stereotype.Component;

/**
 * 一等座选座策略（seatType=1）。
 */
@Component
public class TrainFirstClassPurchaseTicketHandler extends AbstractTrainPurchaseTicketTemplate {

    public TrainFirstClassPurchaseTicketHandler(
            org.opengoofy.index12306.ticket.service.SeatService seatService,
            org.opengoofy.index12306.ticket.dao.mapper.TrainStationPriceMapper trainStationPriceMapper) {
        super(seatService, trainStationPriceMapper);
    }

    @Override
    public Integer seatType() {
        return 1;
    }
}