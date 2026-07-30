package org.opengoofy.index12306.ticket.service;

import org.opengoofy.index12306.ticket.dao.entity.SeatDO;

import java.util.List;

/**
 * 座位服务：余票统计、可用座位选取、锁座/回滚。
 */
public interface SeatService {

    int countAvailable(Long trainId, Integer seatType, String departure, String arrival);

    List<SeatDO> selectAvailable(Long trainId, Integer seatType, String departure, String arrival, int count);

    /** 按 (trainId, 车厢号, 座位号) 锁座：状态 0->1，返回受影响行数 */
    int lockSeatByPosition(Long trainId, String carriageNumber, String seatNumber);

    /** 批量按位置回滚：状态 1->0 */
    int unlockSeats(List<Long> trainIds, List<String> carriageNumbers, List<String> seatNumbers);
}