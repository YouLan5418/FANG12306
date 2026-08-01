package org.opengoofy.index12306.customerservice.embedding;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * 云端 Embedding 客户端，调用 OpenAI 兼容 /embeddings 端点。
 * 支持任意模型/维度；批量请求按配置的 batchSize 自动拆分。
 * 集成 ModelHealthStore 断路器。
 */
@Slf4j
@Component
public class CloudEmbeddingClient implements EmbeddingClient {

    private final EmbeddingProperties properties;
    private final org.opengoofy.index12306.customerservice.chat.ModelHealthStore modelHealthStore;
    private final HttpClient httpClient;
    private final Semaphore semaphore;

    public CloudEmbeddingClient(
            EmbeddingProperties properties,
            org.opengoofy.index12306.customerservice.chat.ModelHealthStore modelHealthStore) {
        this.properties = properties;
        this.modelHealthStore = modelHealthStore;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
        this.semaphore = new Semaphore(Math.max(1, properties.getMaxConcurrent()));
    }

    @Override
    public float[] embed(String text) {
        return withPermit(() -> doEmbed(text));
    }

    private float[] doEmbed(String text) {
        if (!modelHealthStore.allowCall(properties.getModel())) {
            throw new RuntimeException("Embedding 服务不可用（断路器开启）");
        }
        try {
            JSONObject body = new JSONObject();
            body.put("model", properties.getModel());
            body.put("input", text);

            HttpResponse<String> response = sendRequest(body);
            if (response.statusCode() != 200) {
                log.warn("Embedding 请求失败: status={}, body={}", response.statusCode(), response.body());
                modelHealthStore.markFailure(properties.getModel());
                throw new RuntimeException("Embedding 请求失败: HTTP " + response.statusCode());
            }

            float[] result = parseEmbedding(response.body());
            modelHealthStore.markSuccess(properties.getModel());
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            modelHealthStore.markFailure(properties.getModel());
            throw new RuntimeException("Embedding 请求失败", e);
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        int batchSize = properties.getBatchSize();
        if (texts.size() <= batchSize) {
            return doBatchEmbed(texts);
        }
        // 拆分为子批次
        List<float[]> results = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> sub = texts.subList(i, end);
            results.addAll(doBatchEmbed(sub));
        }
        return results;
    }

    private List<float[]> doBatchEmbed(List<String> texts) {
        return withPermit(() -> doBatchEmbedWithPermit(texts));
    }

    private List<float[]> doBatchEmbedWithPermit(List<String> texts) {
        if (!modelHealthStore.allowCall(properties.getModel())) {
            throw new RuntimeException("Embedding 服务不可用（断路器开启）");
        }
        try {
            JSONObject body = new JSONObject();
            body.put("model", properties.getModel());
            body.put("input", texts);

            HttpResponse<String> response = sendRequest(body);
            if (response.statusCode() != 200) {
                log.warn("批量 Embedding 请求失败: status={}, body={}", response.statusCode(), response.body());
                modelHealthStore.markFailure(properties.getModel());
                throw new RuntimeException("批量 Embedding 失败: HTTP " + response.statusCode());
            }

            List<float[]> results = parseBatchEmbeddings(response.body());
            modelHealthStore.markSuccess(properties.getModel());
            return results;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            modelHealthStore.markFailure(properties.getModel());
            throw new RuntimeException("批量 Embedding 失败", e);
        }
    }

    private <T> T withPermit(Supplier<T> supplier) {
        if (!semaphore.tryAcquire()) {
            throw new RuntimeException("Embedding 服务繁忙，请稍后再试");
        }
        try {
            return supplier.get();
        } finally {
            semaphore.release();
        }
    }

    // ==================== HTTP 请求 ====================

    private HttpResponse<String> sendRequest(JSONObject body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/embeddings"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8));
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.getApiKey());
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    // ==================== 响应解析 ====================

    private float[] parseEmbedding(String responseBody) {
        JSONObject json = JSON.parseObject(responseBody);
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("Embedding 响应缺少 data 数组");
        }
        JSONObject first = data.getJSONObject(0);
        JSONArray embedding = first.getJSONArray("embedding");
        return toFloatArray(embedding);
    }

    private List<float[]> parseBatchEmbeddings(String responseBody) {
        JSONObject json = JSON.parseObject(responseBody);
        JSONArray data = json.getJSONArray("data");
        if (data == null) {
            throw new RuntimeException("批量 Embedding 响应缺少 data 数组");
        }
        // 按 index 排序以保持输入顺序
        JSONObject[] sorted = new JSONObject[data.size()];
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            int index = item.getIntValue("index", 0);
            sorted[index] = item;
        }
        List<float[]> results = new ArrayList<>(sorted.length);
        for (JSONObject item : sorted) {
            JSONArray embedding = item.getJSONArray("embedding");
            results.add(toFloatArray(embedding));
        }
        return results;
    }

    private float[] toFloatArray(JSONArray arr) {
        float[] result = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            result[i] = arr.getFloatValue(i);
        }
        return result;
    }
}
