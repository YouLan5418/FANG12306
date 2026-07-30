package org.opengoofy.index12306.customerservice.embedding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Embedding 客户端配置，映射 application.yml 中 ai.embedding 前缀。
 * dimension 由模型决定（bge-m3=1024, text-embedding-3-large=3072 等），
 * 仅用于校验/日志，TypeHandler 不强制。
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

    /** 单次 API 调用最大文本数，超出自动拆分 */
    private int batchSize = 20;
}