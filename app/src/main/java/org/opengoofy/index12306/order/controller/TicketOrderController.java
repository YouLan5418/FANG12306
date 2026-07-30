package org.opengoofy.index12306.order.controller;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.opengoofy.index12306.order.dto.req.CancelTicketOrderReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderCreateReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderItemQueryReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderPageQueryReqDTO;
import org.opengoofy.index12306.order.dto.resp.TicketOrderDetailRespDTO;
import org.opengoofy.index12306.order.dto.resp.TicketOrderDetailSelfRespDTO;
import org.opengoofy.index12306.order.dto.resp.TicketOrderPassengerDetailRespDTO;
import org.opengoofy.index12306.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单控制层。建单/关单为内部调用，分页查询需登录。
 */
@RestController
@RequiredArgsConstructor
public class TicketOrderController {

    private final OrderService orderService;

    @GetMapping("/api/order-service/order/ticket/query")
    public Result<TicketOrderDetailRespDTO> queryTicketOrderByOrderSn(@RequestParam("orderSn") String orderSn) {
        return Results.success(orderService.queryTicketOrderByOrderSn(orderSn));
    }

    @GetMapping("/api/order-service/order/item/ticket/query")
    public Result<List<TicketOrderPassengerDetailRespDTO>> queryItemByOrderSnAndIds(@RequestBody TicketOrderItemQueryReqDTO requestParam) {
        return Results.success(orderService.queryItemByOrderSnAndIds(requestParam));
    }

    @GetMapping("/api/order-service/order/ticket/page")
    public Result<org.opengoofy.index12306.framework.convention.page.PageResponse<TicketOrderDetailRespDTO>> pageTicketOrder(TicketOrderPageQueryReqDTO requestParam) {
        return Results.success(orderService.pageTicketOrder(requestParam));
    }

    @GetMapping("/api/order-service/order/ticket/self/page")
    public Result<org.opengoofy.index12306.framework.convention.page.PageResponse<TicketOrderDetailSelfRespDTO>> pageSelfTicketOrder(TicketOrderPageQueryReqDTO requestParam) {
        return Results.success(orderService.pageSelfTicketOrder(requestParam));
    }

    @PostMapping("/api/order-service/order/ticket/create")
    public Result<String> createTicketOrder(@RequestBody TicketOrderCreateReqDTO requestParam) {
        return Results.success(orderService.createTicketOrder(requestParam));
    }

    @PostMapping("/api/order-service/order/ticket/close")
    public Result<Boolean> closeTickOrder(@RequestBody CancelTicketOrderReqDTO requestParam) {
        return Results.success(orderService.closeTickOrder(requestParam));
    }

    @PostMapping("/api/order-service/order/ticket/cancel")
    public Result<Void> cancelTickOrder(@RequestBody CancelTicketOrderReqDTO requestParam) {
        orderService.cancelTickOrder(requestParam);
        return Results.success();
    }
}