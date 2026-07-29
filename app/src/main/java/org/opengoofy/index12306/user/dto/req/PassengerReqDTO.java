package org.opengoofy.index12306.user.dto.req;

import lombok.Data;

/**
 * 乘车人新增/修改请求。
 */
@Data
public class PassengerReqDTO {

    /** 修改时必传 */
    private String id;
    private String realName;
    private Integer idType;
    private String idCard;
    private Integer discountType;
    private String phone;
}