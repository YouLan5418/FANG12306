package org.opengoofy.index12306.customerservice.dto.resp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KnowledgeSearchRespDTO {

    private Long chunkId;

    private Long categoryId;

    private Long documentId;

    private Integer chunkIndex;

    private String content;

    private Float score;

    private String source;

    private String metadata;
}
