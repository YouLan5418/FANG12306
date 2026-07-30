package org.opengoofy.index12306.ticket.common.constant;

/**
 * 票务域缓存与锁常量。
 */
public final class TicketCacheConstant {

    /** 区间余票/令牌桶 Hash，后缀 trainId_departure_arrival，field=seatType */
    public static final String TRAIN_STATION_REMAINING_TICKET = "index12306:ticket:train-station-remaining:";

    /** 购票分布式公平锁，后缀 trainId */
    public static final String LOCK_PURCHASE_TICKETS = "index12306:ticket:lock:purchase-tickets:";

    private TicketCacheConstant() {
    }
}