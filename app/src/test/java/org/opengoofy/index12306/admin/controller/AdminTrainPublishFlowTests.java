package org.opengoofy.index12306.admin.controller;

import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.admin.dto.req.TrainPublishReqDTO;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.operations.service.OperationRecoveryService;
import org.opengoofy.index12306.ticket.common.constant.TicketCacheConstant;
import org.opengoofy.index12306.ticket.dao.entity.SeatDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationPriceDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationRelationDO;
import org.opengoofy.index12306.ticket.dao.mapper.SeatMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationPriceMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationRelationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class AdminTrainPublishFlowTests {

    @Autowired private AdminTrainController adminTrainController;
    @Autowired private OperationRecoveryService operationRecoveryService;
    @Autowired private TrainStationRelationMapper relationMapper;
    @Autowired private TrainStationPriceMapper priceMapper;
    @Autowired private SeatMapper seatMapper;
    @Autowired private StringRedisTemplate stringRedisTemplate;

    @Test
    void testPublishTrainCreatesRelationsSeatsAndRemainingCache() {
        Result<Long> result = adminTrainController.publishTrain(buildPublishReq());
        Long trainId = result.getData();
        assertNotNull(trainId);

        Long relationCount = relationMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(TrainStationRelationDO.class)
                .eq(TrainStationRelationDO::getTrainId, trainId));
        assertEquals(3, relationCount);

        Long priceCount = priceMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(TrainStationPriceDO.class)
                .eq(TrainStationPriceDO::getTrainId, trainId));
        assertEquals(3, priceCount);

        Long seatCount = seatMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId));
        assertEquals(6, seatCount);

        String key = TicketCacheConstant.TRAIN_STATION_REMAINING_TICKET + trainId + "_A_C";
        assertEquals("2", stringRedisTemplate.opsForHash().get(key, "2"));

        stringRedisTemplate.opsForHash().put(key, "2", "0");
        operationRecoveryService.calibrateRemainingTickets();
        assertEquals("2", stringRedisTemplate.opsForHash().get(key, "2"));
    }

    private TrainPublishReqDTO buildPublishReq() {
        TrainPublishReqDTO req = new TrainPublishReqDTO();
        req.setTrainNumber("G-PUB-" + System.currentTimeMillis());
        req.setTrainType(0);
        req.setSaleTime(new Date(System.currentTimeMillis() - 60_000L));
        req.setSaleStatus(0);

        TrainPublishReqDTO.StationSpec a = station("A", 0, 10);
        TrainPublishReqDTO.StationSpec b = station("B", 60, 70);
        TrainPublishReqDTO.StationSpec c = station("C", 120, 120);
        req.setStations(List.of(a, b, c));

        TrainPublishReqDTO.SeatPriceSpec price = new TrainPublishReqDTO.SeatPriceSpec();
        price.setSeatType(2);
        price.setPrice(55300);
        req.setSeatPrices(List.of(price));

        TrainPublishReqDTO.CarriageSpec carriage = new TrainPublishReqDTO.CarriageSpec();
        carriage.setCarriageNumber("01");
        carriage.setCarriageType(2);
        carriage.setSeatType(2);
        carriage.setSeatCount(2);
        req.setCarriages(List.of(carriage));
        return req;
    }

    private TrainPublishReqDTO.StationSpec station(String name, int arriveMinutes, int departMinutes) {
        TrainPublishReqDTO.StationSpec station = new TrainPublishReqDTO.StationSpec();
        station.setStationName(name);
        station.setRegion("R");
        station.setArrivalTime(new Date(System.currentTimeMillis() + arriveMinutes * 60_000L));
        station.setDepartureTime(new Date(System.currentTimeMillis() + departMinutes * 60_000L));
        station.setStopoverTime(5);
        return station;
    }
}
