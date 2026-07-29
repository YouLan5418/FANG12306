package org.opengoofy.index12306.ticket.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 车票，按 username HASH 分区，主键 (id, username)。
 * ticketStatus：0未支付 1已支付
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ticket")
public class TicketDO extends BaseDO {

    private Long id;
    private String username;
    private Long trainId;
    private String carriageNumber;
    private String seatNumber;
    private Long passengerId;
    private Integer ticketStatus;
}