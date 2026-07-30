package org.opengoofy.index12306.customerservice.embedding;

import java.util.List;

/**
 * Embedding client abstraction for text vectorization.
 * Dimension is determined by the configured model, not hardcoded.
 */
public interface EmbeddingClient {

    /**
     * Embed a single text into a dense vector.
     *
     * @param text input text
     * @return float array whose length equals the model's output dimension
     */
    float[] embed(String text);

    /**
     * Embed multiple texts in a single batch request.
     * Implementations may split into sub-batches if the count exceeds batchSize.
     *
     * @param texts list of input texts
     * @return list of float arrays, same order as input
     */
    List<float[]> embedBatch(List<String> texts);
}