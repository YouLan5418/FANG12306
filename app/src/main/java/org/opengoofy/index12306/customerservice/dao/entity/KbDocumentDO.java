package org.opengoofy.index12306.customerservice.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengoofy.index12306.common.entity.BaseDO;

/**
 * 知识库文档，单表，主键自增。
 * status：0待处理 1处理中 2完成 3失败
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document")
public class KbDocumentDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String fileName;
    private String filePath;
    private String fileType;
    private String parserType;
    private String chunkMode;
    private Integer status;
    private Integer chunkCount;
}