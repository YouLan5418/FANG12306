package org.opengoofy.index12306.ticket.service;

import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.ticket.dto.req.PurchaseTicketPassengerDetailDTO;
import org.opengoofy.index12306.ticket.dto.req.PurchaseTicketReqDTO;
import org.opengoofy.index12306.ticket.dto.resp.TrainPurchaseTicketRespDTO;
import org.opengoofy.index12306.ticket.service.handler.IPurchaseTicket;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 seatType 分组，将乘车人分发到对应策略选座，并回填乘车人信息。
 */
@Slf4j
@Component
public class TrainSeatTypeSelector {

    private final Map<Integer, IPurchaseTicket> handlerMap;

    public TrainSeatTypeSelector(List<IPurchaseTicket> handlers) {
        this.handlerMap = new LinkedHashMap<>();
        handlers.forEach(h -> handlerMap.put(h.seatType(), h));
    }

    public List<TrainPurchaseTicketRespDTO> select(PurchaseTicketReqDTO requestParam,
                                                   Map<String, PassengerActualInfo> passengerInfoMap) {
        // 按 seatType 分组，保留每组乘客 id 顺序
        Map<Integer, List<String>> seatTypePassengers = new LinkedHashMap<>();
        for (PurchaseTicketPassengerDetailDTO p : requestParam.getPassengers()) {
            seatTypePassengers.computeIfAbsent(p.getSeatType(), k -> new ArrayList<>()).add(p.getPassengerId());
        }
        List<TrainPurchaseTicketRespDTO> all = new ArrayList<>();
        seatTypePassengers.forEach((seatType, passengerIds) -> {
            IPurchaseTicket handler = handlerMap.get(seatType);
            if (handler == null) {
                throw new IllegalArgumentException("不支持的座位类型: " + seatType);
            }
            List<TrainPurchaseTicketRespDTO> seats = handler.selectSeats(requestParam, passengerIds.size());
            for (int i = 0; i < seats.size(); i++) {
                TrainPurchaseTicketRespDTO seat = seats.get(i);
                seat.setPassengerId(passengerIds.get(i));
                PassengerActualInfo info = passengerInfoMap.get(passengerIds.get(i));
                if (info != null) {
                    seat.setRealName(info.realName);
                    seat.setIdType(info.idType);
                    seat.setIdCard(info.idCard);
                    seat.setPhone(info.phone);
                }
                all.add(seat);
            }
        });
        return all;
    }

    /** 乘车人实际信息载体（避免依赖 user 模块 DTO 类型） */
    public record PassengerActualInfo(String realName, Integer idType, String idCard, String phone) {
    }
}