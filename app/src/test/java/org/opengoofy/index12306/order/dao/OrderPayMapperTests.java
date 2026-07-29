package org.opengoofy.index12306.order.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.common.toolkit.AESCryptUtil;
import org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil;
import org.opengoofy.index12306.order.dao.entity.OrderDO;
import org.opengoofy.index12306.order.dao.entity.OrderItemDO;
import org.opengoofy.index12306.order.dao.entity.OrderItemPassengerDO;
import org.opengoofy.index12306.order.dao.mapper.OrderItemMapper;
import org.opengoofy.index12306.order.dao.mapper.OrderItemPassengerMapper;
import org.opengoofy.index12306.order.dao.mapper.OrderMapper;
import org.opengoofy.index12306.pay.dao.entity.PayDO;
import org.opengoofy.index12306.pay.dao.entity.RefundDO;
import org.opengoofy.index12306.pay.dao.mapper.PayMapper;
import org.opengoofy.index12306.pay.dao.mapper.RefundMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-T2 验收：订单/支付域 Mapper insert + selectOne 按 order_sn 路由到正确 HASH 分区。
 * 事务回滚，不污染数据库。
 */
@SpringBootTest
@Transactional
class OrderPayMapperTests {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private OrderItemPassengerMapper orderItemPassengerMapper;
    @Autowired
    private PayMapper payMapper;
    @Autowired
    private RefundMapper refundMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String ORDER_SN = "TEST" + System.currentTimeMillis();
    private static final String ID_CARD = "110101199001011234";
    private static final String PHONE = "13800001234";

    private String partitionOf(String table, String idColumnValue) {
        return jdbcTemplate.queryForObject(
                "SELECT tableoid::regclass::text FROM " + table + " WHERE order_sn = ?",
                String.class, idColumnValue);
    }

    @Test
    void testOrderInsertSelectPartition() {
        OrderDO order = new OrderDO();
        long id = SnowflakeIdUtil.nextId();
        order.setId(id);
        order.setOrderSn(ORDER_SN);
        order.setUserId(1001L);
        order.setUsername("mapper_test");
        order.setTrainId(1L);
        order.setTrainNumber("G1");
        order.setRidingDate(LocalDate.now());
        order.setDeparture("北京南");
        order.setArrival("上海虹桥");
        order.setDepartureTime(new Date());
        order.setArrivalTime(new Date());
        order.setSource(0);
        order.setStatus(0);
        order.setOrderTime(new Date());
        assertEquals(1, orderMapper.insert(order));

        OrderDO actual = orderMapper.selectOne(new LambdaQueryWrapper<OrderDO>()
                .eq(OrderDO::getOrderSn, ORDER_SN));
        assertNotNull(actual);
        assertEquals(id, actual.getId());
        assertEquals("G1", actual.getTrainNumber());
        assertEquals(0, actual.getStatus());

        String partition = partitionOf("t_order", ORDER_SN);
        assertTrue(partition.startsWith("t_order_p"), "t_order 应落在子分区，实际: " + partition);
    }

    @Test
    void testOrderItemSamePartitionAsOrder() {
        OrderItemDO item = new OrderItemDO();
        item.setId(SnowflakeIdUtil.nextId());
        item.setOrderSn(ORDER_SN);
        item.setUserId(1001L);
        item.setUsername("mapper_test");
        item.setTrainId(1L);
        item.setCarriageNumber("01");
        item.setSeatType(0);
        item.setSeatNumber("01A");
        item.setRealName("乘车人甲");
        item.setIdType(0);
        item.setIdCard(ID_CARD);
        item.setTicketType(0);
        item.setPhone(PHONE);
        item.setStatus(0);
        item.setAmount(55300);
        assertEquals(1, orderItemMapper.insert(item));

        // 加密字段作查询条件需先显式加密（Wrapper 参数不经过 TypeHandler）
        OrderItemDO actual = orderItemMapper.selectOne(new LambdaQueryWrapper<OrderItemDO>()
                .eq(OrderItemDO::getOrderSn, ORDER_SN)
                .eq(OrderItemDO::getIdCard, AESCryptUtil.encrypt(ID_CARD)));
        assertNotNull(actual);
        assertEquals(ID_CARD, actual.getIdCard());
        assertEquals(PHONE, actual.getPhone());
        assertEquals(55300, actual.getAmount());

        String rawIdCard = jdbcTemplate.queryForObject(
                "SELECT id_card FROM t_order_item WHERE order_sn = ?", String.class, ORDER_SN);
        assertNotEquals(ID_CARD, rawIdCard);

        // 同一 order_sn 下 t_order 与 t_order_item 落在同编号分区（HASH 共置）
        OrderDO order = new OrderDO();
        order.setId(SnowflakeIdUtil.nextId());
        order.setOrderSn(ORDER_SN);
        order.setStatus(0);
        assertEquals(1, orderMapper.insert(order));
        String orderPartition = partitionOf("t_order", ORDER_SN);
        String itemPartition = partitionOf("t_order_item", ORDER_SN);
        assertTrue(itemPartition.startsWith("t_order_item_p"), "t_order_item 应落在子分区，实际: " + itemPartition);
        assertEquals(orderPartition.replace("t_order_p", ""), itemPartition.replace("t_order_item_p", ""),
                "同一 order_sn 的订单与明细应在同编号分区");
    }

