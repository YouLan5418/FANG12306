package org.opengoofy.index12306.customerservice.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.customerservice.embedding.EmbeddingClient;
import org.opengoofy.index12306.customerservice.rerank.RerankClient;
import org.opengoofy.index12306.customerservice.rerank.RetrievedChunk;
import org.opengoofy.index12306.customerservice.rerank.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 检索入口，编排混合检索流程：
 * 查询向量化 -> 向量通道 + 关键词通道检索 -> RRF 融合 -> rerank 精排。
 * 向量通道负责语义匹配，关键词通道负责精确字段匹配（错误码、车次号等）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagRetriever {

    private final EmbeddingClient embeddingClient;
    private final VectorSearchChannel vectorSearchChannel;
    private final KeywordSearchChannel keywordSearchChannel;
    private final RerankClient rerankClient;

    private final RrfFuser rrfFuser = new RrfFuser();

    /** rerank 精排后返回的最大切片数 */
    private static final int RERANK_TOP_N = 3;

    /**
     * 混合检索，返回 rerank 精排后的 top3 切片。
     *
     * @param query     用户查询文本
     * @param categoryId 分类 ID，null 表示不限分类
     * @param topK      每路检索的候选数量（融合前）
     * @return rerank 后的切片列表
     */
    public List<ScoredChunk> retrieve(String query, Long categoryId, int topK) {
        float[] queryVec = null;
        try {
            queryVec = embeddingClient.embed(query);
        } catch (Exception e) {
            log.warn("查询向量化失败，仅走关键词通道: {}", e.getMessage());
        }

        List<RetrievedChunk> vectorHits = List.of();
        if (queryVec != null) {
            try {
                vectorHits = vectorSearchChannel.search(queryVec, query, categoryId, topK);
            } catch (Exception e) {
                log.warn("向量检索失败: {}", e.getMessage());
            }
        }

        List<RetrievedChunk> keywordHits = List.of();
        try {
            keywordHits = keywordSearchChannel.search(null, query, categoryId, topK);
        } catch (Exception e) {
            log.warn("关键词检索失败: {}", e.getMessage());
        }

        List<RetrievedChunk> merged = rrfFuser.fuse(vectorHits, keywordHits, topK);
        if (merged.isEmpty()) {
            return List.of();
        }

        return rerankClient.rerank(query, merged, RERANK_TOP_N);
    }
}