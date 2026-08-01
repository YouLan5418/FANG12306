package org.opengoofy.index12306.customerservice.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.customerservice.fc.ToolExecutionContext;
import org.opengoofy.index12306.customerservice.chat.ChatProperties;
import org.opengoofy.index12306.customerservice.rag.RagRetriever;
import org.opengoofy.index12306.customerservice.rerank.RetrievedChunk;
import org.opengoofy.index12306.customerservice.rerank.ScoredChunk;
import org.opengoofy.index12306.framework.cache.DistributedCache;
import org.opengoofy.index12306.order.dto.resp.TicketOrderDetailRespDTO;
import org.opengoofy.index12306.order.service.OrderService;
import org.opengoofy.index12306.ticket.dao.entity.StationDO;
import org.opengoofy.index12306.ticket.dao.mapper.StationMapper;
import org.opengoofy.index12306.ticket.dto.resp.SeatClassDTO;
import org.opengoofy.index12306.ticket.dto.resp.TicketListDTO;
import org.opengoofy.index12306.ticket.dto.resp.TicketPageQueryRespDTO;
import org.opengoofy.index12306.ticket.service.TicketService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 业务工具单元测试：归属校验、结果截断、参数校验。
 */
class BusinessToolTests {

    private ToolExecutionContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new ToolExecutionContext("user1", "100", "session1");
    }

    // ==================== QueryOrderTool ====================

    @Test
    void queryOrder_correctUser_returnsTruncatedFields() {
        TicketOrderDetailRespDTO order = new TicketOrderDetailRespDTO();
        order.setOrderSn("ORD123");
        order.setUserId(100L);
        order.setStatus(0);
        order.setTrainNumber("G1");
        order.setDeparture("北京");
        order.setArrival("上海");

        OrderService orderService = mock(OrderService.class);
        when(orderService.queryTicketOrderByOrderSn("ORD123")).thenReturn(order);

        QueryOrderTool tool = new QueryOrderTool(orderService);
        String result = tool.execute(Map.of("orderSn", "ORD123"), ctx);

        JSONObject json = JSON.parseObject(result);
        assertEquals("ORD123", json.get("orderSn"));
        assertEquals("G1", json.get("trainNumber"));
        // 截断：不应包含 passengerDetails
        assertFalse(json.containsKey("passengerDetails"));
    }

    @Test
    void queryOrder_wrongUser_returnsForbidden() {
        TicketOrderDetailRespDTO order = new TicketOrderDetailRespDTO();
        order.setUserId(200L);

        OrderService orderService = mock(OrderService.class);
        when(orderService.queryTicketOrderByOrderSn("ORD999")).thenReturn(order);

        QueryOrderTool tool = new QueryOrderTool(orderService);
        String result = tool.execute(Map.of("orderSn", "ORD999"), ctx);

        JSONObject json = JSON.parseObject(result);
        assertEquals("无权查询此订单", json.get("error"));
    }

    @Test
    void queryOrder_notFound_returnsError() {
        OrderService orderService = mock(OrderService.class);
        when(orderService.queryTicketOrderByOrderSn("XXX")).thenReturn(null);

        QueryOrderTool tool = new QueryOrderTool(orderService);
        String result = tool.execute(Map.of("orderSn", "XXX"), ctx);

        assertTrue(result.contains("订单不存在"));
    }

    @Test
    void queryOrder_missingParam_returnsError() {
        QueryOrderTool tool = new QueryOrderTool(mock(OrderService.class));
        String result = tool.execute(Map.of(), ctx);
        assertTrue(result.contains("error"));
    }

    // ==================== QueryTicketTool ====================

    @Test
    void queryTicket_returnsTruncatedTrains() {
        TicketListDTO train = new TicketListDTO();
        train.setTrainNumber("G1");
        train.setDeparture("北京");
        train.setArrival("上海");
        train.setDepartureTime("08:00");
        train.setSeatClassList(List.of(
                SeatClassDTO.builder().seatType(1).price(new BigDecimal("553")).quantity(10).build()
        ));

        TicketService ticketService = mock(TicketService.class);
        when(ticketService.pageListTicketQuery(any())).thenReturn(
                TicketPageQueryRespDTO.builder().trainList(List.of(train)).build());

        QueryTicketTool tool = new QueryTicketTool(ticketService);
        String result = tool.execute(
                Map.of("departure", "北京", "arrival", "上海", "date", "2026-08-01"), ctx);

        JSONArray arr = JSON.parseArray(result);
        assertEquals(1, arr.size());
        JSONObject t = arr.getJSONObject(0);
        assertEquals("G1", t.get("trainNumber"));
        assertEquals("08:00", t.get("departureTime"));
        assertEquals(1, t.getJSONArray("seatClasses").size());
    }

    @Test
    void queryTicket_missingParam_returnsError() {
        QueryTicketTool tool = new QueryTicketTool(mock(TicketService.class));
        String result = tool.execute(Map.of("departure", "北京"), ctx);
        assertTrue(result.contains("error"));
    }

    // ==================== QueryStationTool ====================

    @Test
    void queryStation_returnsMatchingStations() {
        StationDO station = new StationDO();
        station.setName("北京北");
        station.setSpell("beijingbei");
        station.setRegion("BJ");

        StationMapper stationMapper = mock(StationMapper.class);
        when(stationMapper.selectList(any())).thenReturn(List.of(station));

        QueryStationTool tool = new QueryStationTool(stationMapper);
        String result = tool.execute(Map.of("keyword", "北京"), ctx);

        JSONArray arr = JSON.parseArray(result);
        assertEquals(1, arr.size());
        assertEquals("北京北", arr.getJSONObject(0).get("name"));
    }

    // ==================== SearchKnowledgeTool ====================

    @Test
    void searchKnowledge_returnsChunks() {
        RetrievedChunk chunk = RetrievedChunk.builder()
                .chunkId(1L).content("退票手续费5%").score(0.9f).build();

        RagRetriever ragRetriever = mock(RagRetriever.class);
        when(ragRetriever.retrieve(anyString(), any(), anyInt()))
                .thenReturn(List.of(new ScoredChunk(chunk, 0.95f)));

        SearchKnowledgeTool tool = new SearchKnowledgeTool(ragRetriever, mock(DistributedCache.class), new ChatProperties());
        String result = tool.execute(Map.of("query", "退票手续费"), ctx);

        JSONArray arr = JSON.parseArray(result);
        assertEquals(1, arr.size());
        assertTrue(arr.getJSONObject(0).getString("content").contains("退票手续费"));
    }

    @Test
    void searchKnowledge_emptyResult_returnsEmptyArray() {
        RagRetriever ragRetriever = mock(RagRetriever.class);
        when(ragRetriever.retrieve(anyString(), any(), anyInt()))
                .thenReturn(List.of());

        SearchKnowledgeTool tool = new SearchKnowledgeTool(ragRetriever, mock(DistributedCache.class), new ChatProperties());
        String result = tool.execute(Map.of("query", "未知问题"), ctx);

        assertEquals("[]", result);
    }

    @Test
    void searchKnowledge_cacheHitSkipsRetriever() {
        RagRetriever ragRetriever = mock(RagRetriever.class);
        DistributedCache distributedCache = mock(DistributedCache.class);
        when(distributedCache.get(anyString(), eq(String.class))).thenReturn("[{\"content\":\"cached\"}]");

        SearchKnowledgeTool tool = new SearchKnowledgeTool(ragRetriever, distributedCache, new ChatProperties());
        String result = tool.execute(Map.of("query", "  退票   规则 "), ctx);

        assertEquals("[{\"content\":\"cached\"}]", result);
        verifyNoInteractions(ragRetriever);
    }
}
