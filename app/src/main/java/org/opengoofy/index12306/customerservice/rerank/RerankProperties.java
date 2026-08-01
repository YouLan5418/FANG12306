package org.opengoofy.index12306.customerservice.rerank;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 重排序客户端配置，映射 application.yml 中 ai.rerank 前缀。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.rerank")
public class RerankProperties {

    private boolean enabled = true;

    private String baseUrl = "https://api.example.com/v1";

    private String apiKey = "sk-xxx";

    private String model = "gte-rerank";

    private int topN = 3;

    private int timeoutMs = 30000;
}
