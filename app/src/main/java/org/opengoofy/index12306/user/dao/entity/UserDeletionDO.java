package org.opengoofy.index12306.user.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;
import org.opengoofy.index12306.common.config.AESEncryptTypeHandler;

/**
 * 用户注销记录，单表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_user_deletion", autoResultMap = true)
public class UserDeletionDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer idType;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String idCard;
}
