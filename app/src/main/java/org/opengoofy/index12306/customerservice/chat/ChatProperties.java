package org.opengoofy.index12306.customerservice.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 聊天客户端配置，映射 application.yml 中 ai.chat 前缀。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.chat")
public class ChatProperties {

    private String provider = "ollama";

    private String baseUrl = "http://localhost:11434/v1";

    private String apiKey = "ollama";

    private String model = "qwen2.5:7b";

    private double temperature = 0.7;

    private int timeoutMs = 60000;

    /** 限流：每用户每分钟最大消息数 */
    private int rateLimitPerMinute = 10;

    /** 断路器：连续失败多少次后熔断 */
    private int failureThreshold = 3;

    /** 断路器：熔断持续时长（毫秒） */
    private long openDurationMs = 30000;
}