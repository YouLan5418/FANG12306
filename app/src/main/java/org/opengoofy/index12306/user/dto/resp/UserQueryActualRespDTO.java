package org.opengoofy.index12306.user.dto.resp;

import lombok.Data;

/**
 * 用户查询响应（内部使用，含敏感原文）。
 */
@Data
public class UserQueryActualRespDTO {

    private String id;
    private String username;
    private String realName;
    private Integer idType;
    private String idCard;
    private String phone;
    private String mail;
    private String address;
}