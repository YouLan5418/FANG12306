package org.opengoofy.index12306.ticket.service.handler;

import org.opengoofy.index12306.ticket.dto.req.PurchaseTicketReqDTO;
import org.opengoofy.index12306.ticket.dto.resp.TrainPurchaseTicketRespDTO;

import java.util.List;

/**
 * 选座策略接口（策略模式：商务/一等/二等）。
 */
public interface IPurchaseTicket {

    /**
     * 返回本策略处理的 seatType（0商务 1一等 2二等）
     */
    Integer seatType();

    /**
     * 为指定乘车人选取座位。
     *
     * @param requestParam 购票请求
     * @param count        本 seatType 的乘车人数
     */
    List<TrainPurchaseTicketRespDTO> selectSeats(PurchaseTicketReqDTO requestParam, int count);
}