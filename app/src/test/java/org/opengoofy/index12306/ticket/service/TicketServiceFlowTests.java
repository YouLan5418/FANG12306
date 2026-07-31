package org.opengoofy.index12306.ticket.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.common.core.UserContext;
import org.opengoofy.index12306.common.core.UserInfoDTO;
import org.opengoofy.index12306.framework.convention.exception.AbstractException;
import org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil;
import org.opengoofy.index12306.order.dao.entity.OrderDO;
import org.opengoofy.index12306.order.dao.mapper.OrderMapper;
import org.opengoofy.index12306.ticket.common.constant.TicketCacheConstant;
import org.opengoofy.index12306.ticket.dao.entity.SeatDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationPriceDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationRelationDO;
import org.opengoofy.index12306.ticket.dao.mapper.SeatMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TicketMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationPriceMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationRelationMapper;
import org.opengoofy.index12306.ticket.dto.req.CancelTicketReqDTO;
import org.opengoofy.index12306.ticket.dto.req.PurchaseTicketPassengerDetailDTO;
import org.opengoofy.index12306.ticket.dto.req.PurchaseTicketReqDTO;
import org.opengoofy.index12306.ticket.dto.req.TicketPageQueryReqDTO;
import org.opengoofy.index12306.ticket.dto.resp.TicketPageQueryRespDTO;
import org.opengoofy.index12306.ticket.dto.resp.TicketPurchaseRespDTO;
import org.opengoofy.index12306.user.dto.req.PassengerReqDTO;
import org.opengoofy.index12306.user.dto.req.UserRegisterReqDTO;
import org.opengoofy.index12306.user.dto.resp.PassengerRespDTO;
import org.opengoofy.index12306.user.service.PassengerService;
import org.opengoofy.index12306.user.service.UserLoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P5 验收：车票查询(余票缓存)、购票(令牌桶+本地锁+分布式锁+选座+建单)、取消回滚(座位/余票)。
 * 事务回滚，不污染数据库。
 */
@SpringBootTest
@Transactional
class TicketServiceFlowTests {

    @Autowired private TicketService ticketService;
    @Autowired private TrainMapper trainMapper;
    @Autowired private TrainStationRelationMapper relationMapper;
    @Autowired private TrainStationPriceMapper priceMapper;
    @Autowired private SeatMapper seatMapper;
    @Autowired private TicketMapper ticketMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private UserLoginService userLoginService;
    @Autowired private PassengerService passengerService;
    @Autowired private StringRedisTemplate stringRedisTemplate;

    private static final String DEPARTURE = "北京南";
    private static final String ARRIVAL = "上海虹桥";
    private static final String SUFFIX = String.valueOf(System.currentTimeMillis());

    private void seedSeats(Long trainId, int seatType, int count) {
        String[] letters = {"A", "B", "C", "D", "E", "F"};
        for (int i = 0; i < count; i++) {
            SeatDO seat = new SeatDO();
            seat.setId(SnowflakeIdUtil.nextId());
            seat.setTrainId(trainId);
            seat.setCarriageNumber("01");
            seat.setSeatNumber(String.format("01%d%s", seatType, letters[i % letters.length]));
            seat.setSeatType(seatType);
            seat.setStartStation(DEPARTURE);
            seat.setEndStation(ARRIVAL);
            seat.setPrice((seatType + 1) * 55300);
            seat.setSeatStatus(0);
            seatMapper.insert(seat);
        }
    }

    private String registerUserAndPassenger(String tag) {
        String username = "p5" + tag + "_" + SUFFIX;
        String userIdentity = String.format("%04d", Math.abs((tag + SUFFIX).hashCode() % 10000));
        UserRegisterReqDTO reg = new UserRegisterReqDTO();
        reg.setUsername(username);
        reg.setPassword("pwd");
        reg.setRealName("P5测试");
        reg.setIdType(0);
        reg.setIdCard("11010119900101" + userIdentity);
        reg.setPhone("139" + String.format("%08d", Math.abs((tag + SUFFIX).hashCode() % 100000000)));
        reg.setMail("p5" + tag + "_" + SUFFIX + "@example.com");
        userLoginService.register(reg);

        UserContext.setUser(UserInfoDTO.builder().userId("1").username(username).realName("P5测试").token("mock").build());
        PassengerReqDTO p = new PassengerReqDTO();
        p.setRealName("乘车人甲");
        p.setIdType(0);
        p.setIdCard("11010119900201" + userIdentity);
        p.setDiscountType(0);
        p.setPhone("138" + String.format("%08d", Math.abs((tag + SUFFIX).hashCode() % 100000000)));
        passengerService.savePassenger(p);
        List<PassengerRespDTO> passengers = passengerService.listPassenger(username);
        return passengers.get(0).getId();
    }

    @AfterEach
    void clearContext() {
        UserContext.removeUser();
    }

