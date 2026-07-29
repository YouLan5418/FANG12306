package org.opengoofy.index12306.user.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 邮箱反查 username，按 mail HASH 分区，主键 (id, mail)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_mail")
public class UserMailDO extends BaseDO {

    private Long id;
    private String username;
    private String mail;
    private Long deletionTime;
}
