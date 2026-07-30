package org.opengoofy.index12306.ticket.dto.resp;

import lombok.Data;

/**
 * 选座结果（单个乘车人）。
 */
@Data
public class TrainPurchaseTicketRespDTO {

    private String passengerId;
    private String carriageNumber;
    private String seatNumber;
    private Integer seatType;
    private Integer amount;
    private Integer idType;
    private String idCard;
    private String phone;
    private String realName;
    /** 票种：0成人 1儿童 2学生 */
    private Integer ticketType;
}