    @Test
    void testQueryPopulatesRemainingCache() {
        Long trainId = seedTrainBase(5);
        TicketPageQueryReqDTO req = new TicketPageQueryReqDTO();
        req.setDeparture(DEPARTURE);
        req.setArrival(ARRIVAL);
        req.setCurrent(1L);
        req.setSize(10L);
        TicketPageQueryRespDTO resp = ticketService.pageListTicketQuery(req);
        assertEquals(1, resp.getTrainList().size());
        // 二等座余票应为 5
        int secondQty = resp.getTrainList().get(0).getSeatClassList().stream()
                .filter(s -> s.getSeatType() == 2).findFirst().get().getQuantity();
        assertEquals(5, secondQty);
        // 余票已写缓存
        String key = TicketCacheConstant.TRAIN_STATION_REMAINING_TICKET + trainId + "_" + DEPARTURE + "_" + ARRIVAL;
        assertEquals("5", stringRedisTemplate.opsForHash().get(key, "2"));
    }

    @Test
    void testPurchaseLocksSeatAndCreatesOrder() {
        Long trainId = seedTrainBase(3);
        String passengerId = registerUserAndPassenger("buy");

        PurchaseTicketReqDTO req = new PurchaseTicketReqDTO();
        req.setTrainId(String.valueOf(trainId));
        req.setDeparture(DEPARTURE);
        req.setArrival(ARRIVAL);
        PurchaseTicketPassengerDetailDTO p = new PurchaseTicketPassengerDetailDTO();
        p.setPassengerId(passengerId);
        p.setSeatType(2);
        req.setPassengers(List.of(p));

        TicketPurchaseRespDTO resp = ticketService.purchaseTickets(req);
        assertNotNull(resp.getOrderSn());

        // t_ticket 已生成，状态未支付
        Long ticketCount = ticketMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(
                org.opengoofy.index12306.ticket.dao.entity.TicketDO.class)
                .eq(org.opengoofy.index12306.ticket.dao.entity.TicketDO::getTrainId, trainId));
        assertEquals(1, ticketCount);

