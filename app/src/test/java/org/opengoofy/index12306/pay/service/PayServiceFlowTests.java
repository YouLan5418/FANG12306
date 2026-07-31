package org.opengoofy.index12306.pay.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opengoofy.index12306.common.core.UserContext;
import org.opengoofy.index12306.common.core.UserInfoDTO;
import org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil;
import org.opengoofy.index12306.order.common.enums.OrderItemStatusEnum;
import org.opengoofy.index12306.order.common.enums.OrderStatusEnum;
import org.opengoofy.index12306.order.dao.entity.OrderDO;
import org.opengoofy.index12306.order.dao.entity.OrderItemDO;
import org.opengoofy.index12306.order.dao.mapper.OrderMapper;
import org.opengoofy.index12306.order.dto.domain.OrderStatusReversalDTO;
import org.opengoofy.index12306.order.mq.event.PayResultCallbackOrderEvent;
import org.opengoofy.index12306.order.mq.event.RefundResultCallbackOrderEvent;
import org.opengoofy.index12306.order.service.OrderItemService;
import org.opengoofy.index12306.order.service.OrderService;
import org.opengoofy.index12306.pay.common.enums.RefundTypeEnum;
import org.opengoofy.index12306.pay.common.enums.TradeStatusEnum;
import org.opengoofy.index12306.pay.dao.entity.PayDO;
import org.opengoofy.index12306.pay.dao.entity.RefundDO;
import org.opengoofy.index12306.pay.dao.mapper.PayMapper;
import org.opengoofy.index12306.pay.dao.mapper.RefundMapper;
import org.opengoofy.index12306.pay.dto.req.PayCallbackReqDTO;
import org.opengoofy.index12306.pay.dto.req.PayCreateReqDTO;
import org.opengoofy.index12306.pay.dto.req.RefundReqDTO;
import org.opengoofy.index12306.pay.dto.resp.PayRespDTO;
import org.opengoofy.index12306.pay.dto.resp.RefundRespDTO;
import org.opengoofy.index12306.pay.mq.produce.PayResultCallbackOrderSendProduce;
import org.opengoofy.index12306.pay.mq.produce.RefundResultCallbackOrderSendProduce;
import org.opengoofy.index12306.ticket.common.constant.TicketCacheConstant;
import org.opengoofy.index12306.ticket.dao.entity.SeatDO;
import org.opengoofy.index12306.ticket.dao.entity.TicketDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationPriceDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationRelationDO;
import org.opengoofy.index12306.ticket.dao.mapper.SeatMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TicketMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationPriceMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationRelationMapper;
import org.opengoofy.index12306.ticket.dto.req.PurchaseTicketPassengerDetailDTO;
import org.opengoofy.index12306.ticket.dto.req.PurchaseTicketReqDTO;
import org.opengoofy.index12306.ticket.dto.resp.TicketPurchaseRespDTO;
import org.opengoofy.index12306.ticket.service.TicketService;
import org.opengoofy.index12306.user.dto.req.PassengerReqDTO;
import org.opengoofy.index12306.user.dto.req.UserRegisterReqDTO;
import org.opengoofy.index12306.user.dto.resp.PassengerRespDTO;
import org.opengoofy.index12306.user.service.PassengerService;
import org.opengoofy.index12306.user.service.UserLoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

/**
 * P6 验收：支付下单 -> 回调 -> 支付结果回写(座位售出/订单已付) -> 全额退款 -> 退款结果回写(座位回滚/余票恢复)。
 * 事务回滚不污染数据库；Mock 生产者避免异步消费者与测试事务竞争。
 */
@SpringBootTest
@Transactional
class PayServiceFlowTests {

    @Autowired private PayService payService;
    @Autowired private RefundService refundService;
    @Autowired private TicketService ticketService;
    @Autowired private OrderService orderService;
    @Autowired private OrderItemService orderItemService;
    @Autowired private UserLoginService userLoginService;
    @Autowired private PassengerService passengerService;

    @Autowired private PayMapper payMapper;
    @Autowired private RefundMapper refundMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private SeatMapper seatMapper;
    @Autowired private TicketMapper ticketMapper;
    @Autowired private TrainMapper trainMapper;
    @Autowired private TrainStationRelationMapper relationMapper;
    @Autowired private TrainStationPriceMapper priceMapper;

