package org.opengoofy.index12306.user.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;
import org.opengoofy.index12306.common.config.AESEncryptTypeHandler;

/**
 * 用户主表，按 username HASH 分区，主键 (id, username)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_user", autoResultMap = true)
public class UserDO extends BaseDO {

    private Long id;
    private String username;
    private String password;
    private String realName;
    private String region;
    private Integer idType;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String idCard;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String phone;

    private String telephone;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String mail;

    private Integer userType;
    private Integer verifyStatus;
    private String postCode;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String address;

    private Long deletionTime;
}
