package org.opengoofy.index12306.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.convention.page.PageResponse;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.opengoofy.index12306.order.dao.entity.OrderDO;
import org.opengoofy.index12306.order.dao.mapper.OrderMapper;
import org.opengoofy.index12306.order.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理员订单管理控制器。
 */
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    @GetMapping("/list")
    public Result<PageResponse<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        var wrapper = Wrappers.lambdaQuery(OrderDO.class);
        if (status != null) {
            wrapper.eq(OrderDO::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(OrderDO::getOrderSn, keyword)
                    .or().like(OrderDO::getUsername, keyword)
                    .or().like(OrderDO::getTrainNumber, keyword));
        }
        wrapper.orderByDesc(OrderDO::getOrderTime);
        IPage<OrderDO> result = orderMapper.selectPage(Page.of(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("orderSn", o.getOrderSn());
            m.put("userId", o.getUserId());
            m.put("username", o.getUsername());
            m.put("trainNumber", o.getTrainNumber());
            m.put("departure", o.getDeparture());
            m.put("arrival", o.getArrival());
            m.put("ridingDate", o.getRidingDate());
            m.put("departureTime", o.getDepartureTime());
            m.put("arrivalTime", o.getArrivalTime());
            m.put("status", o.getStatus());
            m.put("orderTime", o.getOrderTime());
            m.put("payTime", o.getPayTime());
            return m;
        }).toList();
        PageResponse<Map<String, Object>> resp = new PageResponse<>(page, size, result.getTotal());
        resp.setRecords(records);
        return Results.success(resp);
    }

    @PostMapping("/{orderSn}/cancel")
    public Result<Void> cancel(@PathVariable String orderSn) {
        orderService.closeTickOrder(new org.opengoofy.index12306.order.dto.req.CancelTicketOrderReqDTO() {{
            setOrderSn(orderSn);
        }});
        return Results.success();
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        long total = orderMapper.selectCount(null);
        List<OrderDO> all = orderMapper.selectList(null);
        Map<Integer, Long> byStatus = new LinkedHashMap<>();
        for (OrderDO o : all) {
            byStatus.merge(o.getStatus(), 1L, Long::sum);
        }
        long todayStart = java.time.LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        long todayOrderCount = all.stream()
                .filter(o -> o.getOrderTime() != null && o.getOrderTime().getTime() >= todayStart)
                .count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("byStatus", byStatus);
        m.put("todayOrderCount", todayOrderCount);
        return Results.success(m);
    }
}
