package org.opengoofy.index12306.customerservice.rag;

import org.opengoofy.index12306.customerservice.rerank.RetrievedChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF（Reciprocal Rank Fusion）融合器。
 * 将向量通道和关键词通道的检索结果按排名融合：
 * score = 1/(k + rank_v) + 1/(k + rank_k)，k 默认 60。
 * 仅依赖排名不依赖原始分数，天然解决两通道分值不可比的问题。
 */
public class RrfFuser {

    private static final int DEFAULT_K = 60;

    private final int k;

    public RrfFuser() {
        this(DEFAULT_K);
    }

    public RrfFuser(int k) {
        this.k = k;
    }

    /**
     * 融合两路检索结果，按 RRF 分数降序排列并截取 topK。
     *
     * @param vectorHits  向量通道结果（按相似度降序）
     * @param keywordHits 关键词通道结果（按相关度降序）
     * @param topK        融合后最大返回数量
     * @return 按 RRF 分数降序排列的切片，score 已替换为 RRF 分数
     */
    public List<RetrievedChunk> fuse(List<RetrievedChunk> vectorHits, List<RetrievedChunk> keywordHits, int topK) {
        Map<Long, RetrievedChunk> chunkById = new HashMap<>();
        Map<Long, Float> rrfScore = new HashMap<>();

        addRankScores(vectorHits, chunkById, rrfScore);
        addRankScores(keywordHits, chunkById, rrfScore);

        List<RetrievedChunk> merged = new ArrayList<>(chunkById.values());
        merged.sort((a, b) -> Float.compare(rrfScore.get(b.getChunkId()), rrfScore.get(a.getChunkId())));

        List<RetrievedChunk> result = new ArrayList<>(Math.min(topK, merged.size()));
        for (RetrievedChunk chunk : merged) {
            if (result.size() >= topK) {
                break;
            }
            result.add(RetrievedChunk.builder()
                    .chunkId(chunk.getChunkId())
                    .documentId(chunk.getDocumentId())
                    .categoryId(chunk.getCategoryId())
                    .chunkIndex(chunk.getChunkIndex())
                    .content(chunk.getContent())
                    .score(rrfScore.get(chunk.getChunkId()))
                    .source("fused")
                    .build());
        }
        return result;
    }

    private void addRankScores(List<RetrievedChunk> hits, Map<Long, RetrievedChunk> chunkById, Map<Long, Float> rrfScore) {
        if (hits == null) {
            return;
        }
        for (int rank = 0; rank < hits.size(); rank++) {
            RetrievedChunk chunk = hits.get(rank);
            chunkById.putIfAbsent(chunk.getChunkId(), chunk);
            float contribution = 1.0f / (k + rank + 1);
            rrfScore.merge(chunk.getChunkId(), contribution, Float::sum);
        }
    }
}