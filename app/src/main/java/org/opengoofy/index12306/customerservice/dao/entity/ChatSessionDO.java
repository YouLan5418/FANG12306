package org.opengoofy.index12306.customerservice.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 对话会话，单表，主键自增。本表无 del_flag，故不继承 BaseDO。
 */
@Data
@TableName("chat_session")
public class ChatSessionDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private String username;
    private String title;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}