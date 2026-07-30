package org.opengoofy.index12306.customerservice.rag;

import lombok.Data;

/**
 * 知识切片检索结果 DTO，承载向量/关键词检索返回的数据库行。
 * score 为检索通道给出的相关度分数。
 */
@Data
public class ChunkSearchResult {

    private Long id;
    private Long categoryId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private String metadata;
    private Double score;
}