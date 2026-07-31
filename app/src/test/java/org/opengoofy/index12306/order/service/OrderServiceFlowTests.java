package org.opengoofy.index12306.order.service;

import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.framework.convention.exception.AbstractException;
import org.opengoofy.index12306.framework.convention.page.PageResponse;
import org.opengoofy.index12306.order.common.enums.OrderItemStatusEnum;
import org.opengoofy.index12306.order.common.enums.OrderStatusEnum;
import org.opengoofy.index12306.order.dao.entity.OrderDO;
import org.opengoofy.index12306.order.dao.mapper.OrderItemMapper;
import org.opengoofy.index12306.order.dao.mapper.OrderItemPassengerMapper;
import org.opengoofy.index12306.order.dao.mapper.OrderMapper;
import org.opengoofy.index12306.order.dto.domain.OrderStatusReversalDTO;
import org.opengoofy.index12306.order.dto.req.CancelTicketOrderReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderCreateReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderItemCreateReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderPageQueryReqDTO;
import org.opengoofy.index12306.order.dto.resp.TicketOrderDetailRespDTO;
import org.opengoofy.index12306.order.mq.event.PayResultCallbackOrderEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4 验收：建单（三表写入 + 延迟关单 MQ）、查询分页、状态机流转与关单。
 * 事务回滚，不污染数据库。MQ 发送成功由 createTicketOrder 不抛异常隐含（delayLevel=14）。
 */
@SpringBootTest
@Transactional
class OrderServiceFlowTests {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private OrderItemPassengerMapper orderItemPassengerMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long USER_ID = 9001L;
    private static final String USERNAME = "p4test";

    private TicketOrderCreateReqDTO buildCreateReq() {
        TicketOrderCreateReqDTO req = new TicketOrderCreateReqDTO();
        req.setUserId(USER_ID);
        req.setUsername(USERNAME);
        req.setTrainId(1L);
        req.setTrainNumber("G1");
        req.setDeparture("北京南");
        req.setArrival("上海虹桥");
        req.setDepartureTime(new Date());
        req.setArrivalTime(new Date());
        req.setRidingDate(LocalDate.now());
        req.setSource(0);
        req.setOrderTime(new Date());

        TicketOrderItemCreateReqDTO item1 = new TicketOrderItemCreateReqDTO();
        item1.setAmount(55300);
        item1.setCarriageNumber("01");
        item1.setSeatNumber("01A");
        item1.setIdCard("110101199001011234");
        item1.setIdType(0);
        item1.setPhone("13800001111");
        item1.setSeatType(0);
        item1.setTicketType(0);
        item1.setRealName("乘车人甲");

        TicketOrderItemCreateReqDTO item2 = new TicketOrderItemCreateReqDTO();
        item2.setAmount(55300);
        item2.setCarriageNumber("01");
        item2.setSeatNumber("01B");
        item2.setIdCard("110101199001011235");
        item2.setIdType(0);
        item2.setPhone("13800002222");
        item2.setSeatType(0);
        item2.setTicketType(0);
        item2.setRealName("乘车人乙");

        req.setTicketOrderItems(List.of(item1, item2));
        return req;
    }

