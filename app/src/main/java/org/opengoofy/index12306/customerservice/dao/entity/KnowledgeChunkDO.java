package org.opengoofy.index12306.customerservice.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.opengoofy.index12306.common.config.VectorTypeHandler;

import java.util.Date;
import java.util.List;

/**
 * 向量切片（pgvector），单表，主键自增。
 * 本表只有 create_time，无 update_time/del_flag，故不继承 BaseDO。
 */
@Data
@TableName(value = "knowledge_chunk", autoResultMap = true)
public class KnowledgeChunkDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;

    @TableField(typeHandler = VectorTypeHandler.class)
    private List<Float> embedding;

    /**
     * JSONB 原文（依赖 stringtype=unspecified 由 PG 侧转 jsonb）
     */
    private String metadata;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}