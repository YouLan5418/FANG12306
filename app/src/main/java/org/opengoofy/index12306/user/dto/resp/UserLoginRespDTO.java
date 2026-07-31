package org.opengoofy.index12306.user.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginRespDTO {

    private String userId;
    private String username;
    private String realName;
    private String accessToken;
    private Integer role;
}