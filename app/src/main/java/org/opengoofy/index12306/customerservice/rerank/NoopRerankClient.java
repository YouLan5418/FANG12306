package org.opengoofy.index12306.customerservice.rerank;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 空操作重排序客户端，按原始检索分数排序返回。
 * 用于未配置重排序 API 时的兜底，或测试场景。
 */
@Component
@ConditionalOnProperty(prefix = "ai.rerank", name = "enabled", havingValue = "false")
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
