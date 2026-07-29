package org.opengoofy.index12306.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 列车区间票价，单表，主键自增。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_train_station_price")
public class TrainStationPriceDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long trainId;
    private String departure;
    private String arrival;
    private Integer seatType;
    private Integer price;
}