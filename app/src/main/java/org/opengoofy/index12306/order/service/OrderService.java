package org.opengoofy.index12306.order.service;

import org.opengoofy.index12306.framework.convention.page.PageResponse;
import org.opengoofy.index12306.order.dto.domain.OrderStatusReversalDTO;
import org.opengoofy.index12306.order.dto.req.CancelTicketOrderReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderCreateReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderItemQueryReqDTO;
import org.opengoofy.index12306.order.dto.req.TicketOrderPageQueryReqDTO;
import org.opengoofy.index12306.order.dto.resp.TicketOrderDetailRespDTO;
import org.opengoofy.index12306.order.dto.resp.TicketOrderDetailSelfRespDTO;
import org.opengoofy.index12306.order.dto.resp.TicketOrderPassengerDetailRespDTO;
import org.opengoofy.index12306.order.mq.event.PayResultCallbackOrderEvent;

import java.util.List;

/**
 * 订单服务。
 */
public interface OrderService {

    TicketOrderDetailRespDTO queryTicketOrderByOrderSn(String orderSn);

    PageResponse<TicketOrderDetailRespDTO> pageTicketOrder(TicketOrderPageQueryReqDTO requestParam);

    PageResponse<TicketOrderDetailSelfRespDTO> pageSelfTicketOrder(TicketOrderPageQueryReqDTO requestParam);

    String createTicketOrder(TicketOrderCreateReqDTO requestParam);

    boolean closeTickOrder(CancelTicketOrderReqDTO requestParam);

    void cancelTickOrder(CancelTicketOrderReqDTO requestParam);

    void statusReversal(OrderStatusReversalDTO requestParam);

    void payCallbackOrder(PayResultCallbackOrderEvent requestParam);

    List<TicketOrderPassengerDetailRespDTO> queryItemByOrderSnAndIds(TicketOrderItemQueryReqDTO requestParam);
}