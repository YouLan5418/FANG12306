package org.opengoofy.index12306.user.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;
import org.opengoofy.index12306.common.config.AESEncryptTypeHandler;

import java.util.Date;

/**
 * 乘车人，按 username HASH 分区，主键 (id, username)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_passenger", autoResultMap = true)
public class PassengerDO extends BaseDO {

    private Long id;
    private String username;
    private String realName;
    private Integer idType;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String idCard;

    private Integer discountType;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String phone;

    private Date createDate;
    private Integer verifyStatus;
}
