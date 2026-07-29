package org.opengoofy.index12306.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 注销后可重用的 username 记录，单表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_reuse")
public class UserReuseDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
}
