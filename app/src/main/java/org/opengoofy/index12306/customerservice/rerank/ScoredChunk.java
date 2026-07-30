package org.opengoofy.index12306.customerservice.rerank;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A retrieved chunk with a rerank model's relevance score.
 * Rerank score replaces the original retrieval score for final ordering.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoredChunk {

    private RetrievedChunk chunk;
    private float rerankScore;
}