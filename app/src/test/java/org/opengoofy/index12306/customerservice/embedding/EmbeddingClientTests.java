package org.opengoofy.index12306.customerservice.embedding;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for P7-T2: MockEmbeddingClient determinism and batch behavior.
 */
class EmbeddingClientTests {

    @Test
    void mockEmbeddingClient_returnsCorrectDimension() {
        MockEmbeddingClient client = new MockEmbeddingClient(1024);
        float[] vec = client.embed("hello world");
        assertEquals(1024, vec.length);
    }

    @Test
    void mockEmbeddingClient_sameTextProducesSameVector() {
        MockEmbeddingClient client = new MockEmbeddingClient(256);
        float[] v1 = client.embed("退票手续费");
        float[] v2 = client.embed("退票手续费");
        assertArrayEquals(v1, v2, 0.0001f);
    }

    @Test
    void mockEmbeddingClient_differentTextProducesDifferentVector() {
        MockEmbeddingClient client = new MockEmbeddingClient(256);
        float[] v1 = client.embed("退票手续费");
        float[] v2 = client.embed("改签规则");
        assertFalse(Arrays.equals(v1, v2));
    }

    @Test
    void mockEmbeddingClient_vectorIsNormalized() {
        MockEmbeddingClient client = new MockEmbeddingClient(128);
        float[] vec = client.embed("test normalization");
        float norm = 0;
        for (float v : vec) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        assertEquals(1.0f, norm, 0.01f, "Vector should be unit length");
    }

    @Test
    void mockEmbeddingClient_batchReturnsCorrectOrder() {
        MockEmbeddingClient client = new MockEmbeddingClient(64);
        List<String> texts = Arrays.asList("first", "second", "third");
        List<float[]> results = client.embedBatch(texts);

        assertEquals(3, results.size());
        for (float[] v : results) {
            assertEquals(64, v.length);
        }
        // Same order as input
        assertArrayEquals(client.embed("first"), results.get(0), 0.0001f);
        assertArrayEquals(client.embed("second"), results.get(1), 0.0001f);
        assertArrayEquals(client.embed("third"), results.get(2), 0.0001f);
    }

    @Test
    void mockEmbeddingClient_supportsHighDimension() {
        MockEmbeddingClient client = new MockEmbeddingClient(3072);
        float[] vec = client.embed("high dimensional test");
        assertEquals(3072, vec.length);

        float norm = 0;
        for (float v : vec) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        assertEquals(1.0f, norm, 0.01f);
    }

    @Test
    void embeddingProperties_defaultsAreSane() {
        EmbeddingProperties props = new EmbeddingProperties();
        assertEquals("bge-m3", props.getModel());
        assertEquals(1024, props.getDimension());
        assertEquals(20, props.getBatchSize());
        assertTrue(props.getTimeoutMs() > 0);
    }
}