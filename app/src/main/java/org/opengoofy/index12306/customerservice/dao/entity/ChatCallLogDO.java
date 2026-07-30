package org.opengoofy.index12306.customerservice.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 客服调用日志，单表，主键自增。
 * 每轮 FC 迭代异步写一行，覆盖迭代轮次、工具调用成功率、每轮耗时。
 */
@Data
@TableName("chat_call_log")
public class ChatCallLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private Integer iteration;
    private String toolName;
    private String toolArgs;
    private String toolResult;
    private Long latencyMs;
    private Integer tokenEstimate;
    private String status;
    private String error;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
