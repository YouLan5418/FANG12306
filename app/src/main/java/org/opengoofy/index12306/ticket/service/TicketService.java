package org.opengoofy.index12306.ticket.service;

import org.opengoofy.index12306.ticket.dto.req.CancelTicketReqDTO;
import org.opengoofy.index12306.ticket.dto.req.PurchaseTicketReqDTO;
import org.opengoofy.index12306.ticket.dto.req.TicketPageQueryReqDTO;
import org.opengoofy.index12306.ticket.dto.resp.TicketPageQueryRespDTO;
import org.opengoofy.index12306.ticket.dto.resp.TicketPurchaseRespDTO;

/**
 * 票务服务：车票查询、购票下单、取消回滚。
 */
public interface TicketService {

    TicketPageQueryRespDTO pageListTicketQuery(TicketPageQueryReqDTO requestParam);

    TicketPurchaseRespDTO purchaseTickets(PurchaseTicketReqDTO requestParam);

    void cancelTicketOrder(CancelTicketReqDTO requestParam);

    /**
     * 关闭未支付订单并回滚座位/余票（MQ 延迟关单与主动取消共用）。
     */
    void closeOrderAndRollback(String orderSn);

    /**
     * 订单关闭后回滚座位与余票（由 MQ 延迟关单消费或主动取消触发）。
     */
    void rollbackOnOrderClose(String orderSn);
}