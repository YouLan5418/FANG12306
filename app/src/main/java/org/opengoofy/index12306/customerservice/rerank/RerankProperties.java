package org.opengoofy.index12306.customerservice.rerank;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Rerank client configuration mapped from application.yml prefix "ai.rerank".
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.rerank")
public class RerankProperties {

    private String baseUrl = "https://api.example.com/v1";

    private String apiKey = "sk-xxx";

    private String model = "gte-rerank";

    private int topN = 3;

    private int timeoutMs = 30000;
}