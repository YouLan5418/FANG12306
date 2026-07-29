package org.opengoofy.index12306.ticket.dao;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil;
import org.opengoofy.index12306.ticket.dao.entity.CarriageDO;
import org.opengoofy.index12306.ticket.dao.entity.RegionDO;
import org.opengoofy.index12306.ticket.dao.entity.SeatDO;
import org.opengoofy.index12306.ticket.dao.entity.StationDO;
import org.opengoofy.index12306.ticket.dao.entity.TicketDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationPriceDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationRelationDO;
import org.opengoofy.index12306.ticket.dao.mapper.CarriageMapper;
import org.opengoofy.index12306.ticket.dao.mapper.RegionMapper;
import org.opengoofy.index12306.ticket.dao.mapper.SeatMapper;
import org.opengoofy.index12306.ticket.dao.mapper.StationMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TicketMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationPriceMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationRelationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-T3 验收：t_ticket 按 username 路由、t_seat 按 train_id 路由；7 张基础单表 CRUD 与自增主键回填。
 * 事务回滚，不污染数据库。
 */
@SpringBootTest
@Transactional
class TicketMapperTests {

    @Autowired
    private TicketMapper ticketMapper;
    @Autowired
    private SeatMapper seatMapper;
    @Autowired
    private TrainMapper trainMapper;
    @Autowired
    private StationMapper stationMapper;
    @Autowired
    private CarriageMapper carriageMapper;
    @Autowired
    private TrainStationMapper trainStationMapper;
    @Autowired
    private TrainStationRelationMapper trainStationRelationMapper;
    @Autowired
    private TrainStationPriceMapper trainStationPriceMapper;
    @Autowired
    private RegionMapper regionMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String USERNAME = "mapper_test_" + System.currentTimeMillis();

    @Test
    void testTicketRoutedByUsername() {
        TicketDO ticket = new TicketDO();
        long id = SnowflakeIdUtil.nextId();
        ticket.setId(id);
        ticket.setUsername(USERNAME);
        ticket.setTrainId(1L);
        ticket.setCarriageNumber("01");
        ticket.setSeatNumber("01A");
        ticket.setPassengerId(2001L);
        ticket.setTicketStatus(0);
        assertEquals(1, ticketMapper.insert(ticket));

        TicketDO actual = ticketMapper.selectOne(new LambdaQueryWrapper<TicketDO>()
                .eq(TicketDO::getId, id)
                .eq(TicketDO::getUsername, USERNAME));
        assertNotNull(actual);
        assertEquals(0, actual.getTicketStatus());

        String partition = jdbcTemplate.queryForObject(
                "SELECT tableoid::regclass::text FROM t_ticket WHERE id = ? AND username = ?",
                String.class, id, USERNAME);
        assertTrue(partition.startsWith("t_ticket_p"), "t_ticket 应按 username 落在子分区，实际: " + partition);
    }

    @Test
    void testSeatRoutedByTrainId() {
        SeatDO seat = new SeatDO();
        long id = SnowflakeIdUtil.nextId();
        long trainId = SnowflakeIdUtil.nextId();
        seat.setId(id);
        seat.setTrainId(trainId);
        seat.setCarriageNumber("01");
        seat.setSeatNumber("01A");
        seat.setSeatType(0);
        seat.setStartStation("北京南");
        seat.setEndStation("上海虹桥");
        seat.setPrice(55300);
        seat.setSeatStatus(0);
        assertEquals(1, seatMapper.insert(seat));

        SeatDO actual = seatMapper.selectOne(new LambdaQueryWrapper<SeatDO>()
                .eq(SeatDO::getId, id)
                .eq(SeatDO::getTrainId, trainId));
        assertNotNull(actual);
        assertEquals(0, actual.getSeatStatus());
        assertEquals(55300, actual.getPrice());

        String partition = jdbcTemplate.queryForObject(
                "SELECT tableoid::regclass::text FROM t_seat WHERE id = ? AND train_id = ?",
                String.class, id, trainId);
        assertTrue(partition.startsWith("t_seat_p"), "t_seat 应按 train_id 落在子分区，实际: " + partition);
    }

