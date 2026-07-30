package org.opengoofy.index12306.customerservice.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.customerservice.embedding.EmbeddingClient;
import org.opengoofy.index12306.customerservice.rerank.NoopRerankClient;
import org.opengoofy.index12306.customerservice.rerank.RetrievedChunk;
import org.opengoofy.index12306.customerservice.rerank.ScoredChunk;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RagRetriever 单元测试：混合检索编排、向量化降级、rerank 截断。
 */
class RagRetrieverTests {

    private EmbeddingClient embeddingClient;
    private VectorSearchChannel vectorChannel;
    private KeywordSearchChannel keywordChannel;
    private RagRetriever retriever;

    @BeforeEach
    void setUp() {
        embeddingClient = mock(EmbeddingClient.class);
        vectorChannel = mock(VectorSearchChannel.class);
        keywordChannel = mock(KeywordSearchChannel.class);
        retriever = new RagRetriever(embeddingClient, vectorChannel, keywordChannel, new NoopRerankClient());
    }

    private RetrievedChunk chunk(long id, float score) {
        return RetrievedChunk.builder().chunkId(id).content("c" + id).score(score).source("vector").build();
    }

    @Test
    void retrieve_bothChannels_overlapChunkRanksFirst() {
        when(embeddingClient.embed("query")).thenReturn(new float[]{0.1f, 0.2f});
        when(vectorChannel.search(any(), any(), any(), anyInt()))
                .thenReturn(List.of(chunk(1, 0.9f), chunk(2, 0.8f)));
        when(keywordChannel.search(any(), any(), any(), anyInt()))
                .thenReturn(List.of(chunk(2, 1.0f), chunk(3, 1.0f)));

        List<ScoredChunk> results = retriever.retrieve("query", null, 5);

        assertEquals(3, results.size());
        // chunk 2 两路命中，RRF 分数最高，NoopRerank 按 RRF 分数排序
        assertEquals(2L, results.get(0).getChunk().getChunkId());
        verify(embeddingClient).embed("query");
    }

    @Test
    void retrieve_embeddingFails_fallsBackToKeywordOnly() {
        when(embeddingClient.embed("query")).thenThrow(new RuntimeException("API down"));
        when(keywordChannel.search(any(), any(), any(), anyInt()))
                .thenReturn(List.of(chunk(1, 1.0f), chunk(2, 1.0f)));

        List<ScoredChunk> results = retriever.retrieve("query", null, 5);

        assertEquals(2, results.size());
        // 向量化失败，向量通道不应被调用
        verify(vectorChannel, never()).search(any(), any(), any(), anyInt());
    }

    @Test
    void retrieve_noResults_returnsEmpty() {
        when(embeddingClient.embed("query")).thenReturn(new float[]{0.1f});
        when(vectorChannel.search(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(keywordChannel.search(any(), any(), any(), anyInt())).thenReturn(List.of());

        assertTrue(retriever.retrieve("query", null, 5).isEmpty());
    }

    @Test
    void retrieve_rerankLimitedToThree() {
        when(embeddingClient.embed("query")).thenReturn(new float[]{0.1f});
        when(vectorChannel.search(any(), any(), any(), anyInt()))
                .thenReturn(List.of(chunk(1, 0.9f), chunk(2, 0.8f), chunk(3, 0.7f), chunk(4, 0.6f)));
        when(keywordChannel.search(any(), any(), any(), anyInt())).thenReturn(List.of());

        List<ScoredChunk> results = retriever.retrieve("query", null, 5);

        // rerank 精排固定返回 top3
        assertEquals(3, results.size());
    }

    @Test
    void retrieve_keywordChannelFails_stillReturnsVectorHits() {
        when(embeddingClient.embed("query")).thenReturn(new float[]{0.1f});
        when(vectorChannel.search(any(), any(), any(), anyInt()))
                .thenReturn(List.of(chunk(1, 0.9f)));
        when(keywordChannel.search(any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("DB error"));

        List<ScoredChunk> results = retriever.retrieve("query", null, 5);

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).getChunk().getChunkId());
    }
}