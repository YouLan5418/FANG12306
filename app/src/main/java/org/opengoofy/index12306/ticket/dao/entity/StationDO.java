package org.opengoofy.index12306.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 车站，单表，主键自增。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_station")
public class StationDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String spell;
    private String region;
    private String regionName;
}