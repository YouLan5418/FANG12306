package org.opengoofy.index12306.ticket.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
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
        return Results.success(stationMapper.selectList(null));
    }

    /** 按区域或名称查询车站 */
    @GetMapping("/api/ticket-service/region-station/query")
    public Result<List<StationDO>> listRegionStations(
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "name", required = false) String name) {
        LambdaQueryWrapper<StationDO> wrapper = new LambdaQueryWrapper<>();
        if (region != null && !region.isBlank()) {
            wrapper.eq(StationDO::getRegion, region);
        }
        if (name != null && !name.isBlank()) {
            wrapper.like(StationDO::getName, name);
        }
        return Results.success(stationMapper.selectList(wrapper));
    }

    /** 按车次查询经停站明细 */
    @GetMapping("/api/ticket-service/train-station/query")
    public Result<List<TrainStationDO>> listTrainStations(@RequestParam("trainId") Long trainId) {
        return Results.success(trainStationMapper.selectList(
                new LambdaQueryWrapper<TrainStationDO>()
                        .eq(TrainStationDO::getTrainId, trainId)
                        .orderByAsc(TrainStationDO::getId)));
    }
}