    @Test
    void testOrderItemPassengerPartition() {
        OrderItemPassengerDO passenger = new OrderItemPassengerDO();
        passenger.setId(SnowflakeIdUtil.nextId());
        passenger.setOrderSn(ORDER_SN);
        passenger.setIdType(0);
        passenger.setIdCard(ID_CARD);
        assertEquals(1, orderItemPassengerMapper.insert(passenger));

        OrderItemPassengerDO actual = orderItemPassengerMapper.selectOne(new LambdaQueryWrapper<OrderItemPassengerDO>()
                .eq(OrderItemPassengerDO::getOrderSn, ORDER_SN));
        assertNotNull(actual);
        assertEquals(ID_CARD, actual.getIdCard());
        assertTrue(partitionOf("t_order_item_passenger", ORDER_SN).startsWith("t_order_item_passenger_p"));
    }

    @Test
    void testPayInsertSelectPartition() {
        PayDO pay = new PayDO();
        pay.setId(SnowflakeIdUtil.nextId());
        pay.setPaySn("PAY" + System.currentTimeMillis());
        pay.setOrderSn(ORDER_SN);
        pay.setChannel("alipay");
        pay.setTradeType("page");
        pay.setSubject("G1 北京南-上海虹桥");
        pay.setOrderRequestId("REQ" + System.currentTimeMillis());
        pay.setTotalAmount(55300);
        pay.setStatus("WAIT_BUYER_PAY");
        assertEquals(1, payMapper.insert(pay));

        PayDO actual = payMapper.selectOne(new LambdaQueryWrapper<PayDO>()
                .eq(PayDO::getOrderSn, ORDER_SN));
        assertNotNull(actual);
        assertEquals("WAIT_BUYER_PAY", actual.getStatus());
        assertEquals(55300, actual.getTotalAmount());
        assertTrue(partitionOf("t_pay", ORDER_SN).startsWith("t_pay_p"));
    }

    @Test
    void testRefundInsertSelectPartition() {
        RefundDO refund = new RefundDO();
        refund.setId(SnowflakeIdUtil.nextId());
        refund.setPaySn("PAY123");
        refund.setOrderSn(ORDER_SN);
        refund.setTradeNo("TRADE123");
        refund.setAmount(55300);
        refund.setUserId(1001L);
        refund.setUsername("mapper_test");
        refund.setTrainId(1L);
        refund.setTrainNumber("G1");
        refund.setRidingDate(LocalDate.now());
        refund.setSeatType(0);
        refund.setIdType(0);
        refund.setIdCard(ID_CARD);
        refund.setRealName("乘车人甲");
        refund.setStatus(0);
        assertEquals(1, refundMapper.insert(refund));

        RefundDO actual = refundMapper.selectOne(new LambdaQueryWrapper<RefundDO>()
                .eq(RefundDO::getOrderSn, ORDER_SN)
                .eq(RefundDO::getIdCard, AESCryptUtil.encrypt(ID_CARD)));
        assertNotNull(actual);
        assertEquals(ID_CARD, actual.getIdCard());
        assertEquals(55300, actual.getAmount());
        assertTrue(partitionOf("t_refund", ORDER_SN).startsWith("t_refund_p"));
    }
}