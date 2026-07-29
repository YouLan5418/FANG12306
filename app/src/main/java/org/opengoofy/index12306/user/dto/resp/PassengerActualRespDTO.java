package org.opengoofy.index12306.user.dto.resp;

import lombok.Data;

/**
 * 乘车人查询响应（内部使用，含敏感原文）。
 */
@Data
public class PassengerActualRespDTO {

    private String id;
    private String realName;
    private Integer idType;
    private String idCard;
    private Integer discountType;
    private String phone;
    private Integer verifyStatus;
}