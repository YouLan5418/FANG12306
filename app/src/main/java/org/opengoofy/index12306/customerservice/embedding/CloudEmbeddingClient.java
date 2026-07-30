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

/**
 * Cloud embedding client using OpenAI-compatible /embeddings endpoint.
 * Supports any model/dimension; batch requests are split by configured batchSize.
 * Circuit breaker integration via ModelHealthStore.
 */
@Slf4j
@Component
public class CloudEmbeddingClient implements EmbeddingClient {

    private final EmbeddingProperties properties;
    private final org.opengoofy.index12306.customerservice.chat.ModelHealthStore modelHealthStore;
    private final HttpClient httpClient;

    public CloudEmbeddingClient(
            EmbeddingProperties properties,
            org.opengoofy.index12306.customerservice.chat.ModelHealthStore modelHealthStore) {
        this.properties = properties;
        this.modelHealthStore = modelHealthStore;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
    }

    @Override
    public float[] embed(String text) {
        if (!modelHealthStore.allowCall(properties.getModel())) {
            throw new RuntimeException("Embedding service unavailable (circuit breaker open)");
        }
        try {
            JSONObject body = new JSONObject();
            body.put("model", properties.getModel());
            body.put("input", text);

            HttpResponse<String> response = sendRequest(body);
            if (response.statusCode() != 200) {
                log.warn("Embedding request failed: status={}, body={}", response.statusCode(), response.body());
                modelHealthStore.markFailure(properties.getModel());
                throw new RuntimeException("Embedding request failed: HTTP " + response.statusCode());
            }

            float[] result = parseEmbedding(response.body());
            modelHealthStore.markSuccess(properties.getModel());
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            modelHealthStore.markFailure(properties.getModel());
            throw new RuntimeException("Embedding request failed", e);
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
        // Split into sub-batches
        List<float[]> results = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> sub = texts.subList(i, end);
            results.addAll(doBatchEmbed(sub));
        }
        return results;
    }

    private List<float[]> doBatchEmbed(List<String> texts) {
        if (!modelHealthStore.allowCall(properties.getModel())) {
            throw new RuntimeException("Embedding service unavailable (circuit breaker open)");
        }
        try {
            JSONObject body = new JSONObject();
            body.put("model", properties.getModel());
            body.put("input", texts);

            HttpResponse<String> response = sendRequest(body);
            if (response.statusCode() != 200) {
                log.warn("Batch embedding request failed: status={}, body={}", response.statusCode(), response.body());
                modelHealthStore.markFailure(properties.getModel());
                throw new RuntimeException("Batch embedding failed: HTTP " + response.statusCode());
            }

            List<float[]> results = parseBatchEmbeddings(response.body());
            modelHealthStore.markSuccess(properties.getModel());
            return results;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            modelHealthStore.markFailure(properties.getModel());
            throw new RuntimeException("Batch embedding failed", e);
        }
    }

    // ==================== HTTP ====================

    private HttpResponse<String> sendRequest(JSONObject body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/embeddings"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8))
                .build();
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/embeddings"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8))
                    .build();
        }
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    // ==================== Response parsing ====================

    private float[] parseEmbedding(String responseBody) {
        JSONObject json = JSON.parseObject(responseBody);
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("Embedding response missing data array");
        }
        JSONObject first = data.getJSONObject(0);
        JSONArray embedding = first.getJSONArray("embedding");
        return toFloatArray(embedding);
    }

    private List<float[]> parseBatchEmbeddings(String responseBody) {
        JSONObject json = JSON.parseObject(responseBody);
        JSONArray data = json.getJSONArray("data");
        if (data == null) {
            throw new RuntimeException("Batch embedding response missing data array");
        }
        // Sort by index to preserve input order
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