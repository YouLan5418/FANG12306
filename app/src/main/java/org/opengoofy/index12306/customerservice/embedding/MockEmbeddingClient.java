package org.opengoofy.index12306.customerservice.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock EmbeddingClient for testing RAG retrieval and ingestion pipeline
 * without a real embedding API. Generates deterministic pseudo-random vectors
 * based on text hash, so the same text always produces the same vector.
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
     * Generates a deterministic unit vector from text hash.
     * Same text -> same vector, different text -> orthogonal-ish vector.
     */
    private float[] pseudoVector(String text) {
        float[] vec = new float[dimension];
        int hash = text.hashCode();
        for (int i = 0; i < dimension; i++) {
            hash = 31 * hash + i;
            vec[i] = (hash % 1000) / 1000.0f;
        }
        // Normalize to unit length
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