package org.opengoofy.index12306.customerservice.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 知识库分类，单表，主键自增。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_category")
public class KbCategoryDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
}