        // 座位已锁定
        Long lockedCount = seatMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getSeatStatus, 1));
        assertEquals(1, lockedCount);

        // 令牌桶余票递减
        String key = TicketCacheConstant.TRAIN_STATION_REMAINING_TICKET + trainId + "_" + DEPARTURE + "_" + ARRIVAL;
        assertEquals("2", stringRedisTemplate.opsForHash().get(key, "2"));

        // 订单已创建，状态待支付
        OrderDO order = orderMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, resp.getOrderSn()));
        assertEquals(0, order.getStatus());
    }

    @Test
    void testPurchaseNoStockRejected() {
        Long trainId = seedTrainBase(1);
        String passengerId = registerUserAndPassenger("nos");

        PurchaseTicketReqDTO req = new PurchaseTicketReqDTO();
        req.setTrainId(String.valueOf(trainId));
        req.setDeparture(DEPARTURE);
        req.setArrival(ARRIVAL);
        PurchaseTicketPassengerDetailDTO p1 = new PurchaseTicketPassengerDetailDTO();
        p1.setPassengerId(passengerId);
        p1.setSeatType(2);
        PurchaseTicketPassengerDetailDTO p2 = new PurchaseTicketPassengerDetailDTO();
        p2.setPassengerId(passengerId);
        p2.setSeatType(2);
        req.setPassengers(List.of(p1, p2));

        assertThrows(AbstractException.class, () -> ticketService.purchaseTickets(req));

        String key = TicketCacheConstant.TRAIN_STATION_REMAINING_TICKET + trainId + "_" + DEPARTURE + "_" + ARRIVAL;
        assertEquals("1", stringRedisTemplate.opsForHash().get(key, "2"));
    }

    @Test
    void testPurchaseWithChooseSeatsLocksSpecifiedSeat() {
        Long trainId = seedTrainBase(3);
        String passengerId = registerUserAndPassenger("choose");

        PurchaseTicketReqDTO req = new PurchaseTicketReqDTO();
        req.setTrainId(String.valueOf(trainId));
        req.setDeparture(DEPARTURE);
        req.setArrival(ARRIVAL);
        req.setChooseSeats(List.of("012A"));
        PurchaseTicketPassengerDetailDTO p = new PurchaseTicketPassengerDetailDTO();
        p.setPassengerId(passengerId);
        p.setSeatType(2);
        req.setPassengers(List.of(p));

        TicketPurchaseRespDTO resp = ticketService.purchaseTickets(req);
        assertNotNull(resp.getOrderSn());

        Long lockedSpecifiedSeat = seatMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getSeatNumber, "012A")
                .eq(SeatDO::getSeatStatus, 1));
        assertEquals(1, lockedSpecifiedSeat);
    }

    @Test
    void testPurchaseWithOccupiedChooseSeatsRejectedAndRemainingRolledBack() {
        Long trainId = seedTrainBase(3);
        String firstPassengerId = registerUserAndPassenger("choose1");

        PurchaseTicketReqDTO firstReq = new PurchaseTicketReqDTO();
        firstReq.setTrainId(String.valueOf(trainId));
        firstReq.setDeparture(DEPARTURE);
        firstReq.setArrival(ARRIVAL);
        firstReq.setChooseSeats(List.of("012A"));
        PurchaseTicketPassengerDetailDTO firstPassenger = new PurchaseTicketPassengerDetailDTO();
        firstPassenger.setPassengerId(firstPassengerId);
        firstPassenger.setSeatType(2);
        firstReq.setPassengers(List.of(firstPassenger));
        ticketService.purchaseTickets(firstReq);

        String secondPassengerId = registerUserAndPassenger("choose2");
        PurchaseTicketReqDTO secondReq = new PurchaseTicketReqDTO();
        secondReq.setTrainId(String.valueOf(trainId));
        secondReq.setDeparture(DEPARTURE);
        secondReq.setArrival(ARRIVAL);
        secondReq.setChooseSeats(List.of("012A"));
        PurchaseTicketPassengerDetailDTO secondPassenger = new PurchaseTicketPassengerDetailDTO();
        secondPassenger.setPassengerId(secondPassengerId);
        secondPassenger.setSeatType(2);
        secondReq.setPassengers(List.of(secondPassenger));

        assertThrows(AbstractException.class, () -> ticketService.purchaseTickets(secondReq));
        String key = TicketCacheConstant.TRAIN_STATION_REMAINING_TICKET + trainId + "_" + DEPARTURE + "_" + ARRIVAL;
        assertEquals("2", stringRedisTemplate.opsForHash().get(key, "2"));
    }

    @Test
    void testCancelRollsBackSeatAndRemaining() {
        Long trainId = seedTrainBase(3);
        String passengerId = registerUserAndPassenger("cancel");

        PurchaseTicketReqDTO req = new PurchaseTicketReqDTO();
        req.setTrainId(String.valueOf(trainId));
        req.setDeparture(DEPARTURE);
        req.setArrival(ARRIVAL);
        PurchaseTicketPassengerDetailDTO p = new PurchaseTicketPassengerDetailDTO();
        p.setPassengerId(passengerId);
        p.setSeatType(2);
        req.setPassengers(List.of(p));
        TicketPurchaseRespDTO resp = ticketService.purchaseTickets(req);

        CancelTicketReqDTO cancel = new CancelTicketReqDTO();
        cancel.setOrderSn(resp.getOrderSn());
        ticketService.cancelTicketOrder(cancel);

        // 订单已关闭
        OrderDO order = orderMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, resp.getOrderSn()));
        assertEquals(30, order.getStatus());

        // 座位回滚为可用
        Long lockedCount = seatMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getSeatStatus, 1));
        assertEquals(0, lockedCount);

        // 余票恢复
        String key = TicketCacheConstant.TRAIN_STATION_REMAINING_TICKET + trainId + "_" + DEPARTURE + "_" + ARRIVAL;
        assertEquals("3", stringRedisTemplate.opsForHash().get(key, "2"));
    }

    /** 建车次/区间/票价，并按 seatType 各放若干座位（二等座 secondCount 张） */
    private Long seedTrainBase(int secondCount) {
        TrainDO train = new TrainDO();
        train.setId(SnowflakeIdUtil.nextId());
        train.setTrainNumber("G" + train.getId());
        train.setTrainType(0);
        train.setStartStation(DEPARTURE);
        train.setEndStation(ARRIVAL);
        train.setSaleTime(new Date());
        train.setSaleStatus(0);
        train.setDepartureTime(new Date());
        train.setArrivalTime(new Date());
        trainMapper.insert(train);
        Long trainId = train.getId();
        stringRedisTemplate.delete(TicketCacheConstant.TRAIN_STATION_REMAINING_TICKET + trainId + "_" + DEPARTURE + "_" + ARRIVAL);

        TrainStationRelationDO relation = new TrainStationRelationDO();
        relation.setTrainId(trainId);
        relation.setDeparture(DEPARTURE);
        relation.setArrival(ARRIVAL);
        relation.setDepartureFlag(1);
        relation.setArrivalFlag(1);
        relation.setDepartureTime(new Date());
        relation.setArrivalTime(new Date());
        relationMapper.insert(relation);

        for (int seatType : new int[]{0, 1, 2}) {
            TrainStationPriceDO price = new TrainStationPriceDO();
            price.setTrainId(trainId);
            price.setDeparture(DEPARTURE);
            price.setArrival(ARRIVAL);
            price.setSeatType(seatType);
            price.setPrice((seatType + 1) * 55300);
            priceMapper.insert(price);
        }
        seedSeats(trainId, 0, 2);
        seedSeats(trainId, 1, 2);
        seedSeats(trainId, 2, secondCount);
        return trainId;
    }
}
