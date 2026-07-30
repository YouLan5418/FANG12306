package org.opengoofy.index12306.customerservice.rerank;

import java.util.Comparator;
import java.util.List;

/**
 * No-op rerank client that returns candidates in original retrieval-score order.
 * Used as fallback when no rerank API is configured, or for testing.
 */
public class NoopRerankClient implements RerankClient {

    @Override
    public List<ScoredChunk> rerank(String query, List<RetrievedChunk> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .sorted(Comparator.comparingDouble((RetrievedChunk c) -> c.getScore()).reversed())
                .limit(topN)
                .map(c -> new ScoredChunk(c, c.getScore()))
                .toList();
    }
}