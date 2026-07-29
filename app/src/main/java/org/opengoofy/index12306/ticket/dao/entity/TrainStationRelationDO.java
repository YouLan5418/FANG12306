package org.opengoofy.index12306.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

import java.util.Date;

/**
 * 列车站点可达关系（出发-到达区间），单表，主键自增。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_train_station_relation")
public class TrainStationRelationDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long trainId;
    private String departure;
    private String arrival;
    private String startRegion;
    private String endRegion;
    private Integer departureFlag;
    private Integer arrivalFlag;
    private Date departureTime;
    private Date arrivalTime;
}