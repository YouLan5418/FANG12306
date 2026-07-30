package org.opengoofy.index12306.customerservice.embedding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Embedding client configuration mapped from application.yml prefix "ai.embedding".
 * Dimension is model-dependent (bge-m3=1024, text-embedding-3-large=3072, etc.)
 * and only used for validation/logging, not enforced by the type handler.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.embedding")
public class EmbeddingProperties {

    private String baseUrl = "https://api.example.com/v1";

    private String apiKey = "sk-xxx";

    private String model = "bge-m3";

    private int dimension = 1024;

    private int timeoutMs = 30000;

    /** Max texts per API call; larger batches are split. */
    private int batchSize = 20;
}