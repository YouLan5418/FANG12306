package org.opengoofy.index12306.ticket.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.ticket.dao.entity.SeatDO;
import org.opengoofy.index12306.ticket.dao.mapper.SeatMapper;
import org.opengoofy.index12306.ticket.service.SeatService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 座位服务实现。座位按 (train_id, carriage_number, seat_number) 定位，按区间 start/end_station 过滤。
 */
@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatMapper seatMapper;

    @Override
    public int countAvailable(Long trainId, Integer seatType, String departure, String arrival) {
        Long count = seatMapper.selectCount(Wrappers.lambdaQuery(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getSeatType, seatType)
                .eq(SeatDO::getStartStation, departure)
                .eq(SeatDO::getEndStation, arrival)
                .eq(SeatDO::getSeatStatus, 0));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public List<SeatDO> selectAvailable(Long trainId, Integer seatType, String departure, String arrival, int count) {
        return seatMapper.selectList(Wrappers.lambdaQuery(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getSeatType, seatType)
                .eq(SeatDO::getStartStation, departure)
                .eq(SeatDO::getEndStation, arrival)
                .eq(SeatDO::getSeatStatus, 0)
                .orderByAsc(SeatDO::getCarriageNumber)
                .orderByAsc(SeatDO::getSeatNumber)
                .last("LIMIT " + count));
    }

    @Override
    public int lockSeatByPosition(Long trainId, String carriageNumber, String seatNumber) {
        return seatMapper.update(null, Wrappers.lambdaUpdate(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getCarriageNumber, carriageNumber)
                .eq(SeatDO::getSeatNumber, seatNumber)
                .eq(SeatDO::getSeatStatus, 0)
                .set(SeatDO::getSeatStatus, 1));
    }

    @Override
    public int unlockSeats(List<Long> trainIds, List<String> carriageNumbers, List<String> seatNumbers) {
        int updated = 0;
        for (int i = 0; i < trainIds.size(); i++) {
            updated += seatMapper.update(null, Wrappers.lambdaUpdate(SeatDO.class)
                    .eq(SeatDO::getTrainId, trainIds.get(i))
                    .eq(SeatDO::getCarriageNumber, carriageNumbers.get(i))
                    .eq(SeatDO::getSeatNumber, seatNumbers.get(i))
                    .eq(SeatDO::getSeatStatus, 1)
                    .set(SeatDO::getSeatStatus, 0));
        }
        return updated;
    }
}