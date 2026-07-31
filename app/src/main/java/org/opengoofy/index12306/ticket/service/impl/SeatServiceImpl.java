package org.opengoofy.index12306.ticket.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.ticket.dao.entity.SeatDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationDO;
import org.opengoofy.index12306.ticket.dao.mapper.SeatMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationMapper;
import org.opengoofy.index12306.ticket.service.SeatService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 座位服务实现。座位按 (train_id, carriage_number, seat_number) 定位，按区间 start/end_station 过滤。
 */
@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatMapper seatMapper;
    private final TrainStationMapper trainStationMapper;

    @Override
    public int countAvailable(Long trainId, Integer seatType, String departure, String arrival) {
        return listAvailablePhysicalSeats(trainId, seatType, departure, arrival, null, Integer.MAX_VALUE).size();
    }

    @Override
    public List<SeatDO> selectAvailable(Long trainId, Integer seatType, String departure, String arrival, int count) {
        return listAvailablePhysicalSeats(trainId, seatType, departure, arrival, null, count);
    }

    @Override
    public List<SeatDO> selectAvailableBySeatNumbers(Long trainId, Integer seatType, String departure, String arrival,
                                                     List<String> seatNumbers) {
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            return List.of();
        }
        return listAvailablePhysicalSeats(trainId, seatType, departure, arrival, seatNumbers, Integer.MAX_VALUE);
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
    public int lockSeatByPosition(Long trainId, String carriageNumber, String seatNumber, String departure, String arrival) {
        return updateOverlappedSeatSegments(trainId, carriageNumber, seatNumber, departure, arrival,
                0, wrapper -> wrapper.set(SeatDO::getSeatStatus, 1));
    }

    @Override
    public int sellSeatByPosition(Long trainId, String carriageNumber, String seatNumber) {
        return seatMapper.update(null, Wrappers.lambdaUpdate(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getCarriageNumber, carriageNumber)
                .eq(SeatDO::getSeatNumber, seatNumber)
                .eq(SeatDO::getSeatStatus, 1)
                .set(SeatDO::getSeatStatus, 2));
    }

    @Override
    public int sellSeatByPosition(Long trainId, String carriageNumber, String seatNumber, String departure, String arrival) {
        return updateOverlappedSeatSegments(trainId, carriageNumber, seatNumber, departure, arrival,
                1, wrapper -> wrapper.set(SeatDO::getSeatStatus, 2));
    }

    @Override
    public int releaseSeatByPosition(Long trainId, String carriageNumber, String seatNumber) {
        return seatMapper.update(null, Wrappers.lambdaUpdate(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getCarriageNumber, carriageNumber)
                .eq(SeatDO::getSeatNumber, seatNumber)
                .in(SeatDO::getSeatStatus, 1, 2)
                .set(SeatDO::getSeatStatus, 0));
    }

    @Override
    public int releaseSeatByPosition(Long trainId, String carriageNumber, String seatNumber, String departure, String arrival) {
        List<Long> targetIds = listOverlappedSeatSegmentIds(trainId, carriageNumber, seatNumber, departure, arrival);
        if (targetIds.isEmpty()) {
            return 0;
        }
        return seatMapper.update(null, Wrappers.lambdaUpdate(SeatDO.class)
                .in(SeatDO::getId, targetIds)
                .in(SeatDO::getSeatStatus, 1, 2)
                .set(SeatDO::getSeatStatus, 0));
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

    private int updateOverlappedSeatSegments(Long trainId, String carriageNumber, String seatNumber,
                                             String departure, String arrival, Integer expectedStatus,
                                             Consumer<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SeatDO>> updater) {
        List<Long> targetIds = listOverlappedSeatSegmentIds(trainId, carriageNumber, seatNumber, departure, arrival);
        if (targetIds.isEmpty()) {
            return 0;
        }
        var wrapper = Wrappers.lambdaUpdate(SeatDO.class)
                .in(SeatDO::getId, targetIds)
                .eq(SeatDO::getSeatStatus, expectedStatus);
        updater.accept(wrapper);
        int updated = seatMapper.update(null, wrapper);
        return updated == targetIds.size() ? updated : 0;
    }

    private List<Long> listOverlappedSeatSegmentIds(Long trainId, String carriageNumber, String seatNumber,
                                                    String departure, String arrival) {
        List<SeatDO> seatSegments = seatMapper.selectList(Wrappers.lambdaQuery(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getCarriageNumber, carriageNumber)
                .eq(SeatDO::getSeatNumber, seatNumber));
        if (seatSegments.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> stationOrderMap = buildStationOrderMap(trainId);
        Integer departureOrder = stationOrderMap.get(departure);
        Integer arrivalOrder = stationOrderMap.get(arrival);
        if (departureOrder == null || arrivalOrder == null || departureOrder >= arrivalOrder) {
            return seatSegments.stream()
                    .filter(each -> Objects.equals(each.getStartStation(), departure)
                            && Objects.equals(each.getEndStation(), arrival))
                    .map(SeatDO::getId)
                    .collect(Collectors.toList());
        }
        return seatSegments.stream()
                .filter(each -> isSegmentOverlapped(stationOrderMap, departureOrder, arrivalOrder, each))
                .map(SeatDO::getId)
                .collect(Collectors.toList());
    }

    private List<SeatDO> listAvailablePhysicalSeats(Long trainId, Integer seatType, String departure, String arrival,
                                                    List<String> seatNumbers, int limit) {
        List<SeatDO> seatSegments = seatMapper.selectList(Wrappers.lambdaQuery(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getSeatType, seatType)
                .in(seatNumbers != null && !seatNumbers.isEmpty(), SeatDO::getSeatNumber, seatNumbers)
                .orderByAsc(SeatDO::getCarriageNumber)
                .orderByAsc(SeatDO::getSeatNumber));
        if (seatSegments.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> stationOrderMap = buildStationOrderMap(trainId);
        Integer departureOrder = stationOrderMap.get(departure);
        Integer arrivalOrder = stationOrderMap.get(arrival);
        if (departureOrder == null || arrivalOrder == null || departureOrder >= arrivalOrder) {
            return seatSegments.stream()
                    .filter(each -> Objects.equals(each.getStartStation(), departure)
                            && Objects.equals(each.getEndStation(), arrival)
                            && Objects.equals(each.getSeatStatus(), 0))
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        return seatSegments.stream()
                .collect(Collectors.groupingBy(this::physicalSeatKey))
                .values()
                .stream()
                .map(each -> selectAvailableRepresentative(each, stationOrderMap, departureOrder, arrivalOrder, departure, arrival))
                .filter(Objects::nonNull)
                .sorted((left, right) -> {
                    int carriage = left.getCarriageNumber().compareTo(right.getCarriageNumber());
                    return carriage != 0 ? carriage : left.getSeatNumber().compareTo(right.getSeatNumber());
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    private SeatDO selectAvailableRepresentative(List<SeatDO> physicalSeatSegments, Map<String, Integer> stationOrderMap,
                                                 Integer departureOrder, Integer arrivalOrder,
                                                 String departure, String arrival) {
        List<SeatDO> overlappedSegments = physicalSeatSegments.stream()
                .filter(each -> isSegmentOverlapped(stationOrderMap, departureOrder, arrivalOrder, each))
                .toList();
        if (overlappedSegments.isEmpty() || overlappedSegments.stream().anyMatch(each -> !Objects.equals(each.getSeatStatus(), 0))) {
            return null;
        }
        return overlappedSegments.stream()
                .filter(each -> Objects.equals(each.getStartStation(), departure)
                        && Objects.equals(each.getEndStation(), arrival))
                .findFirst()
                .orElse(overlappedSegments.get(0));
    }

    private String physicalSeatKey(SeatDO seat) {
        return seat.getCarriageNumber() + "#" + seat.getSeatNumber();
    }

    private Map<String, Integer> buildStationOrderMap(Long trainId) {
        return trainStationMapper.selectList(Wrappers.lambdaQuery(TrainStationDO.class)
                        .eq(TrainStationDO::getTrainId, trainId))
                .stream()
                .filter(each -> each.getDeparture() != null)
                .collect(Collectors.toMap(
                        TrainStationDO::getDeparture,
                        this::parseSequence,
                        Math::min));
    }

    private boolean isSegmentOverlapped(Map<String, Integer> stationOrderMap, Integer departureOrder,
                                        Integer arrivalOrder, SeatDO seatSegment) {
        Integer segmentStart = stationOrderMap.get(seatSegment.getStartStation());
        Integer segmentEnd = stationOrderMap.get(seatSegment.getEndStation());
        if (segmentStart == null || segmentEnd == null || segmentStart >= segmentEnd) {
            return false;
        }
        return segmentStart < arrivalOrder && departureOrder < segmentEnd;
    }

    private Integer parseSequence(TrainStationDO trainStation) {
        try {
            return Integer.parseInt(trainStation.getSequence());
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }
}
