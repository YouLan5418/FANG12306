package org.opengoofy.index12306.customerservice.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.opengoofy.index12306.customerservice.chat.ChatProperties;
import org.opengoofy.index12306.customerservice.chat.ModelHealthStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Ollama 本地向量模型连通性测试。
 *
 * 默认不会执行，避免普通单测依赖本机 Ollama。
 * 手动运行示例：
 * mvn -pl app -Dtest=OllamaEmbeddingConnectivityIT -Dollama.live=true test
 */
@EnabledIfSystemProperty(named = "ollama.live", matches = "true")
class OllamaEmbeddingConnectivityIT {

    @Test
    void ollamaEmbedding_shouldReturnConfiguredDimension() {
        EmbeddingProperties embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setBaseUrl("http://127.0.0.1:11434/v1");
        embeddingProperties.setApiKey("ollama");
        embeddingProperties.setModel("qwen3-embedding:0.6b");
        embeddingProperties.setDimension(1024);
        embeddingProperties.setTimeoutMs(60_000);
        embeddingProperties.setBatchSize(4);

        ChatProperties chatProperties = new ChatProperties();
        chatProperties.setFailureThreshold(3);
        chatProperties.setOpenDurationMs(30_000);

        CloudEmbeddingClient client = new CloudEmbeddingClient(
                embeddingProperties,
                new ModelHealthStore(chatProperties)
        );

        float[] vector = client.embed("退票规则是什么");

        assertNotNull(vector);
        assertEquals(embeddingProperties.getDimension(), vector.length);
    }
}
