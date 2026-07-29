package org.opengoofy.index12306.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

import java.util.Date;

/**
 * 列车车次，单表，主键自增。
 * trainType：0高铁 1动车 2普通车
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_train")
public class TrainDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String trainNumber;
    private Integer trainType;
    private String trainTag;
    private String trainBrand;
    private String startStation;
    private String endStation;
    private String startRegion;
    private String endRegion;
    private Date saleTime;
    private Integer saleStatus;
    private Date departureTime;
    private Date arrivalTime;
}