    @Test
    void testCreateTicketOrderThreeTablesAndMq() {
        String orderSn = orderService.createTicketOrder(buildCreateReq());
        assertNotNull(orderSn);

        // t_order 一条，状态待支付
        OrderDO order = orderMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, orderSn));
        assertEquals(OrderStatusEnum.PENDING_PAYMENT.getStatus(), order.getStatus());

        // 三表均有记录
        Long itemCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_order_item WHERE order_sn = ?", Long.class, orderSn);
        Long passengerCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_order_item_passenger WHERE order_sn = ?", Long.class, orderSn);
        assertEquals(2L, itemCount);
        assertEquals(2L, passengerCount);
        // 延迟关单消息已投递（createTicketOrder 未抛异常即 SendStatus=SEND_OK）
    }

    @Test
    void testQueryDetailAndPage() {
        String orderSn = orderService.createTicketOrder(buildCreateReq());

        TicketOrderDetailRespDTO detail = orderService.queryTicketOrderByOrderSn(orderSn);
        assertEquals(orderSn, detail.getOrderSn());
        assertEquals(2, detail.getPassengerDetails().size());

        TicketOrderPageQueryReqDTO pageReq = new TicketOrderPageQueryReqDTO();
        pageReq.setUserId(USER_ID);
        pageReq.setStatusType(0);
        pageReq.setCurrent(1L);
        pageReq.setSize(10L);
        PageResponse<TicketOrderDetailRespDTO> page = orderService.pageTicketOrder(pageReq);
        assertTrue(page.getTotal() >= 1);
        assertEquals(1, page.getRecords().size());
    }

    @Test
    void testStatusReversalStateMachineRejectsNonPending() {
        String orderSn = orderService.createTicketOrder(buildCreateReq());

        // 待支付 -> 已支付：成功
        orderService.statusReversal(OrderStatusReversalDTO.builder()
                .orderSn(orderSn)
                .orderStatus(OrderStatusEnum.ALREADY_PAID.getStatus())
                .orderItemStatus(OrderItemStatusEnum.ALREADY_PAID.getStatus())
                .build());
        OrderDO paid = orderMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, orderSn));
        assertEquals(OrderStatusEnum.ALREADY_PAID.getStatus(), paid.getStatus());

        // 支付成功消息重复投递：幂等忽略
        orderService.statusReversal(OrderStatusReversalDTO.builder()
                .orderSn(orderSn)
                .orderStatus(OrderStatusEnum.ALREADY_PAID.getStatus())
                .orderItemStatus(OrderItemStatusEnum.ALREADY_PAID.getStatus())
                .build());

        // 已支付状态流转到其它状态：被拒
        assertThrows(AbstractException.class, () -> orderService.statusReversal(OrderStatusReversalDTO.builder()
                .orderSn(orderSn)
                .orderStatus(OrderStatusEnum.COMPLETED.getStatus())
                .orderItemStatus(OrderItemStatusEnum.CHECKED_IN.getStatus())
                .build()));

        // 已支付订单关单返回 false（不关单）
        CancelTicketOrderReqDTO closeReq = new CancelTicketOrderReqDTO();
        closeReq.setOrderSn(orderSn);
        assertFalse(orderService.closeTickOrder(closeReq));
    }

    @Test
    void testPayCallbackUpdatesPayInfo() {
        String orderSn = orderService.createTicketOrder(buildCreateReq());
        Date payTime = new Date();
        orderService.payCallbackOrder(PayResultCallbackOrderEvent.builder()
                .orderSn(orderSn)
                .paySn("PAY" + System.currentTimeMillis())
                .channel("WECHAT_PAY")
                .gmtPayment(payTime)
                .payAmount(110600)
                .status("TRADE_SUCCESS")
                .build());
        OrderDO order = orderMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, orderSn));
        assertNotNull(order.getPayTime());
        assertEquals(1, order.getPayType());
    }

    @Test
    void testCloseTickOrderOnPending() {
        String orderSn = orderService.createTicketOrder(buildCreateReq());
        CancelTicketOrderReqDTO closeReq = new CancelTicketOrderReqDTO();
        closeReq.setOrderSn(orderSn);

        assertTrue(orderService.closeTickOrder(closeReq));
        OrderDO closed = orderMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(OrderDO.class)
                .eq(OrderDO::getOrderSn, orderSn));
        assertEquals(OrderStatusEnum.CLOSED.getStatus(), closed.getStatus());

        Long itemClosedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_order_item WHERE order_sn = ? AND status = ?",
                Long.class, orderSn, OrderItemStatusEnum.CLOSED.getStatus());
        assertEquals(2L, itemClosedCount);

        // 已关闭订单再次关单返回 false
        assertFalse(orderService.closeTickOrder(closeReq));

        assertThrows(AbstractException.class, () -> orderService.statusReversal(OrderStatusReversalDTO.builder()
                .orderSn(orderSn)
                .orderStatus(OrderStatusEnum.ALREADY_PAID.getStatus())
                .orderItemStatus(OrderItemStatusEnum.ALREADY_PAID.getStatus())
                .build()));
    }
}
