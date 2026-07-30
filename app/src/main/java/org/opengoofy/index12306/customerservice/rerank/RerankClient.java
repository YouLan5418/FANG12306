package org.opengoofy.index12306.customerservice.rerank;

import java.util.List;

/**
 * Rerank client abstraction for reordering retrieved chunks by relevance to the query.
 * Implementations must degrade gracefully: on failure, return original candidates
 * limited to topN (do not throw, do not break the RAG pipeline).
 */
public interface RerankClient {

    /**
     * Rerank candidate chunks by relevance to the query.
     *
     * @param query     user query text
     * @param candidates retrieved chunks from hybrid search
     * @param topN      max results to return
     * @return reranked chunks sorted by relevance (descending), limited to topN
     */
    List<ScoredChunk> rerank(String query, List<RetrievedChunk> candidates, int topN);
}