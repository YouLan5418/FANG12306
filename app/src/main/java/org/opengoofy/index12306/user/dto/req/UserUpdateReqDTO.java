package org.opengoofy.index12306.user.dto.req;

import lombok.Data;

/**
 * 用户信息更新请求。
 */
@Data
public class UserUpdateReqDTO {

    private String username;
    private String realName;
    private String phone;
    private String mail;
    private String region;
    private String postCode;
    private String address;
}