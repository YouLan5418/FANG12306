package org.opengoofy.index12306.pay.dto.req;

import lombok.Data;

import java.util.List;

/**
 * 退款请求。
 */
@Data
public class RefundReqDTO {

    private String orderSn;
    /** 11 部分退款 12 全部退款 */
    private Integer refundType;
    private Integer refundAmount;
    private List<RefundDetailReqDTO> refundDetailReqDTOList;
}