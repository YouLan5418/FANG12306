package org.opengoofy.index12306.ticket.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengoofy.index12306.framework.convention.exception.ClientException;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationRelationDO;
import org.opengoofy.index12306.ticket.dao.mapper.StationMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationRelationMapper;
import org.opengoofy.index12306.ticket.dto.req.PurchaseTicketPassengerDetailDTO;
import org.opengoofy.index12306.ticket.dto.req.PurchaseTicketReqDTO;
import org.opengoofy.index12306.ticket.dto.req.TicketPageQueryReqDTO;
import org.opengoofy.index12306.ticket.service.impl.TicketServiceImpl;
import org.opengoofy.index12306.user.service.PassengerService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketQueryValidationTests {
    @Mock StationMapper stationMapper;
    @Mock TrainStationRelationMapper relationMapper;
    @Mock PassengerService passengerService;
    @InjectMocks TicketServiceImpl service;

    @BeforeEach
    void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, TrainStationRelationDO.class);
    }

    @Test
    void queryUsesPageAndShanghaiDateBoundaries() {
        var req = query();
        req.setCurrent(2L);
        req.setSize(3L);
        req.setDepartureDate(LocalDate.of(2026, 10, 14));
        when(stationMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<TrainStationRelationDO> page = invocation.getArgument(0);
            assertEquals(2L, page.getCurrent());
            assertEquals(3L, page.getSize());
            LambdaQueryWrapper<TrainStationRelationDO> wrapper = invocation.getArgument(1);
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("departure_time >="));
            assertTrue(sql.contains("departure_time <"));
            assertTrue(sql.contains("ORDER BY departure_time ASC,id ASC"));
            var zone = ZoneId.of("Asia/Shanghai");
            assertTrue(wrapper.getParamNameValuePairs().containsValue(
                    Date.from(req.getDepartureDate().atStartOfDay(zone).toInstant())));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(
                    Date.from(req.getDepartureDate().plusDays(1).atStartOfDay(zone).toInstant())));
            return page.setRecords(List.of()).setTotal(7L);
        });
        var result = service.pageListTicketQuery(req);
        assertEquals(7L, result.getTotal());
        assertEquals(2L, result.getCurrent());
        assertTrue(result.getTrainList().isEmpty());
    }

    @Test
    void queryWithoutDateRemainsSupported() {
        when(stationMapper.selectList(any())).thenReturn(List.of());
        when(relationMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<TrainStationRelationDO> wrapper = invocation.getArgument(1);
            assertFalse(wrapper.getSqlSegment().contains("departure_time >="));
            Page<TrainStationRelationDO> page = invocation.getArgument(0);
            return page.setRecords(List.of()).setTotal(0L);
        });
        assertEquals(0L, service.pageListTicketQuery(query()).getTotal());
    }

    @Test
    void invalidPaginationAndSameStationAreRejectedBeforeDatabaseAccess() {
        for (long size : new long[]{0, -1, 501}) {
            var req = query();
            req.setSize(size);
            assertThrows(ClientException.class, () -> service.pageListTicketQuery(req));
        }
        var req = query();
        req.setCurrent(0L);
        assertThrows(ClientException.class, () -> service.pageListTicketQuery(req));
        req.setCurrent(1L);
        req.setArrival(req.getDeparture());
        assertThrows(ClientException.class, () -> service.pageListTicketQuery(req));
        verifyNoInteractions(stationMapper, relationMapper);
    }

    @Test
    void duplicatePassengersRejectedBeforePassengerLookupOrStockChange() {
        var req = purchase();
        req.setPassengers(List.of(passenger(), passenger()));
        assertThrows(ClientException.class, () -> service.purchaseTickets(req));
        verifyNoInteractions(passengerService);
    }

    @Test
    void incompletePassengerAndInvalidTrainRejectedAsClientErrors() {
        var req = purchase();
        req.setPassengers(Arrays.asList((PurchaseTicketPassengerDetailDTO) null));
        assertThrows(ClientException.class, () -> service.purchaseTickets(req));
        req.setPassengers(List.of(passenger()));
        for (String id : new String[]{"abc", "-1", "99999999999999999999999"}) {
            req.setTrainId(id);
            assertThrows(ClientException.class, () -> service.purchaseTickets(req));
        }
        verifyNoInteractions(passengerService);
    }

    @Test
    void purchaseSameStationRejected() {
        var req = purchase();
        req.setArrival(req.getDeparture());
        assertThrows(ClientException.class, () -> service.purchaseTickets(req));
        verifyNoInteractions(passengerService);
    }

    private TicketPageQueryReqDTO query() {
        var req = new TicketPageQueryReqDTO();
        req.setDeparture("北京南");
        req.setArrival("上海虹桥");
        return req;
    }

    private PurchaseTicketReqDTO purchase() {
        var req = new PurchaseTicketReqDTO();
        req.setTrainId("1");
        req.setDeparture("北京南");
        req.setArrival("上海虹桥");
        req.setPassengers(List.of(passenger()));
        return req;
    }

    private PurchaseTicketPassengerDetailDTO passenger() {
        var passenger = new PurchaseTicketPassengerDetailDTO();
        passenger.setPassengerId("123");
        passenger.setSeatType(2);
        return passenger;
    }
}
