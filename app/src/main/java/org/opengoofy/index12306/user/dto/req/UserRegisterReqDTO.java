package org.opengoofy.index12306.user.dto.req;

import lombok.Data;

/**
 * 用户注册请求。
 */
@Data
public class UserRegisterReqDTO {

    private String username;
    private String password;
    private String realName;
    private Integer idType;
    private String idCard;
    private String phone;
    private String mail;
    private String region;
}