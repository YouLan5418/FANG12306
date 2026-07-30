package org.opengoofy.index12306.customerservice.rag;

import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.customerservice.rerank.RetrievedChunk;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RrfFuser 单元测试：RRF 融合逻辑、排名计算、topK 截断。
 */
class RrfFuserTests {

    private RetrievedChunk chunk(long id, float score, String source) {
        return RetrievedChunk.builder()
                .chunkId(id)
                .content("chunk-" + id)
                .score(score)
                .source(source)
                .build();
    }

    @Test
    void fuse_bothChannelsOverlap_overlapRanksHighest() {
        // 向量通道：chunk 1 第1，chunk 2 第2
        List<RetrievedChunk> vectorHits = List.of(chunk(1, 0.9f, "vector"), chunk(2, 0.8f, "vector"));
        // 关键词通道：chunk 2 第1，chunk 3 第2
        List<RetrievedChunk> keywordHits = List.of(chunk(2, 1.0f, "keyword"), chunk(3, 1.0f, "keyword"));

        List<RetrievedChunk> merged = new RrfFuser(60).fuse(vectorHits, keywordHits, 10);

        // chunk 2 两路都命中，RRF 分数最高
        assertEquals(3, merged.size());
        assertEquals(2L, merged.get(0).getChunkId());
        float expected = 1.0f / 62 + 1.0f / 61;
        assertEquals(expected, merged.get(0).getScore(), 0.0001f);
        assertEquals("fused", merged.get(0).getSource());
    }

    @Test
    void fuse_singleChannelOnly_correctRankScore() {
        List<RetrievedChunk> vectorHits = List.of(chunk(1, 0.9f, "vector"), chunk(2, 0.8f, "vector"));
        List<RetrievedChunk> merged = new RrfFuser().fuse(vectorHits, List.of(), 10);

        assertEquals(2, merged.size());
        assertEquals(1L, merged.get(0).getChunkId());
        // rank 1: 1/(60+1) = 1/61
        assertEquals(1.0f / 61, merged.get(0).getScore(), 0.0001f);
    }

    @Test
    void fuse_emptyInputs_returnsEmpty() {
        assertTrue(new RrfFuser().fuse(List.of(), List.of(), 10).isEmpty());
        assertTrue(new RrfFuser().fuse(null, null, 10).isEmpty());
    }

    @Test
    void fuse_topKTruncates() {
        List<RetrievedChunk> vectorHits = List.of(
                chunk(1, 0.9f, "vector"), chunk(2, 0.8f, "vector"), chunk(3, 0.7f, "vector"));
        List<RetrievedChunk> merged = new RrfFuser().fuse(vectorHits, List.of(), 2);
        assertEquals(2, merged.size());
    }

    @Test
    void fuse_orderByRrfScoreDescending() {
        // 向量通道：chunk 1 第1，chunk 2 第2，chunk 3 第3
        List<RetrievedChunk> vectorHits = List.of(
                chunk(1, 0.9f, "vector"), chunk(2, 0.8f, "vector"), chunk(3, 0.7f, "vector"));
        // 关键词通道：chunk 3 第1
        List<RetrievedChunk> keywordHits = List.of(chunk(3, 1.0f, "keyword"));

        List<RetrievedChunk> merged = new RrfFuser().fuse(vectorHits, keywordHits, 10);

        // chunk 1: 1/61 ≈ 0.01639
        // chunk 3: 1/63 + 1/61 ≈ 0.01587 + 0.01639 = 0.03226
        // chunk 2: 1/62 ≈ 0.01613
        // 排序：chunk 3 > chunk 1 > chunk 2
        assertEquals(3L, merged.get(0).getChunkId());
        assertEquals(1L, merged.get(1).getChunkId());
        assertEquals(2L, merged.get(2).getChunkId());
    }
}