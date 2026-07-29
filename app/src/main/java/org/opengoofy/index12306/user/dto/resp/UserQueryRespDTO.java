package org.opengoofy.index12306.user.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import org.opengoofy.index12306.user.serialize.PhoneDesensitizationSerializer;

/**
 * 用户查询响应（敏感字段脱敏）。
 */
@Data
public class UserQueryRespDTO {

    private String id;
    private String username;
    private String realName;
    private String region;
    private Integer idType;

    @JsonSerialize(using = PhoneDesensitizationSerializer.class)
    private String phone;

    private String mail;
    private Integer userType;
    private Integer verifyStatus;
    private String postCode;
}