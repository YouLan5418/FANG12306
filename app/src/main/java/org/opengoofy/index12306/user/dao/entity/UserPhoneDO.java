package org.opengoofy.index12306.user.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 手机号反查 username，按 phone HASH 分区，主键 (id, phone)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_phone")
public class UserPhoneDO extends BaseDO {

    private Long id;
    private String username;
    private String phone;
    private Long deletionTime;
}
