package org.opengoofy.index12306.customerservice.rerank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 从知识库检索到的切片（向量或关键词通道）。
 * 携带检索元数据供重排序阶段使用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrievedChunk {

    private Long chunkId;
    private Long documentId;
    private Long categoryId;
    private Integer chunkIndex;
    private String content;

    /** 来源通道的检索分数（余弦相似度或关键词相关度） */
    private float score;

    /** 检索通道来源："vector" 或 "keyword" */
    private String source;
}