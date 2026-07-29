package org.opengoofy.index12306.pay.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

import java.util.Date;

/**
 * 支付单，按 order_sn HASH 分区，主键 (id, order_sn)。
 * status：TRADE_SUCCESS / TRADE_CLOSED 等支付渠道状态
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_pay")
public class PayDO extends BaseDO {

    private Long id;
    private String paySn;
    private String orderSn;
    private String outOrderSn;
    private String channel;
    private String tradeType;
    private String subject;
    private String orderRequestId;
    private Integer totalAmount;
    private String tradeNo;
    private Date gmtPayment;
    private Integer payAmount;
    private String status;
}