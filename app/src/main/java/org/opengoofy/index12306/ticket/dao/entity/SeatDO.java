package org.opengoofy.index12306.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 座位，按 train_id HASH 分区，主键 (id, train_id)。
 * seatStatus：0可用 1锁定 2已售
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_seat")
public class SeatDO extends BaseDO {

    private Long id;
    private Long trainId;
    private String carriageNumber;
    private String seatNumber;
    private Integer seatType;
    private String startStation;
    private String endStation;
    private Integer price;
    private Integer seatStatus;
}