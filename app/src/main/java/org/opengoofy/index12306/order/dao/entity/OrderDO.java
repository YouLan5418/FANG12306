package org.opengoofy.index12306.order.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

import java.time.LocalDate;
import java.util.Date;

/**
 * 订单主表，按 order_sn HASH 分区，主键 (id, order_sn)。
 * status：0待支付 10已支付 11部分退款 12全部退款 20已完成 30已取消
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_order")
public class OrderDO extends BaseDO {

    private Long id;
    private String orderSn;
    private Long userId;
    private String username;
    private Long trainId;
    private String trainNumber;
    private LocalDate ridingDate;
    private String departure;
    private String arrival;
    private Date departureTime;
    private Date arrivalTime;
    private Integer source;
    private Integer status;
    private Date orderTime;
    private Integer payType;
    private Date payTime;
}