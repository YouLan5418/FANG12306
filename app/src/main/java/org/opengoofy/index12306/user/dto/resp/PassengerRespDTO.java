package org.opengoofy.index12306.user.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import org.opengoofy.index12306.user.serialize.IdCardDesensitizationSerializer;
import org.opengoofy.index12306.user.serialize.PhoneDesensitizationSerializer;

import java.util.Date;

/**
 * 乘车人查询响应（证件号/手机号脱敏）。
 */
@Data
public class PassengerRespDTO {

    private String id;
    private String realName;
    private Integer idType;

    @JsonSerialize(using = IdCardDesensitizationSerializer.class)
    private String idCard;

    private Integer discountType;

    @JsonSerialize(using = PhoneDesensitizationSerializer.class)
    private String phone;

    private Date createDate;
    private Integer verifyStatus;
}