package org.opengoofy.index12306.customerservice.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 知识库入库任务，单表，主键自增。
 * status：0待处理 1解析 2切片 3向量化 4完成 5失败
 * 本表无 del_flag，故不继承 BaseDO。
 */
@Data
@TableName("ingestion_task")
public class IngestionTaskDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer status;
    private Integer totalChunks;
    private Integer processedChunks;
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}