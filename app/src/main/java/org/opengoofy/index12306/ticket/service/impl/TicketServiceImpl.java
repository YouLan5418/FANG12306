package org.opengoofy.index12306.ticket.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.common.core.UserContext;
import org.opengoofy.index12306.common.toolkit.AESCryptUtil;
import org.opengoofy.index12306.framework.convention.exception.ClientException;
import org.opengoofy.index12306.framework.convention.exception.ServiceException;
import org.opengoofy.index12306.framework.idempotent.annotation.Idempotent;
import org.opengoofy.index12306.framework.idempotent.enums.IdempotentSceneEnum;
import org.opengoofy.index12306.framework.idempotent.enums.IdempotentTypeEnum;
import org.opengoofy.index12306.order.common.enums.OrderItemStatusEnum;
import org.opengoofy.index12306.order.common.enums.OrderStatusEnum;
import org.opengoofy.index12306.order.dao.entity.OrderDO;
import org.opengoofy.index12306.order.dao.entity.OrderItemDO;
import org.opengoofy.index12306.order.dao.mapper.OrderItemMapper;
import org.opengoofy.index12306.order.dao.mapper.OrderMapper;
import org.opengoofy.index12306.pay.dto.req.RefundDetailReqDTO;
import org.opengoofy.index12306.order.dto.req.CancelTicketOrderReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderCreateReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderItemCreateReqDTO;
import org.opengoofy.index12306.order.service.OrderItemService;
import org.opengoofy.index12306.order.service.OrderService;
import org.opengoofy.index12306.ticket.common.constant.TicketCacheConstant;
import org.opengoofy.index12306.ticket.common.enums.TicketStatusEnum;
import org.opengoofy.index12306.ticket.dao.entity.SeatDO;
import org.opengoofy.index12306.ticket.dao.entity.StationDO;
import org.opengoofy.index12306.ticket.dao.entity.TicketDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationPriceDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationRelationDO;
import org.opengoofy.index12306.ticket.dao.mapper.StationMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TicketMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationPriceMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationRelationMapper;
import org.opengoofy.index12306.ticket.dto.req.CancelTicketReqDTO;
import org.opengoofy.index12306.ticket.dto.req.PurchaseTicketReqDTO;
import org.opengoofy.index12306.ticket.dto.req.TicketPageQueryReqDTO;
import org.opengoofy.index12306.ticket.dto.resp.SeatClassDTO;
import org.opengoofy.index12306.ticket.dto.resp.TicketListDTO;
import org.opengoofy.index12306.ticket.dto.resp.TicketPageQueryRespDTO;
import org.opengoofy.index12306.ticket.dto.resp.TicketPurchaseRespDTO;
import org.opengoofy.index12306.ticket.dto.resp.TrainPurchaseTicketRespDTO;
import org.opengoofy.index12306.ticket.service.SeatService;
import org.opengoofy.index12306.ticket.service.TicketService;
import org.opengoofy.index12306.ticket.service.TrainSeatTypeSelector;
import org.opengoofy.index12306.ticket.service.TrainStationService;
import org.opengoofy.index12306.user.dto.resp.PassengerActualRespDTO;
import org.opengoofy.index12306.user.service.PassengerService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 票务服务实现：查询(余票缓存)、购票(令牌桶+本地锁+分布式公平锁+选座+建单)、取消回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TrainMapper trainMapper;
    private final TrainStationRelationMapper trainStationRelationMapper;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final StationMapper stationMapper;
    private final TicketMapper ticketMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SeatService seatService;
    private final TrainStationService trainStationService;
    private final TrainSeatTypeSelector trainSeatTypeSelector;
    private final PassengerService passengerService;
    private final OrderService orderService;
    private final OrderItemService orderItemService;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;

    /** 本地锁（Caffeine 缓存 ReentrantLock），按 trainId 串行化单进程购票 */
    private final Cache<String, java.util.concurrent.locks.ReentrantLock> localLockMap =
            Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.DAYS).build();

    @Override
    public TicketPageQueryRespDTO pageListTicketQuery(TicketPageQueryReqDTO requestParam) {
        if (requestParam == null || requestParam.getDeparture() == null || requestParam.getDeparture().isBlank()
                || requestParam.getArrival() == null || requestParam.getArrival().isBlank()) {
            throw new ClientException("出发站/到达站不能为空");
        }
        if (requestParam.getDeparture().equals(requestParam.getArrival())) {
            throw new ClientException("出发站与到达站不能相同");
        }
        Long current = requestParam.getCurrent();
        Long size = requestParam.getSize();
        if (current == null || current < 1 || size == null || size < 1 || size > 500) {
            throw new ClientException("分页参数不合法，页码须为正数，每页最多500条");
        }
        List<String> departureStations = resolveStationNames(requestParam.getDeparture());
        List<String> arrivalStations = resolveStationNames(requestParam.getArrival());
        var wrapper = Wrappers.lambdaQuery(TrainStationRelationDO.class)
                .in(TrainStationRelationDO::getDeparture, departureStations)
                .in(TrainStationRelationDO::getArrival, arrivalStations);
        if (requestParam.getDepartureDate() != null) {
            ZoneId zone = ZoneId.of("Asia/Shanghai");
            LocalDate date = requestParam.getDepartureDate();
            wrapper.ge(TrainStationRelationDO::getDepartureTime, Date.from(date.atStartOfDay(zone).toInstant()))
                    .lt(TrainStationRelationDO::getDepartureTime, Date.from(date.plusDays(1).atStartOfDay(zone).toInstant()));
        }
        wrapper.orderByAsc(TrainStationRelationDO::getDepartureTime, TrainStationRelationDO::getId);
        Page<TrainStationRelationDO> page = trainStationRelationMapper.selectPage(new Page<>(current, size), wrapper);
        List<TrainStationRelationDO> relations = page.getRecords();
        List<TicketListDTO> trainList = new ArrayList<>();
        for (TrainStationRelationDO relation : relations) {
            TrainDO train = trainMapper.selectById(relation.getTrainId());
            TicketListDTO dto = new TicketListDTO();
            dto.setTrainId(String.valueOf(train.getId()));
            dto.setTrainNumber(train.getTrainNumber());
            dto.setTrainType(train.getTrainType());
            dto.setDeparture(relation.getDeparture());
            dto.setArrival(relation.getArrival());
            dto.setDepartureTime(formatTime(relation.getDepartureTime()));
            dto.setArrivalTime(formatTime(relation.getArrivalTime()));
            dto.setSeatClassList(buildSeatClassList(train.getId(), relation.getDeparture(), relation.getArrival()));
            trainList.add(dto);
        }
        return TicketPageQueryRespDTO.builder()
                .trainList(trainList)
                .current(requestParam.getCurrent())
                .size(requestParam.getSize())
                .total(page.getTotal())
                .build();
    }

    @Idempotent(
            uniqueKeyPrefix = "index12306-ticket:purchase:",
            key = "T(org.opengoofy.index12306.common.core.UserContext).getUsername()",
            message = "正在执行下单流程，请稍后...",
            scene = IdempotentSceneEnum.RESTAPI,
            type = IdempotentTypeEnum.SPEL
    )
    @Transactional(rollbackFor = Exception.class)
    @Override
    public TicketPurchaseRespDTO purchaseTickets(PurchaseTicketReqDTO requestParam) {
        validatePurchase(requestParam);
        String username = UserContext.getUsername();
        Long trainId = Long.parseLong(requestParam.getTrainId());

        // 乘车人实际信息
        List<String> passengerIds = requestParam.getPassengers().stream()
                .map(p -> p.getPassengerId()).toList();
        List<PassengerActualRespDTO> passengerActuals = passengerService.listPassengerActual(username, passengerIds);
        Map<String, TrainSeatTypeSelector.PassengerActualInfo> infoMap = passengerActuals.stream()
                .collect(Collectors.toMap(PassengerActualRespDTO::getId,
                        p -> new TrainSeatTypeSelector.PassengerActualInfo(p.getRealName(), p.getIdType(), p.getIdCard(), p.getPhone())));
        validateBusinessRules(requestParam, trainId, passengerActuals);

        // 按 seatType 分组令牌桶扣减
        Map<Integer, Long> seatTypeCount = requestParam.getPassengers().stream()
                .collect(Collectors.groupingBy(p -> p.getSeatType(), Collectors.counting()));

        // 分布式公平锁 + 本地锁
        RLock distributedLock = redissonClient.getFairLock(TicketCacheConstant.LOCK_PURCHASE_TICKETS + trainId);
        boolean tokenDecremented = false;
        try {
            distributedLock.lock();
            localLock(trainId).lock();
            try {
                // 获取购票锁后重新检查，避免等待锁期间已经生成的订单被漏掉。
                validatePassengerTimeConflict(passengerActuals,
                        trainStationService.getRelation(trainId, requestParam.getDeparture(), requestParam.getArrival()));
                // 令牌桶：余票不足直接拒绝
                for (Map.Entry<Integer, Long> e : seatTypeCount.entrySet()) {
                    int remaining = getRemaining(trainId, e.getKey(), requestParam.getDeparture(), requestParam.getArrival());
                    if (remaining < e.getValue()) {
                        throw new ServiceException("列车站点已无余票，seatType=" + e.getKey());
                    }
                }
                // 扣减令牌
                for (Map.Entry<Integer, Long> e : seatTypeCount.entrySet()) {
                    decrementRemaining(trainId, e.getKey(), requestParam.getDeparture(), requestParam.getArrival(), e.getValue().intValue());
                }
                tokenDecremented = true;

                List<TrainPurchaseTicketRespDTO> seats = selectAndLockSeats(trainId, requestParam, infoMap);
                // 存 t_ticket
                for (TrainPurchaseTicketRespDTO seat : seats) {
                    TicketDO ticket = new TicketDO();
                    ticket.setId(org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil.nextId());
                    ticket.setUsername(username);
                    ticket.setTrainId(trainId);
                    ticket.setCarriageNumber(seat.getCarriageNumber());
                    ticket.setSeatNumber(seat.getSeatNumber());
                    ticket.setPassengerId(Long.parseLong(seat.getPassengerId()));
                    ticket.setTicketStatus(TicketStatusEnum.UNPAID.getCode());
                    ticketMapper.insert(ticket);
                }
                // 调订单服务建单
                TrainDO train = trainMapper.selectById(trainId);
                TrainStationRelationDO relation = trainStationService.getRelation(trainId, requestParam.getDeparture(), requestParam.getArrival());
                TicketOrderCreateReqDTO orderReq = buildOrderReq(username, train, relation, requestParam, seats);
                String orderSn = orderService.createTicketOrder(orderReq);

                TicketPurchaseRespDTO resp = new TicketPurchaseRespDTO();
                resp.setOrderSn(orderSn);
                resp.setTicketOrderDetailResults(seats);
                return resp;
            } finally {
                try { localLock(trainId).unlock(); } catch (Throwable ignored) {}
            }
        } catch (Throwable ex) {
            // 令牌已扣减需手动回滚（DB 由事务回滚）
            if (tokenDecremented) {
                rollbackToken(trainId, requestParam.getDeparture(), requestParam.getArrival(), seatTypeCount);
            }
            throw ex;
        } finally {
            try { distributedLock.unlock(); } catch (Throwable ignored) {}
        }
    }

    @Override
    public void cancelTicketOrder(CancelTicketReqDTO requestParam) {
        closeOrderAndRollback(requestParam.getOrderSn());
    }

    @Override
    public void closeOrderAndRollback(String orderSn) {
        CancelTicketOrderReqDTO req = new CancelTicketOrderReqDTO();
        req.setOrderSn(orderSn);
        boolean closed = orderService.closeTickOrder(req);
        if (closed) {
            rollbackOnOrderClose(orderSn);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void rollbackOnOrderClose(String orderSn) {
        List<OrderItemDO> items = orderItemService.listSeatOperationItemsByOrderSn(orderSn);
        if (items.isEmpty()) {
            return;
        }
        Map<Integer, Integer> seatTypeCount = new HashMap<>();
        Long trainId = items.get(0).getTrainId();
        org.opengoofy.index12306.order.dto.resp.TicketOrderDetailRespDTO detail = orderService.queryTicketOrderBasicByOrderSn(orderSn);
        for (OrderItemDO item : items) {
            int released = seatService.releaseSeatByPosition(trainId, item.getCarriageNumber(), item.getSeatNumber(),
                    detail.getDeparture(), detail.getArrival());
            if (released > 0) {
                seatTypeCount.merge(item.getSeatType(), 1, Integer::sum);
                ticketMapper.update(null, Wrappers.lambdaUpdate(TicketDO.class)
                        .eq(TicketDO::getTrainId, trainId)
                        .eq(TicketDO::getCarriageNumber, item.getCarriageNumber())
                        .eq(TicketDO::getSeatNumber, item.getSeatNumber())
                        .eq(TicketDO::getUsername, item.getUsername())
                        .set(TicketDO::getTicketStatus, TicketStatusEnum.UNPAID.getCode()));
            }
        }
        for (Map.Entry<Integer, Integer> e : seatTypeCount.entrySet()) {
            incrementRemaining(trainId, e.getKey(), detail.getDeparture(), detail.getArrival(), e.getValue());
        }
        log.info("订单 {} 关单回滚完成，座位与余票已恢复", orderSn);
    }

    @Override
    public void markSeatsSold(String orderSn) {
        List<OrderItemDO> items = orderItemService.listSeatOperationItemsByOrderSn(orderSn);
        if (items.isEmpty()) {
            return;
        }
        Long trainId = items.get(0).getTrainId();
        var detail = orderService.queryTicketOrderBasicByOrderSn(orderSn);
        for (OrderItemDO item : items) {
            seatService.sellSeatByPosition(trainId, item.getCarriageNumber(), item.getSeatNumber(),
                    detail.getDeparture(), detail.getArrival());
            // t_ticket -> 已支付（按 username+trainId+座位定位）
            ticketMapper.update(null, Wrappers.lambdaUpdate(TicketDO.class)
                    .eq(TicketDO::getTrainId, trainId)
                    .eq(TicketDO::getCarriageNumber, item.getCarriageNumber())
                    .eq(TicketDO::getSeatNumber, item.getSeatNumber())
                    .eq(TicketDO::getUsername, item.getUsername())
                    .set(TicketDO::getTicketStatus, TicketStatusEnum.PAID.getCode()));
        }
    }

    @Override
    public void rollbackRefund(String orderSn, List<RefundDetailReqDTO> partialDetails) {
        List<OrderItemDO> items = orderItemService.listSeatOperationItemsByOrderSn(orderSn);
        if (items.isEmpty()) {
            return;
        }
        Long trainId = items.get(0).getTrainId();
        var detail = orderService.queryTicketOrderBasicByOrderSn(orderSn);
        Map<Integer, Integer> seatTypeCount = new HashMap<>();
        List<OrderItemDO> toRollback;
        if (partialDetails == null || partialDetails.isEmpty()) {
            toRollback = items;
        } else {
            toRollback = items.stream().filter(it -> partialDetails.stream().anyMatch(d ->
                    Objects.equals(d.getCarriageNumber(), it.getCarriageNumber())
                            && Objects.equals(d.getSeatNumber(), it.getSeatNumber()))).toList();
        }
        for (OrderItemDO item : toRollback) {
            int released = seatService.releaseSeatByPosition(trainId, item.getCarriageNumber(), item.getSeatNumber(),
                    detail.getDeparture(), detail.getArrival());
            if (released > 0) {
                seatTypeCount.merge(item.getSeatType(), 1, Integer::sum);
                ticketMapper.update(null, Wrappers.lambdaUpdate(TicketDO.class)
                        .eq(TicketDO::getTrainId, trainId)
                        .eq(TicketDO::getCarriageNumber, item.getCarriageNumber())
                        .eq(TicketDO::getSeatNumber, item.getSeatNumber())
                        .set(TicketDO::getTicketStatus, TicketStatusEnum.UNPAID.getCode()));
            }
        }
        for (Map.Entry<Integer, Integer> e : seatTypeCount.entrySet()) {
            incrementRemaining(trainId, e.getKey(), detail.getDeparture(), detail.getArrival(), e.getValue());
        }
        log.info("订单 {} 退款回滚完成，座位与余票已恢复", orderSn);
    }

    @Override
    public void refreshRemainingTicketCache(Long trainId, String departure, String arrival) {
        String key = remainingKey(trainId, departure, arrival);
        List<TrainStationPriceDO> prices = trainStationPriceMapper.selectList(
                Wrappers.lambdaQuery(TrainStationPriceDO.class)
                        .eq(TrainStationPriceDO::getTrainId, trainId)
                        .eq(TrainStationPriceDO::getDeparture, departure)
                        .eq(TrainStationPriceDO::getArrival, arrival));
        for (TrainStationPriceDO price : prices) {
            int remaining = seatService.countAvailable(trainId, price.getSeatType(), departure, arrival);
            stringRedisTemplate.opsForHash().put(key, price.getSeatType().toString(), String.valueOf(remaining));
        }
    }

    // ---------- 私有辅助 ----------

    private void validatePurchase(PurchaseTicketReqDTO req) {
        if (req == null || req.getTrainId() == null || !req.getTrainId().matches("[1-9]\\d*")
                || req.getPassengers() == null || req.getPassengers().isEmpty()
                || req.getDeparture() == null || req.getDeparture().isBlank()
                || req.getArrival() == null || req.getArrival().isBlank()) {
            throw new ClientException("购票参数缺失或不合法");
        }
        try {
            Long.parseLong(req.getTrainId());
        } catch (NumberFormatException ex) {
            throw new ClientException("车次编号不合法");
        }
        if (req.getDeparture().equals(req.getArrival())) {
            throw new ClientException("出发站与到达站不能相同");
        }
        Set<String> passengerIds = new HashSet<>();
        for (var passenger : req.getPassengers()) {
            if (passenger == null || passenger.getPassengerId() == null || passenger.getPassengerId().isBlank()
                    || passenger.getSeatType() == null) {
                throw new ClientException("乘车人或座席参数缺失");
            }
            if (!passengerIds.add(passenger.getPassengerId())) {
                throw new ClientException("同一乘车人不能重复购票");
            }
        }
    }

    private void validateBusinessRules(PurchaseTicketReqDTO req, Long trainId, List<PassengerActualRespDTO> passengerActuals) {
        TrainDO train = trainMapper.selectById(trainId);
        if (train == null) {
            throw new ClientException("车次不存在");
        }
        TrainStationRelationDO relation = trainStationService.getRelation(trainId, req.getDeparture(), req.getArrival());
        if (relation == null) {
            throw new ClientException("车次区间不存在");
        }
        Date now = new Date();
        if (relation.getDepartureTime() != null && !relation.getDepartureTime().after(now)) {
            throw new ClientException("列车已发车，无法购票");
        }
        if (train.getSaleTime() != null && train.getSaleTime().after(now)) {
            throw new ClientException("车票尚未开售");
        }
        validateTicketType(req, passengerActuals);
        validatePassengerTimeConflict(passengerActuals, relation);
    }

    private void validateTicketType(PurchaseTicketReqDTO req, List<PassengerActualRespDTO> passengerActuals) {
        Map<String, PassengerActualRespDTO> actualMap = passengerActuals.stream()
                .collect(Collectors.toMap(PassengerActualRespDTO::getId, each -> each));
        for (var passenger : req.getPassengers()) {
            int ticketType = passenger.getTicketType() == null ? 0 : passenger.getTicketType();
            if (ticketType < 0 || ticketType > 2) {
                throw new ClientException("不支持的票种: " + ticketType);
            }
            PassengerActualRespDTO actual = actualMap.get(passenger.getPassengerId());
            if (actual == null) {
                throw new ClientException("乘车人不存在: " + passenger.getPassengerId());
            }
            if (ticketType == 2 && !Objects.equals(actual.getDiscountType(), 2)) {
                throw new ClientException("乘车人不符合学生票购买条件");
            }
        }
    }

    private void validatePassengerTimeConflict(List<PassengerActualRespDTO> passengerActuals, TrainStationRelationDO relation) {
        List<String> encryptedIdCards = passengerActuals.stream()
                .map(PassengerActualRespDTO::getIdCard)
                .filter(Objects::nonNull)
                .map(AESCryptUtil::encrypt)
                .toList();
        if (encryptedIdCards.isEmpty()) {
            return;
        }
        List<OrderItemDO> activeItems = orderItemMapper.selectList(Wrappers.lambdaQuery(OrderItemDO.class)
                .in(OrderItemDO::getIdCard, encryptedIdCards)
                .in(OrderItemDO::getStatus, OrderItemStatusEnum.PENDING_PAYMENT.getStatus(), OrderItemStatusEnum.ALREADY_PAID.getStatus()));
        for (OrderItemDO item : activeItems) {
            OrderDO order = orderMapper.selectOne(Wrappers.lambdaQuery(OrderDO.class)
                    .eq(OrderDO::getOrderSn, item.getOrderSn())
                    .in(OrderDO::getStatus, OrderStatusEnum.PENDING_PAYMENT.getStatus(),
                            OrderStatusEnum.ALREADY_PAID.getStatus(), OrderStatusEnum.PARTIAL_REFUND.getStatus()));
            if (order != null && Objects.equals(order.getRidingDate(), toLocalDate(relation.getDepartureTime()))
                    && timeOverlap(order.getDepartureTime(), order.getArrivalTime(), relation.getDepartureTime(), relation.getArrivalTime())) {
                throw new ClientException("乘车人已存在同日时间冲突订单");
            }
        }
    }

    private boolean timeOverlap(Date leftStart, Date leftEnd, Date rightStart, Date rightEnd) {
        if (leftStart == null || leftEnd == null || rightStart == null || rightEnd == null) {
            return false;
        }
        return leftStart.before(rightEnd) && rightStart.before(leftEnd);
    }

    private java.util.concurrent.locks.ReentrantLock localLock(Long trainId) {
        return localLockMap.get(TicketCacheConstant.LOCK_PURCHASE_TICKETS + trainId, k -> new java.util.concurrent.locks.ReentrantLock());
    }

    private List<TrainPurchaseTicketRespDTO> selectAndLockSeats(Long trainId, PurchaseTicketReqDTO requestParam,
                                                                Map<String, TrainSeatTypeSelector.PassengerActualInfo> infoMap) {
        boolean chooseSeat = requestParam.getChooseSeats() != null && !requestParam.getChooseSeats().isEmpty();
        int maxRetryTimes = chooseSeat ? 1 : 5;
        ServiceException lastException = null;
        for (int i = 0; i < maxRetryTimes; i++) {
            List<TrainPurchaseTicketRespDTO> seats = trainSeatTypeSelector.select(requestParam, infoMap);
            List<TrainPurchaseTicketRespDTO> lockedSeats = new ArrayList<>();
            try {
                lockSeatsByAllocation(trainId, requestParam.getDeparture(), requestParam.getArrival(), seats, lockedSeats);
                return seats;
            } catch (ServiceException ex) {
                releaseLockedSeats(trainId, requestParam.getDeparture(), requestParam.getArrival(), lockedSeats);
                lastException = ex;
                if (chooseSeat) {
                    throw ex;
                }
            }
        }
        throw lastException == null ? new ServiceException("座位竞争激烈，请稍后重试") : lastException;
    }

    private void lockSeatsByAllocation(Long trainId, String departure, String arrival, List<TrainPurchaseTicketRespDTO> seats,
                                       List<TrainPurchaseTicketRespDTO> lockedSeats) {
        for (TrainPurchaseTicketRespDTO seat : seats) {
            int updated = seatService.lockSeatByPosition(trainId, seat.getCarriageNumber(), seat.getSeatNumber(), departure, arrival);
            if (updated < 1) {
                throw new ServiceException("座位已被占用，选座失败: " + seat.getCarriageNumber() + seat.getSeatNumber());
            }
            lockedSeats.add(seat);
        }
    }

    private void releaseLockedSeats(Long trainId, String departure, String arrival, List<TrainPurchaseTicketRespDTO> lockedSeats) {
        for (TrainPurchaseTicketRespDTO seat : lockedSeats) {
            seatService.releaseSeatByPosition(trainId, seat.getCarriageNumber(), seat.getSeatNumber(), departure, arrival);
        }
    }

    private String remainingKey(Long trainId, String departure, String arrival) {
        return TicketCacheConstant.TRAIN_STATION_REMAINING_TICKET + trainId + "_" + departure + "_" + arrival;
    }

    private int getRemaining(Long trainId, Integer seatType, String departure, String arrival) {
        String key = remainingKey(trainId, departure, arrival);
        Object val = stringRedisTemplate.opsForHash().get(key, seatType.toString());
        if (val == null) {
            int count = seatService.countAvailable(trainId, seatType, departure, arrival);
            stringRedisTemplate.opsForHash().put(key, seatType.toString(), String.valueOf(count));
            return count;
        }
        return Integer.parseInt(val.toString());
    }

    private List<String> resolveStationNames(String stationOrRegion) {
        List<StationDO> stations = stationMapper.selectList(Wrappers.lambdaQuery(StationDO.class)
                .eq(StationDO::getName, stationOrRegion)
                .or().eq(StationDO::getCode, stationOrRegion)
                .or().eq(StationDO::getRegion, stationOrRegion)
                .or().eq(StationDO::getRegionName, stationOrRegion));
        if (stations.isEmpty()) {
            return List.of(stationOrRegion);
        }
        return stations.stream()
                .map(StationDO::getName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void decrementRemaining(Long trainId, Integer seatType, String departure, String arrival, int count) {
        stringRedisTemplate.opsForHash().increment(remainingKey(trainId, departure, arrival), seatType.toString(), -count);
    }

    private void incrementRemaining(Long trainId, Integer seatType, String departure, String arrival, int count) {
        stringRedisTemplate.opsForHash().increment(remainingKey(trainId, departure, arrival), seatType.toString(), count);
    }

    private void rollbackToken(Long trainId, String departure, String arrival, Map<Integer, Long> seatTypeCount) {
        seatTypeCount.forEach((st, c) -> {
            try { incrementRemaining(trainId, st, departure, arrival, c.intValue()); } catch (Throwable ignored) {}
        });
    }

    private List<SeatClassDTO> buildSeatClassList(Long trainId, String departure, String arrival) {
        List<TrainStationPriceDO> prices = trainStationPriceMapper.selectList(
                Wrappers.lambdaQuery(TrainStationPriceDO.class)
                        .eq(TrainStationPriceDO::getTrainId, trainId)
                        .eq(TrainStationPriceDO::getDeparture, departure)
                        .eq(TrainStationPriceDO::getArrival, arrival));
        List<SeatClassDTO> result = new ArrayList<>();
        for (TrainStationPriceDO p : prices) {
            int remaining = getRemaining(trainId, p.getSeatType(), departure, arrival);
            result.add(SeatClassDTO.builder()
                    .seatType(p.getSeatType())
                    .quantity(remaining)
                    .price(new BigDecimal(p.getPrice()).divide(new BigDecimal("100"), 1, RoundingMode.HALF_UP))
                    .build());
        }
        return result;
    }

    private TicketOrderCreateReqDTO buildOrderReq(String username, TrainDO train, TrainStationRelationDO relation,
                                                  PurchaseTicketReqDTO req, List<TrainPurchaseTicketRespDTO> seats) {
        TicketOrderCreateReqDTO orderReq = new TicketOrderCreateReqDTO();
        orderReq.setUserId(parseUserId());
        orderReq.setUsername(username);
        orderReq.setTrainId(train.getId());
        orderReq.setTrainNumber(train.getTrainNumber());
        orderReq.setDeparture(req.getDeparture());
        orderReq.setArrival(req.getArrival());
        orderReq.setDepartureTime(relation.getDepartureTime());
        orderReq.setArrivalTime(relation.getArrivalTime());
        orderReq.setRidingDate(toLocalDate(relation.getDepartureTime()));
        orderReq.setSource(0);
        orderReq.setOrderTime(new Date());
        List<TicketOrderItemCreateReqDTO> items = seats.stream().map(s -> {
            TicketOrderItemCreateReqDTO item = new TicketOrderItemCreateReqDTO();
            item.setAmount(s.getAmount());
            item.setCarriageNumber(s.getCarriageNumber());
            item.setSeatNumber(s.getSeatNumber());
            item.setIdCard(s.getIdCard());
            item.setIdType(s.getIdType());
            item.setPhone(s.getPhone());
            item.setSeatType(s.getSeatType());
            item.setTicketType(s.getTicketType());
            item.setRealName(s.getRealName());
            return item;
        }).toList();
        orderReq.setTicketOrderItems(items);
        return orderReq;
    }

    private Long parseUserId() {
        String uid = UserContext.getUserId();
        return uid == null ? 0L : Long.parseLong(uid);
    }

    private LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String formatTime(Date date) {
        if (date == null) return null;
        return String.format("%tH:%<tM", date);
    }
}
