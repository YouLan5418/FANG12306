package org.opengoofy.index12306.common.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfoDTO {

    private String userId;
    private String username;
    private String realName;
    private String token;
    private Integer role;
}
