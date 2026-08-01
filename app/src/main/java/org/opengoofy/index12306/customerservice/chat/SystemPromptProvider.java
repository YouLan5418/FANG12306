package org.opengoofy.index12306.customerservice.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads the customer-service system prompt from an external file.
 */
@Slf4j
@Component
public class SystemPromptProvider {

    private final ChatProperties properties;
    private volatile String cachedPrompt;
    private volatile long cachedLastModified = Long.MIN_VALUE;

    public SystemPromptProvider(ChatProperties properties) {
        this.properties = properties;
    }

    public String currentPrompt() {
        Path promptPath = Path.of(properties.getSystemPromptFile());
        try {
            if (!Files.exists(promptPath)) {
                return properties.getSystemPrompt();
            }
            long lastModified = Files.getLastModifiedTime(promptPath).toMillis();
            String prompt = cachedPrompt;
            if (prompt != null && cachedLastModified == lastModified) {
                return prompt;
            }
            synchronized (this) {
                lastModified = Files.getLastModifiedTime(promptPath).toMillis();
                if (cachedPrompt == null || cachedLastModified != lastModified) {
                    String loaded = Files.readString(promptPath, StandardCharsets.UTF_8).trim();
                    if (!loaded.isBlank()) {
                        cachedPrompt = loaded;
                        cachedLastModified = lastModified;
                        log.info("Reloaded customer-service system prompt: {}", promptPath.toAbsolutePath());
                    }
                }
                return cachedPrompt != null ? cachedPrompt : properties.getSystemPrompt();
            }
        } catch (IOException e) {
            log.warn("Failed to load customer-service system prompt: {}", e.getMessage());
            return cachedPrompt != null ? cachedPrompt : properties.getSystemPrompt();
        }
    }
}
