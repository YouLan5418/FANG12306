package org.opengoofy.index12306.ticket.controller;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.cache.DistributedCache;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.opengoofy.index12306.ticket.dao.entity.StationDO;
import org.opengoofy.index12306.ticket.dao.entity.TrainStationDO;
import org.opengoofy.index12306.ticket.dao.mapper.StationMapper;
import org.opengoofy.index12306.ticket.dao.mapper.TrainStationMapper;
import org.opengoofy.index12306.ticket.dto.req.CancelTicketReqDTO;
import org.opengoofy.index12306.ticket.dto.req.PurchaseTicketReqDTO;
import org.opengoofy.index12306.ticket.dto.req.TicketPageQueryReqDTO;
import org.opengoofy.index12306.ticket.dto.resp.TicketPageQueryRespDTO;
import org.opengoofy.index12306.ticket.dto.resp.TicketPurchaseRespDTO;
import org.opengoofy.index12306.ticket.service.TicketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 票务控制层。车票查询/车站查询公开，购票/取消需登录。
 */
@RestController
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final StationMapper stationMapper;
    private final TrainStationMapper trainStationMapper;
    private final DistributedCache distributedCache;

    private static final long STATION_CACHE_TIMEOUT_SECONDS = 24 * 60 * 60;
    private static final String STATION_ALL_CACHE_KEY = "index12306:ticket-service:station:all";
    private static final String STATION_REGION_CACHE_KEY = "index12306:ticket-service:station:regions";
    private static final String STATION_QUERY_CACHE_KEY_PREFIX = "index12306:ticket-service:station:query:";

    @GetMapping("/api/ticket-service/ticket/query")
    public Result<TicketPageQueryRespDTO> pageListTicketQuery(TicketPageQueryReqDTO requestParam) {
        return Results.success(ticketService.pageListTicketQuery(requestParam));
    }

    @PostMapping("/api/ticket-service/ticket/purchase")
    public Result<TicketPurchaseRespDTO> purchaseTickets(@RequestBody PurchaseTicketReqDTO requestParam) {
        return Results.success(ticketService.purchaseTickets(requestParam));
    }

    @PostMapping("/api/ticket-service/ticket/cancel")
    public Result<Void> cancelTicketOrder(@RequestBody CancelTicketReqDTO requestParam) {
        ticketService.cancelTicketOrder(requestParam);
        return Results.success();
    }

    // ==================== 车站基础数据（公开） ====================

    /** 查询全部车站 */
    @GetMapping("/api/ticket-service/station/all")
    public Result<List<StationDO>> listAllStations() {
        return Results.success(getCachedStationList(STATION_ALL_CACHE_KEY, () -> stationMapper.selectList(null)));
    }

    /** 查询全部区域，用于前端按区域分组选择车站 */
    @GetMapping("/api/ticket-service/station/regions")
    public Result<List<String>> listStationRegions() {
        String cachedRegions = distributedCache.get(STATION_REGION_CACHE_KEY, String.class);
        if (cachedRegions != null && !cachedRegions.isBlank()) {
            return Results.success(JSON.parseArray(cachedRegions, String.class));
        }
        List<String> regions = stationMapper.selectList(null).stream()
                .map(each -> each.getRegionName() != null ? each.getRegionName() : each.getRegion())
                .filter(each -> each != null && !each.isBlank())
                .distinct()
                .sorted()
                .toList();
        distributedCache.put(STATION_REGION_CACHE_KEY, regions, STATION_CACHE_TIMEOUT_SECONDS);
        return Results.success(regions);
    }

    /** 按区域或名称查询车站 */
    @GetMapping("/api/ticket-service/region-station/query")
    public Result<List<StationDO>> listRegionStations(
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "name", required = false) String name) {
        String cacheKey = STATION_QUERY_CACHE_KEY_PREFIX
                + "region:" + normalizeCachePart(region)
                + ":name:" + normalizeCachePart(name);
        return Results.success(getCachedStationList(cacheKey, () -> {
            LambdaQueryWrapper<StationDO> wrapper = new LambdaQueryWrapper<>();
            if (region != null && !region.isBlank()) {
                wrapper.and(each -> each.eq(StationDO::getRegion, region)
                        .or().eq(StationDO::getRegionName, region));
            }
            if (name != null && !name.isBlank()) {
                wrapper.and(each -> each.like(StationDO::getName, name)
                        .or().like(StationDO::getSpell, name)
                        .or().like(StationDO::getCode, name));
            }
            return stationMapper.selectList(wrapper);
        }));
    }

    /** 按车次查询经停站明细 */
    @GetMapping("/api/ticket-service/train-station/query")
    public Result<List<TrainStationDO>> listTrainStations(@RequestParam("trainId") Long trainId) {
        return Results.success(trainStationMapper.selectList(
                new LambdaQueryWrapper<TrainStationDO>()
                        .eq(TrainStationDO::getTrainId, trainId)
                        .orderByAsc(TrainStationDO::getId)));
    }

    private List<StationDO> getCachedStationList(String cacheKey, StationCacheLoader loader) {
        String cachedStations = distributedCache.get(cacheKey, String.class);
        if (cachedStations != null && !cachedStations.isBlank()) {
            return JSON.parseArray(cachedStations, StationDO.class);
        }
        List<StationDO> stations = loader.load();
        distributedCache.put(cacheKey, stations, STATION_CACHE_TIMEOUT_SECONDS);
        return stations;
    }

    private String normalizeCachePart(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    @FunctionalInterface
    private interface StationCacheLoader {
        List<StationDO> load();
    }
}
