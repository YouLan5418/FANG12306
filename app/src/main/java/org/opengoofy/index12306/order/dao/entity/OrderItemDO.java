package org.opengoofy.index12306.order.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.config.AESEncryptTypeHandler;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 订单明细，按 order_sn HASH 分区（与订单同分区），主键 (id, order_sn)。
 * status：0待支付 10已支付 20已进站 30已取消 40已退票 50已改签
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_order_item", autoResultMap = true)
public class OrderItemDO extends BaseDO {

    private Long id;
    private String orderSn;
    private Long userId;
    private String username;
    private Long trainId;
    private String carriageNumber;
    private Integer seatType;
    private String seatNumber;
    private String realName;
    private Integer idType;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String idCard;

    private Integer ticketType;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String phone;

    private Integer status;
    private Integer amount;
}