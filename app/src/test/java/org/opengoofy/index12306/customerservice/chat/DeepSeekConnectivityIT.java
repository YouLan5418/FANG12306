package org.opengoofy.index12306.customerservice.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * DeepSeek 云端连通性测试。
 *
 * 默认不会执行，避免普通单测依赖外网和真实 API Key。
 * 手动运行示例：
 * mvn -pl app -Dtest=DeepSeekConnectivityIT -Ddeepseek.live=true test
 */
@EnabledIfSystemProperty(named = "deepseek.live", matches = "true")
class DeepSeekConnectivityIT {

    @Test
    void deepSeek_chatCompletionShouldReturnContent() throws IOException {
        String apiKey = resolveApiKey();
        assumeFalse(apiKey.isBlank(), "DEEPSEEK_API_KEY is not set in environment or .env");

        ChatProperties properties = new ChatProperties();
        properties.setProvider("deepseek");
        properties.setBaseUrl("https://api.deepseek.com");
        properties.setApiKey(apiKey);
        properties.setModel("deepseek-v4-flash");
        properties.setTemperature(0.1);
        properties.setTimeoutMs(60_000);
        properties.setFailureThreshold(3);
        properties.setOpenDurationMs(30_000);

        OpenAIStyleChatClient client = new OpenAIStyleChatClient(
                properties,
                new ModelHealthStore(properties)
        );

        ChatResponse response = client.chat(ChatRequest.builder()
                .message(ChatMessage.system("You are a connectivity test."))
                .message(ChatMessage.user("Reply with exactly: ok"))
                .temperature(0.1)
                .maxTokens(16)
                .build());

        assertNotNull(response);
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isBlank(), "DeepSeek response content should not be blank");
    }

    private String resolveApiKey() throws IOException {
        String value = System.getenv("DEEPSEEK_API_KEY");
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        for (Path path : new Path[]{Path.of(".env"), Path.of("../.env")}) {
            if (!Files.exists(path)) {
                continue;
            }
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            }
            value = properties.getProperty("DEEPSEEK_API_KEY");
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
