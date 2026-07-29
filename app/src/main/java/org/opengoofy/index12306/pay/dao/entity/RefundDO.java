package org.opengoofy.index12306.pay.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.config.AESEncryptTypeHandler;
import org.opengoofy.index12306.common.entity.BaseDO;

import java.time.LocalDate;
import java.util.Date;

/**
 * 退款记录，按 order_sn HASH 分区，主键 (id, order_sn)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_refund", autoResultMap = true)
public class RefundDO extends BaseDO {

    private Long id;
    private String paySn;
    private String orderSn;
    private String tradeNo;
    private Integer amount;
    private Long userId;
    private String username;
    private Long trainId;
    private String trainNumber;
    private LocalDate ridingDate;
    private String departure;
    private String arrival;
    private Date departureTime;
    private Date arrivalTime;
    private Integer seatType;
    private Integer idType;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String idCard;

    private String realName;
    private Integer status;
    private Date refundTime;
}