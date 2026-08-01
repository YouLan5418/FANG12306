package org.opengoofy.index12306.customerservice.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 知识切片响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkRespDTO {

    private Long id;
    private Long categoryId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private String metadata;
    private Integer embeddingDimension;
    private Date createTime;
}
