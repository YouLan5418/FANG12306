package org.opengoofy.index12306.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 地区，单表，主键自增。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_region")
public class RegionDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String fullName;
    private String code;
    private String initial;
    private String spell;
    private Integer popularFlag;
}