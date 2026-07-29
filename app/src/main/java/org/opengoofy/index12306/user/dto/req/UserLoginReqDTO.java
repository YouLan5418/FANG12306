package org.opengoofy.index12306.user.dto.req;

import lombok.Data;

/**
 * 用户登录请求，usernameOrMailOrPhone 支持用户名/邮箱/手机号三种方式。
 */
@Data
public class UserLoginReqDTO {

    private String usernameOrMailOrPhone;
    private String password;
}