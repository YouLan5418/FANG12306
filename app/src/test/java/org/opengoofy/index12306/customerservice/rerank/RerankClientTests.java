package org.opengoofy.index12306.customerservice.rerank;

import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.customerservice.chat.ChatProperties;
import org.opengoofy.index12306.customerservice.chat.ModelHealthStore;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P7-T3 单元测试：NoopRerankClient 排序、CloudRerankClient 降级、重排序数据模型。
 */
class RerankClientTests {

    private List<RetrievedChunk> sampleCandidates() {
        return Arrays.asList(
                RetrievedChunk.builder().chunkId(1L).content("退票手续费按时间梯度收取").score(0.85f).source("vector").build(),
                RetrievedChunk.builder().chunkId(2L).content("开车前48小时以上退票收5%").score(0.72f).source("keyword").build(),
                RetrievedChunk.builder().chunkId(3L).content("改签需要在开车前办理").score(0.68f).source("vector").build(),
                RetrievedChunk.builder().chunkId(4L).content("儿童票按规定身高购买").score(0.45f).source("keyword").build()
        );
    }

    @Test
    void noopRerankClient_returnsTopNByRetrievalScore() {
        NoopRerankClient client = new NoopRerankClient();
        List<ScoredChunk> results = client.rerank("退票手续费", sampleCandidates(), 2);

        assertEquals(2, results.size());
        assertEquals(1L, results.get(0).getChunk().getChunkId());
        assertEquals(0.85f, results.get(0).getRerankScore(), 0.001f);
        assertEquals(2L, results.get(1).getChunk().getChunkId());
    }

    @Test
    void noopRerankClient_emptyCandidatesReturnsEmpty() {
        NoopRerankClient client = new NoopRerankClient();
        List<ScoredChunk> results = client.rerank("query", List.of(), 3);
        assertTrue(results.isEmpty());
    }

    @Test
    void noopRerankClient_topNExceedsSizeReturnsAll() {
        NoopRerankClient client = new NoopRerankClient();
        List<ScoredChunk> results = client.rerank("query", sampleCandidates(), 10);
        assertEquals(4, results.size());
    }

    @Test
    void cloudRerankClient_degradesOnApiFailure() {
        // 使用无效端点触发失败 -> 应优雅降级
        RerankProperties props = new RerankProperties();
        props.setBaseUrl("http://localhost:1");
        props.setApiKey("test");
        props.setModel("test-rerank");
        props.setTimeoutMs(500);

        ChatProperties chatProps = new ChatProperties();
        chatProps.setFailureThreshold(100);
        ModelHealthStore healthStore = new ModelHealthStore(chatProps);

        CloudRerankClient client = new CloudRerankClient(props, healthStore);
        List<ScoredChunk> results = client.rerank("退票手续费", sampleCandidates(), 3);

        // 应降级为原始顺序，不应抛出异常
        assertEquals(3, results.size());
        assertEquals(1L, results.get(0).getChunk().getChunkId());
    }

    @Test
    void cloudRerankClient_singleCandidateSkipsApi() {
        RerankProperties props = new RerankProperties();
        props.setBaseUrl("http://localhost:1");
        props.setTimeoutMs(500);

        ChatProperties chatProps = new ChatProperties();
        ModelHealthStore healthStore = new ModelHealthStore(chatProps);

        CloudRerankClient client = new CloudRerankClient(props, healthStore);
        List<RetrievedChunk> single = List.of(
                RetrievedChunk.builder().chunkId(1L).content("only one").score(0.9f).build()
        );
        List<ScoredChunk> results = client.rerank("query", single, 1);

        assertEquals(1, results.size());
        assertEquals(0.9f, results.get(0).getRerankScore(), 0.001f);
    }

    @Test
    void rerankProperties_defaultsAreSane() {
        RerankProperties props = new RerankProperties();
        assertEquals("gte-rerank", props.getModel());
        assertEquals(3, props.getTopN());
        assertTrue(props.getTimeoutMs() > 0);
    }

    @Test
    void scoredChunk_wrapsRetrievedChunk() {
        RetrievedChunk chunk = RetrievedChunk.builder()
                .chunkId(1L)
                .content("test")
                .score(0.8f)
                .source("vector")
                .build();
        ScoredChunk scored = new ScoredChunk(chunk, 0.95f);

        assertEquals(1L, scored.getChunk().getChunkId());
        assertEquals(0.95f, scored.getRerankScore(), 0.001f);
        assertEquals("test", scored.getChunk().getContent());
    }
}