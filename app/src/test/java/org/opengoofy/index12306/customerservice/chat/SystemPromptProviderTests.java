package org.opengoofy.index12306.customerservice.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemPromptProviderTests {

    @TempDir
    Path tempDir;

    @Test
    void currentPrompt_reloadWhenFileChanges() throws Exception {
        Path promptFile = tempDir.resolve("prompt.md");
        Files.writeString(promptFile, "first prompt", StandardCharsets.UTF_8);

        ChatProperties properties = new ChatProperties();
        properties.setSystemPromptFile(promptFile.toString());
        SystemPromptProvider provider = new SystemPromptProvider(properties);

        assertEquals("first prompt", provider.currentPrompt());

        Thread.sleep(5);
        Files.writeString(promptFile, "second prompt", StandardCharsets.UTF_8);

        assertEquals("second prompt", provider.currentPrompt());
    }

    @Test
    void currentPrompt_fallbackWhenFileMissing() {
        ChatProperties properties = new ChatProperties();
        properties.setSystemPromptFile(tempDir.resolve("missing.md").toString());
        properties.setSystemPrompt("fallback prompt");

        SystemPromptProvider provider = new SystemPromptProvider(properties);

        assertEquals("fallback prompt", provider.currentPrompt());
    }
}
