package org.opengoofy.index12306.customerservice.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock Embedding 客户端，用于测试 RAG 检索和入库管道，不依赖真实 API。
 * 基于文本哈希生成确定性伪随机向量，相同文本始终产生相同向量。
 */
public class MockEmbeddingClient implements EmbeddingClient {

    private final int dimension;

    public MockEmbeddingClient(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public float[] embed(String text) {
        return pseudoVector(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(pseudoVector(text));
        }
        return results;
    }

    /**
     * 基于文本哈希生成确定性单位向量。
     * 相同文本 -> 相同向量，不同文本 -> 近似正交向量。
     */
    private float[] pseudoVector(String text) {
        float[] vec = new float[dimension];
        int hash = text.hashCode();
        for (int i = 0; i < dimension; i++) {
            hash = 31 * hash + i;
            vec[i] = (hash % 1000) / 1000.0f;
        }
        // 归一化为单位长度
        float norm = 0;
        for (float v : vec) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dimension; i++) {
                vec[i] /= norm;
            }
        }
        return vec;
    }
}