    @Test
    void testTrainCrudAndAutoId() {
        TrainDO train = new TrainDO();
        train.setTrainNumber("G" + System.currentTimeMillis() % 100000);
        train.setTrainType(0);
        train.setStartStation("北京南");
        train.setEndStation("上海虹桥");
        train.setSaleTime(new Date());
        train.setSaleStatus(0);
        train.setDepartureTime(new Date());
        train.setArrivalTime(new Date());
        assertEquals(1, trainMapper.insert(train));
        assertNotNull(train.getId(), "BIGSERIAL 主键应回填");

        TrainDO actual = trainMapper.selectById(train.getId());
        assertNotNull(actual);
        assertEquals(train.getTrainNumber(), actual.getTrainNumber());
    }

    @Test
    void testStationCrud() {
        StationDO station = new StationDO();
        station.setCode("TBJP");
        station.setName("测试北京" + System.currentTimeMillis() % 1000);
        station.setSpell("ceshibeijing");
        station.setRegion("BJ");
        station.setRegionName("北京");
        assertEquals(1, stationMapper.insert(station));
        assertNotNull(station.getId());

        StationDO actual = stationMapper.selectOne(new LambdaQueryWrapper<StationDO>()
                .eq(StationDO::getName, station.getName()));
        assertNotNull(actual);
        assertEquals("TBJP", actual.getCode());
    }

    @Test
    void testCarriageCrud() {
        CarriageDO carriage = new CarriageDO();
        carriage.setTrainId(1L);
        carriage.setCarriageNumber("01");
        carriage.setCarriageType(0);
        carriage.setSeatCount(80);
        assertEquals(1, carriageMapper.insert(carriage));
        assertNotNull(carriage.getId());

        CarriageDO actual = carriageMapper.selectById(carriage.getId());
        assertNotNull(actual);
        assertEquals(80, actual.getSeatCount());
    }

    @Test
    void testTrainStationCrud() {
        TrainStationDO ts = new TrainStationDO();
        ts.setTrainId(1L);
        ts.setStationId(1L);
        ts.setSequence("1");
        ts.setDeparture("北京南");
        ts.setArrival("上海虹桥");
        ts.setArrivalTime(new Date());
        ts.setDepartureTime(new Date());
        ts.setStopoverTime(5);
        assertEquals(1, trainStationMapper.insert(ts));
        assertNotNull(ts.getId());

        TrainStationDO actual = trainStationMapper.selectById(ts.getId());
        assertNotNull(actual);
        assertEquals(5, actual.getStopoverTime());
    }

    @Test
    void testTrainStationRelationCrud() {
        TrainStationRelationDO relation = new TrainStationRelationDO();
        relation.setTrainId(1L);
        relation.setDeparture("北京南");
        relation.setArrival("上海虹桥");
        relation.setDepartureFlag(1);
        relation.setArrivalFlag(1);
        relation.setDepartureTime(new Date());
        relation.setArrivalTime(new Date());
        assertEquals(1, trainStationRelationMapper.insert(relation));
        assertNotNull(relation.getId());

        TrainStationRelationDO actual = trainStationRelationMapper.selectOne(
                new LambdaQueryWrapper<TrainStationRelationDO>()
                        .eq(TrainStationRelationDO::getTrainId, 1L)
                        .eq(TrainStationRelationDO::getDeparture, "北京南")
                        .eq(TrainStationRelationDO::getArrival, "上海虹桥"));
        assertNotNull(actual);
        assertEquals(1, actual.getDepartureFlag());
    }

    @Test
    void testTrainStationPriceCrud() {
        TrainStationPriceDO price = new TrainStationPriceDO();
        price.setTrainId(1L);
        price.setDeparture("北京南");
        price.setArrival("上海虹桥");
        price.setSeatType(0);
        price.setPrice(55300);
        assertEquals(1, trainStationPriceMapper.insert(price));
        assertNotNull(price.getId());

        TrainStationPriceDO actual = trainStationPriceMapper.selectById(price.getId());
        assertNotNull(actual);
        assertEquals(55300, actual.getPrice());
    }

    @Test
    void testRegionCrud() {
        RegionDO region = new RegionDO();
        region.setName("测试州" + System.currentTimeMillis() % 1000);
        region.setFullName("测试州全称");
        region.setCode("CSZ");
        region.setInitial("C");
        region.setSpell("ceshizhou");
        region.setPopularFlag(0);
        assertEquals(1, regionMapper.insert(region));
        assertNotNull(region.getId());

        RegionDO actual = regionMapper.selectOne(new LambdaQueryWrapper<RegionDO>()
                .eq(RegionDO::getCode, "CSZ"));
        assertNotNull(actual);
        assertEquals("测试州全称", actual.getFullName());
    }
}