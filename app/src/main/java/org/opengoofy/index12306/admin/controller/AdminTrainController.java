package org.opengoofy.index12306.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.admin.dto.req.TrainPublishReqDTO;
import org.opengoofy.index12306.framework.convention.exception.ClientException;
import org.opengoofy.index12306.framework.convention.page.PageResponse;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil;
import org.opengoofy.index12306.ticket.dao.entity.*;
import org.opengoofy.index12306.ticket.dao.mapper.*;
import org.opengoofy.index12306.ticket.service.TicketService;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员车次/车票管理控制器。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminTrainController {

    private final TrainMapper trainMapper;
    private final TrainStationMapper trainStationMapper;
    private final TrainStationRelationMapper trainStationRelationMapper;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final CarriageMapper carriageMapper;
    private final SeatMapper seatMapper;
    private final TicketMapper ticketMapper;
    private final TicketService ticketService;

    @GetMapping("/train/list")
    public Result<PageResponse<TrainDO>> listTrain(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        var wrapper = Wrappers.lambdaQuery(TrainDO.class);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(TrainDO::getTrainNumber, keyword)
                    .or().like(TrainDO::getStartStation, keyword)
                    .or().like(TrainDO::getEndStation, keyword));
        }
        wrapper.orderByAsc(TrainDO::getTrainNumber);
        IPage<TrainDO> result = trainMapper.selectPage(Page.of(page, size), wrapper);
        PageResponse<TrainDO> resp = new PageResponse<>(page, size, result.getTotal());
        resp.setRecords(result.getRecords());
        return Results.success(resp);
    }

    @GetMapping("/train/{id}")
    public Result<Map<String, Object>> detailTrain(@PathVariable Long id) {
        TrainDO train = trainMapper.selectById(id);
        if (train == null) return Results.success(null);
        List<TrainStationDO> stations = trainStationMapper.selectList(
                Wrappers.lambdaQuery(TrainStationDO.class)
                        .eq(TrainStationDO::getTrainId, id)
                        .orderByAsc(TrainStationDO::getSequence));
        List<TrainStationRelationDO> relations = trainStationRelationMapper.selectList(
                Wrappers.lambdaQuery(TrainStationRelationDO.class)
                        .eq(TrainStationRelationDO::getTrainId, id));
        List<TrainStationPriceDO> prices = trainStationPriceMapper.selectList(
                Wrappers.lambdaQuery(TrainStationPriceDO.class)
                        .eq(TrainStationPriceDO::getTrainId, id));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("train", train);
        m.put("stations", stations);
        m.put("relations", relations);
        m.put("prices", prices);
        return Results.success(m);
    }

    @PostMapping("/train")
    public Result<Long> createTrain(@RequestBody TrainDO train) {
        trainMapper.insert(train);
        return Results.success(train.getId());
    }

    @PostMapping("/train/publish")
    @Transactional(rollbackFor = Exception.class)
    public Result<Long> publishTrain(@RequestBody TrainPublishReqDTO requestParam) {
        validatePublishRequest(requestParam);
        List<TrainPublishReqDTO.StationSpec> stations = requestParam.getStations();
        TrainPublishReqDTO.StationSpec firstStation = stations.get(0);
        TrainPublishReqDTO.StationSpec lastStation = stations.get(stations.size() - 1);

        TrainDO train = new TrainDO();
        train.setTrainNumber(requestParam.getTrainNumber());
        train.setTrainType(requestParam.getTrainType());
        train.setTrainTag(requestParam.getTrainTag());
        train.setTrainBrand(requestParam.getTrainBrand());
        train.setStartStation(firstStation.getStationName());
        train.setEndStation(lastStation.getStationName());
        train.setStartRegion(firstStation.getRegion());
        train.setEndRegion(lastStation.getRegion());
        train.setSaleTime(requestParam.getSaleTime());
        train.setSaleStatus(Optional.ofNullable(requestParam.getSaleStatus()).orElse(0));
        train.setDepartureTime(firstStation.getDepartureTime());
        train.setArrivalTime(lastStation.getArrivalTime());
        trainMapper.insert(train);

        Long trainId = train.getId();
        for (int i = 0; i < stations.size(); i++) {
            TrainPublishReqDTO.StationSpec station = stations.get(i);
            TrainStationDO stationDO = new TrainStationDO();
            stationDO.setTrainId(trainId);
            stationDO.setStationId(station.getStationId());
            stationDO.setSequence(String.valueOf(i + 1));
            stationDO.setDeparture(station.getStationName());
            stationDO.setArrival(station.getStationName());
            stationDO.setStartRegion(station.getRegion());
            stationDO.setEndRegion(station.getRegion());
            stationDO.setArrivalTime(station.getArrivalTime());
            stationDO.setDepartureTime(station.getDepartureTime());
            stationDO.setStopoverTime(station.getStopoverTime());
            trainStationMapper.insert(stationDO);
        }

        Map<Integer, Integer> priceMap = requestParam.getSeatPrices().stream()
                .collect(Collectors.toMap(TrainPublishReqDTO.SeatPriceSpec::getSeatType,
                        TrainPublishReqDTO.SeatPriceSpec::getPrice, (a, b) -> b));
        for (int i = 0; i < stations.size() - 1; i++) {
            for (int j = i + 1; j < stations.size(); j++) {
                TrainPublishReqDTO.StationSpec departure = stations.get(i);
                TrainPublishReqDTO.StationSpec arrival = stations.get(j);
                TrainStationRelationDO relation = new TrainStationRelationDO();
                relation.setTrainId(trainId);
                relation.setDeparture(departure.getStationName());
                relation.setArrival(arrival.getStationName());
                relation.setStartRegion(departure.getRegion());
                relation.setEndRegion(arrival.getRegion());
                relation.setDepartureFlag(i == 0 ? 1 : 0);
                relation.setArrivalFlag(j == stations.size() - 1 ? 1 : 0);
                relation.setDepartureTime(departure.getDepartureTime());
                relation.setArrivalTime(arrival.getArrivalTime());
                trainStationRelationMapper.insert(relation);
                for (TrainPublishReqDTO.SeatPriceSpec priceSpec : requestParam.getSeatPrices()) {
                    TrainStationPriceDO price = new TrainStationPriceDO();
                    price.setTrainId(trainId);
                    price.setDeparture(departure.getStationName());
                    price.setArrival(arrival.getStationName());
                    price.setSeatType(priceSpec.getSeatType());
                    price.setPrice(priceSpec.getPrice());
                    trainStationPriceMapper.insert(price);
                }
            }
        }

        for (TrainPublishReqDTO.CarriageSpec carriageSpec : requestParam.getCarriages()) {
            CarriageDO carriage = new CarriageDO();
            carriage.setTrainId(trainId);
            carriage.setCarriageNumber(carriageSpec.getCarriageNumber());
            carriage.setCarriageType(carriageSpec.getCarriageType());
            carriage.setSeatCount(carriageSpec.getSeatCount());
            carriageMapper.insert(carriage);
            for (int i = 0; i < stations.size() - 1; i++) {
                for (int j = i + 1; j < stations.size(); j++) {
                    generateSeats(trainId, stations.get(i).getStationName(), stations.get(j).getStationName(), carriageSpec,
                            priceMap.getOrDefault(carriageSpec.getSeatType(), 0));
                }
            }
        }

        for (int i = 0; i < stations.size() - 1; i++) {
            for (int j = i + 1; j < stations.size(); j++) {
                ticketService.refreshRemainingTicketCache(trainId, stations.get(i).getStationName(), stations.get(j).getStationName());
            }
        }
        return Results.success(trainId);
    }

    @PutMapping("/train/{id}")
    public Result<Void> updateTrain(@PathVariable Long id, @RequestBody TrainDO train) {
        train.setId(id);
        trainMapper.updateById(train);
        return Results.success();
    }

    @DeleteMapping("/train/{id}")
    public Result<Void> deleteTrain(@PathVariable Long id) {
        trainMapper.deleteById(id);
        trainStationMapper.delete(Wrappers.lambdaQuery(TrainStationDO.class)
                .eq(TrainStationDO::getTrainId, id));
        trainStationRelationMapper.delete(Wrappers.lambdaQuery(TrainStationRelationDO.class)
                .eq(TrainStationRelationDO::getTrainId, id));
        trainStationPriceMapper.delete(Wrappers.lambdaQuery(TrainStationPriceDO.class)
                .eq(TrainStationPriceDO::getTrainId, id));
        carriageMapper.delete(Wrappers.lambdaQuery(CarriageDO.class)
                .eq(CarriageDO::getTrainId, id));
        seatMapper.delete(Wrappers.lambdaQuery(SeatDO.class)
                .eq(SeatDO::getTrainId, id));
        return Results.success();
    }

    @GetMapping("/ticket/list")
    public Result<PageResponse<TicketDO>> listTicket(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long trainId,
            @RequestParam(required = false) String username) {
        var wrapper = Wrappers.lambdaQuery(TicketDO.class);
        if (trainId != null) {
            wrapper.eq(TicketDO::getTrainId, trainId);
        }
        if (username != null && !username.isBlank()) {
            wrapper.eq(TicketDO::getUsername, username);
        }
        wrapper.orderByDesc(TicketDO::getCreateTime);
        IPage<TicketDO> result = ticketMapper.selectPage(Page.of(page, size), wrapper);
        PageResponse<TicketDO> resp = new PageResponse<>(page, size, result.getTotal());
        resp.setRecords(result.getRecords());
        return Results.success(resp);
    }

    private void validatePublishRequest(TrainPublishReqDTO requestParam) {
        if (requestParam == null || requestParam.getTrainNumber() == null || requestParam.getTrainNumber().isBlank()) {
            throw new ClientException("车次编号不能为空");
        }
        if (requestParam.getStations() == null || requestParam.getStations().size() < 2) {
            throw new ClientException("至少需要两个经停站");
        }
        if (requestParam.getSeatPrices() == null || requestParam.getSeatPrices().isEmpty()) {
            throw new ClientException("座席价格不能为空");
        }
        if (requestParam.getCarriages() == null || requestParam.getCarriages().isEmpty()) {
            throw new ClientException("车厢配置不能为空");
        }
        Map<String, Long> stationNameCount = requestParam.getStations().stream()
                .map(TrainPublishReqDTO.StationSpec::getStationName)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        if (stationNameCount.size() != requestParam.getStations().size()) {
            throw new ClientException("经停站名称不能为空且不能重复");
        }
        for (int i = 0; i < requestParam.getStations().size(); i++) {
            TrainPublishReqDTO.StationSpec station = requestParam.getStations().get(i);
            if (station.getArrivalTime() == null || station.getDepartureTime() == null) {
                throw new ClientException("经停站到达/出发时间不能为空");
            }
            if (station.getDepartureTime().before(station.getArrivalTime())) {
                throw new ClientException("经停站出发时间不能早于到达时间");
            }
            if (i > 0) {
                TrainPublishReqDTO.StationSpec previous = requestParam.getStations().get(i - 1);
                if (!station.getArrivalTime().after(previous.getDepartureTime())) {
                    throw new ClientException("经停站时间顺序不合法");
                }
            }
        }
    }

    private void generateSeats(Long trainId, String startStation, String endStation,
                               TrainPublishReqDTO.CarriageSpec carriageSpec, Integer price) {
        for (int i = 1; i <= carriageSpec.getSeatCount(); i++) {
            SeatDO seat = new SeatDO();
            seat.setId(SnowflakeIdUtil.nextId());
            seat.setTrainId(trainId);
            seat.setCarriageNumber(carriageSpec.getCarriageNumber());
            seat.setSeatNumber(String.format("%03d", i));
            seat.setSeatType(carriageSpec.getSeatType());
            seat.setStartStation(startStation);
            seat.setEndStation(endStation);
            seat.setPrice(price);
            seat.setSeatStatus(0);
            seatMapper.insert(seat);
        }
    }
}
