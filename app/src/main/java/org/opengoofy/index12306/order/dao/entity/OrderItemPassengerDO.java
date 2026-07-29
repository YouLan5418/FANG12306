package org.opengoofy.index12306.order.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.config.AESEncryptTypeHandler;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 订单乘车人关系，按 order_sn HASH 分区，主键 (id, order_sn)。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_order_item_passenger", autoResultMap = true)
public class OrderItemPassengerDO extends BaseDO {

    private Long id;
    private String orderSn;
    private Integer idType;

    @TableField(typeHandler = AESEncryptTypeHandler.class)
    private String idCard;
}