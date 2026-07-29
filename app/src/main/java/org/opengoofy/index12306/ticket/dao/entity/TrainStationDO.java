package org.opengoofy.index12306.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

import java.util.Date;

/**
 * 列车经停站明细，单表，主键自增。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_train_station")
public class TrainStationDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long trainId;
    private Long stationId;
    private String sequence;
    private String departure;
    private String arrival;
    private String startRegion;
    private String endRegion;
    private Date arrivalTime;
    private Date departureTime;
    private Integer stopoverTime;
}