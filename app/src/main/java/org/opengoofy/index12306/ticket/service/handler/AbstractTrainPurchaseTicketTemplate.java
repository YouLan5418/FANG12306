package org.opengoofy.index12306.ticket.service.handler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.convention.exception.ServiceException;
import org.opengoofy.index12306.ticket.dao.entity.SeatDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationPriceDO;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationPriceMapper;
import org.opengoofy.index12306.ticket.dto.req.PurchaseTicketReqDTO;
import org.opengoofy.index12306.ticket.dto.resp.TrainPurchaseTicketRespDTO;
import org.opengoofy.index12306.ticket.service.SeatService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 选座策略基类：按 seatType 选取可用座位并组装选座结果（含票价）。
 * 子类只声明 seatType，体现策略模式。
 */
@Component
@RequiredArgsConstructor
public abstract class AbstractTrainPurchaseTicketTemplate implements IPurchaseTicket {

    protected final SeatService seatService;
    protected final TrainStationPriceMapper trainStationPriceMapper;

    @Override
    public List<TrainPurchaseTicketRespDTO> selectSeats(PurchaseTicketReqDTO requestParam, int count) {
        Long trainId = Long.parseLong(requestParam.getTrainId());
        List<SeatDO> seats;
        if (requestParam.getChooseSeats() != null && !requestParam.getChooseSeats().isEmpty()) {
            List<String> seatNumbers = requestParam.getChooseSeats().stream()
                    .map(this::extractSeatNumber)
                    .filter(each -> each != null && !each.isBlank())
                    .collect(Collectors.toList());
            seats = seatService.selectAvailableBySeatNumbers(trainId, seatType(),
                    requestParam.getDeparture(), requestParam.getArrival(), seatNumbers);
            if (seats.size() > count) {
                seats = seats.subList(0, count);
            }
        } else {
            seats = seatService.selectAvailable(trainId, seatType(),
                    requestParam.getDeparture(), requestParam.getArrival(), count);
        }
        if (seats.size() < count) {
            throw new ServiceException("余票不足， seatType=" + seatType() + " 需求=" + count + " 可用=" + seats.size());
        }
        Integer price = getPrice(trainId, requestParam.getDeparture(), requestParam.getArrival(), seatType());
        List<TrainPurchaseTicketRespDTO> results = new ArrayList<>();
        for (SeatDO seat : seats) {
            TrainPurchaseTicketRespDTO resp = new TrainPurchaseTicketRespDTO();
            resp.setCarriageNumber(seat.getCarriageNumber());
            resp.setSeatNumber(seat.getSeatNumber());
            resp.setSeatType(seatType());
            resp.setAmount(price);
            resp.setTicketType(0);
            results.add(resp);
        }
        return results;
    }

    protected Integer getPrice(Long trainId, String departure, String arrival, Integer seatType) {
        TrainStationPriceDO price = trainStationPriceMapper.selectOne(Wrappers.lambdaQuery(TrainStationPriceDO.class)
                .eq(TrainStationPriceDO::getTrainId, trainId)
                .eq(TrainStationPriceDO::getDeparture, departure)
                .eq(TrainStationPriceDO::getArrival, arrival)
                .eq(TrainStationPriceDO::getSeatType, seatType));
        return price == null ? 0 : price.getPrice();
    }

    private String extractSeatNumber(String chooseSeat) {
        if (chooseSeat == null) {
            return null;
        }
        String trimmed = chooseSeat.trim();
        int delimiter = Math.max(trimmed.lastIndexOf(':'), trimmed.lastIndexOf('-'));
        return delimiter >= 0 ? trimmed.substring(delimiter + 1) : trimmed;
    }
}
