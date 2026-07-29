package org.opengoofy.index12306.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 车厢，单表，主键自增。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_carriage")
public class CarriageDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long trainId;
    private String carriageNumber;
    private Integer carriageType;
    private Integer seatCount;
}