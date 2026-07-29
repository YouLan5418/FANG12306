package org.opengoofy.index12306.customerservice.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 对话消息，单表，主键自增。
 * role：user/assistant/tool。本表只有 create_time，故不继承 BaseDO。
 */
@Data
@TableName("chat_message")
public class ChatMessageDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private String role;
    private String content;
    private String toolName;

    /**
     * JSONB 原文（依赖 stringtype=unspecified 由 PG 侧转 jsonb）
     */
    private String toolArgs;

    private String toolResult;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}