package org.opengoofy.index12306.customerservice.rerank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A chunk retrieved from the knowledge base (vector or keyword channel).
 * Carries retrieval metadata for the rerank stage.
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

    /** Retrieval score from the source channel (cosine similarity or keyword relevance). */
    private float score;

    /** Which retrieval channel produced this chunk: "vector" or "keyword". */
    private String source;
}