    @Autowired private StringRedisTemplate stringRedisTemplate;

    @MockBean private PayResultCallbackOrderSendProduce payResultCallbackOrderSendProduce;
    @MockBean private RefundResultCallbackOrderSendProduce refundResultCallbackOrderSendProduce;

    private static final String DEPARTURE = "\u5317\u4eac\u5357";
    private static final String ARRIVAL = "\u4e0a\u6d77\u8679\u6865";
    private static final String SUFFIX = String.valueOf(System.currentTimeMillis());

    @AfterEach
    void clearContext() {
        UserContext.removeUser();
    }

    @Test
    void testPayAndRefundFullFlow() {
        Long trainId = seedTrainBase(3);
        String passengerId = registerUserAndPassenger("pay");

        PurchaseTicketReqDTO purchaseReq = new PurchaseTicketReqDTO();
        purchaseReq.setTrainId(String.valueOf(trainId));
        purchaseReq.setDeparture(DEPARTURE);
        purchaseReq.setArrival(ARRIVAL);
        PurchaseTicketPassengerDetailDTO p = new PurchaseTicketPassengerDetailDTO();
        p.setPassengerId(passengerId);
        p.setSeatType(2);
        purchaseReq.setPassengers(List.of(p));
        TicketPurchaseRespDTO purchaseResp = ticketService.purchaseTickets(purchaseReq);
        String orderSn = purchaseResp.getOrderSn();
        assertNotNull(orderSn);

        List<OrderItemDO> items = orderItemService.listByOrderSn(orderSn);
        int totalAmount = items.stream().mapToInt(OrderItemDO::getAmount).sum();

        PayCreateReqDTO createPayReq = new PayCreateReqDTO();
        createPayReq.setOrderSn(orderSn);
        createPayReq.setTotalAmount(totalAmount);
        createPayReq.setChannel("ALI_PAY");
        PayRespDTO payResp = payService.createPay(createPayReq);
        assertNotNull(payResp.getPaySn());
        assertNotNull(payResp.getPayUrl());
        assertEquals(TradeStatusEnum.WAIT_BUYER_PAY.getTradeCode(), payResp.getStatus());

        PayDO payDO = payMapper.selectOne(Wrappers.lambdaQuery(PayDO.class).eq(PayDO::getOrderSn, orderSn));
        assertEquals(TradeStatusEnum.WAIT_BUYER_PAY.getTradeCode(), payDO.getStatus());

        PayCallbackReqDTO callbackReq = new PayCallbackReqDTO();
        callbackReq.setOrderSn(orderSn);
        callbackReq.setTradeNo("TRADE_TEST_" + System.currentTimeMillis());
        callbackReq.setStatus(TradeStatusEnum.TRADE_SUCCESS.getTradeCode());
        callbackReq.setPayAmount(totalAmount);
        callbackReq.setGmtPayment(new Date());
        payService.callbackPay(callbackReq);

        payDO = payMapper.selectOne(Wrappers.lambdaQuery(PayDO.class).eq(PayDO::getOrderSn, orderSn));
        assertEquals(TradeStatusEnum.TRADE_SUCCESS.getTradeCode(), payDO.getStatus());

        ArgumentCaptor<PayResultCallbackOrderEvent> payCaptor = ArgumentCaptor.forClass(PayResultCallbackOrderEvent.class);
        verify(payResultCallbackOrderSendProduce).sendMessage(payCaptor.capture());
        PayResultCallbackOrderEvent payEvent = payCaptor.getValue();
        assertEquals(orderSn, payEvent.getOrderSn());

        orderService.payCallbackOrder(payEvent);
        orderService.statusReversal(OrderStatusReversalDTO.builder()
                .orderSn(orderSn)
                .orderStatus(OrderStatusEnum.ALREADY_PAID.getStatus())
                .orderItemStatus(OrderItemStatusEnum.ALREADY_PAID.getStatus())
                .build());
        ticketService.markSeatsSold(orderSn);

        Long soldSeats = seatMapper.selectCount(Wrappers.lambdaQuery(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId).eq(SeatDO::getSeatStatus, 2));
        assertEquals(1, soldSeats);

        OrderDO order = orderMapper.selectOne(Wrappers.lambdaQuery(OrderDO.class).eq(OrderDO::getOrderSn, orderSn));
        assertEquals(OrderStatusEnum.ALREADY_PAID.getStatus(), order.getStatus());
        assertNotNull(order.getPayTime());

        Long paidTickets = ticketMapper.selectCount(Wrappers.lambdaQuery(TicketDO.class)
                .eq(TicketDO::getTrainId, trainId).eq(TicketDO::getTicketStatus, 1));
        assertEquals(1, paidTickets);

        RefundReqDTO refundReq = new RefundReqDTO();
        refundReq.setOrderSn(orderSn);
        refundReq.setRefundType(RefundTypeEnum.FULL_REFUND.getCode());
        RefundRespDTO refundResp = refundService.commonRefund(refundReq);
        assertEquals(totalAmount, refundResp.getRefundAmount());
        assertEquals(TradeStatusEnum.TRADE_CLOSED.getTradeCode(), refundResp.getStatus());

        payDO = payMapper.selectOne(Wrappers.lambdaQuery(PayDO.class).eq(PayDO::getOrderSn, orderSn));
        assertEquals(TradeStatusEnum.TRADE_CLOSED.getTradeCode(), payDO.getStatus());

        Long refundCount = refundMapper.selectCount(Wrappers.lambdaQuery(RefundDO.class).eq(RefundDO::getOrderSn, orderSn));
        assertEquals(items.size(), refundCount);

        ArgumentCaptor<RefundResultCallbackOrderEvent> refundCaptor = ArgumentCaptor.forClass(RefundResultCallbackOrderEvent.class);
        verify(refundResultCallbackOrderSendProduce).sendMessage(refundCaptor.capture());
        RefundResultCallbackOrderEvent refundEvent = refundCaptor.getValue();
        assertEquals(orderSn, refundEvent.getOrderSn());
        assertEquals(RefundTypeEnum.FULL_REFUND.getCode(), refundEvent.getRefundType());

        orderService.refundReversal(refundEvent.getOrderSn(), refundEvent.getRefundType(), refundEvent.getPartialRefundDetails());
        ticketService.rollbackRefund(refundEvent.getOrderSn(), refundEvent.getPartialRefundDetails());

        Long soldAfterRefund = seatMapper.selectCount(Wrappers.lambdaQuery(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId).eq(SeatDO::getSeatStatus, 2));
        assertEquals(0, soldAfterRefund);

        order = orderMapper.selectOne(Wrappers.lambdaQuery(OrderDO.class).eq(OrderDO::getOrderSn, orderSn));
        assertEquals(OrderStatusEnum.FULL_REFUND.getStatus(), order.getStatus());

        Long unpaidTickets = ticketMapper.selectCount(Wrappers.lambdaQuery(TicketDO.class)
                .eq(TicketDO::getTrainId, trainId).eq(TicketDO::getTicketStatus, 0));
        assertEquals(1, unpaidTickets);

        String key = TicketCacheConstant.TRAIN_STATION_REMAINING_TICKET + trainId + "_" + DEPARTURE + "_" + ARRIVAL;
        assertEquals("3", stringRedisTemplate.opsForHash().get(key, "2"));
    }

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
        String username = "p6" + tag + "_" + SUFFIX;
        String userIdentity = String.format("%04d", Math.abs((tag + SUFFIX).hashCode() % 10000));
        UserRegisterReqDTO reg = new UserRegisterReqDTO();
        reg.setUsername(username);
        reg.setPassword("pwd");
        reg.setRealName("P6Test");
        reg.setIdType(0);
        reg.setIdCard("11010119900101" + userIdentity);
        reg.setPhone("137" + String.format("%08d", Math.abs((tag + SUFFIX).hashCode() % 100000000)));
        reg.setMail("p6" + tag + "_" + SUFFIX + "@example.com");
        userLoginService.register(reg);

        UserContext.setUser(UserInfoDTO.builder().userId("1").username(username).realName("P6Test").token("mock").build());
        PassengerReqDTO p = new PassengerReqDTO();
        p.setRealName("PassengerA");
        p.setIdType(0);
        p.setIdCard("11010119900201" + userIdentity);
        p.setDiscountType(0);
        p.setPhone("138" + String.format("%08d", Math.abs((tag + SUFFIX).hashCode() % 100000000)));
        passengerService.savePassenger(p);
        List<PassengerRespDTO> passengers = passengerService.listPassenger(username);
        return passengers.get(0).getId();
    }
}
