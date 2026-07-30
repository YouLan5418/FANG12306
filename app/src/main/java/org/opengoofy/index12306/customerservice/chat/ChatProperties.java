package org.opengoofy.index12306.customerservice.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI chat client configuration mapped from application.yml prefix "ai.chat".
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

    /** Circuit breaker: consecutive failures before opening. */
    private int failureThreshold = 3;

    private int rateLimitPerMinute = 10;
    /** Rate limit: max chat messages per user per minute. */

    /** Circuit breaker: open state duration in milliseconds. */
    private long openDurationMs = 30000;
}
