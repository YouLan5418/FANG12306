package org.opengoofy.index12306.order.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import org.opengoofy.index12306.user.serialize.IdCardDesensitizationSerializer;
import org.opengoofy.index12306.user.serialize.PhoneDesensitizationSerializer;

/**
 * 订单乘车人明细响应（证件号/手机号脱敏）。
 */
@Data
public class TicketOrderPassengerDetailRespDTO {

    private String seatNumber;
    private String carriageNumber;
    private Integer seatType;
    private String realName;
    private Integer idType;

    @JsonSerialize(using = IdCardDesensitizationSerializer.class)
    private String idCard;

    @JsonSerialize(using = PhoneDesensitizationSerializer.class)
    private String phone;

    private Integer amount;
    private Integer ticketType;
    private Integer status;
}