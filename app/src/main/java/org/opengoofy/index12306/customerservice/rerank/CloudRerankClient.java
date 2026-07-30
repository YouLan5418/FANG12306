package org.opengoofy.index12306.customerservice.rerank;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.customerservice.chat.ModelHealthStore;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cloud rerank client using Cohere/Jina-style /rerank endpoint.
 * Degrades gracefully: on any failure (circuit breaker, API error, timeout),
 * returns the original candidates limited to topN sorted by retrieval score.
 * This ensures the RAG pipeline never breaks due to rerank issues.
 */
@Slf4j
@Component
public class CloudRerankClient implements RerankClient {

    private final RerankProperties properties;
    private final ModelHealthStore modelHealthStore;
    private final HttpClient httpClient;

    public CloudRerankClient(RerankProperties properties, ModelHealthStore modelHealthStore) {
        this.properties = properties;
        this.modelHealthStore = modelHealthStore;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
    }

    @Override
    public List<ScoredChunk> rerank(String query, List<RetrievedChunk> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        if (candidates.size() == 1 || topN <= 0) {
            return candidates.stream().limit(Math.max(topN, 1))
                    .map(c -> new ScoredChunk(c, c.getScore()))
                    .toList();
        }
        if (!modelHealthStore.allowCall(properties.getModel())) {
            log.warn("Rerank circuit breaker open, returning original candidates");
            return degrade(candidates, topN);
        }
        try {
            JSONObject body = new JSONObject();
            body.put("model", properties.getModel());
            body.put("query", query);
            List<String> docs = candidates.stream().map(RetrievedChunk::getContent).toList();
            body.put("documents", docs);
            body.put("top_n", Math.min(topN, candidates.size()));

            HttpRequest request = buildHttpRequest(body);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.warn("Rerank request failed: status={}, body={}", response.statusCode(), response.body());
                modelHealthStore.markFailure(properties.getModel());
                return degrade(candidates, topN);
            }

            List<ScoredChunk> results = parseRerankResponse(response.body(), candidates);
            modelHealthStore.markSuccess(properties.getModel());
            return results;
        } catch (Exception e) {
            log.warn("Rerank failed, degrading to original order: {}", e.getMessage());
            modelHealthStore.markFailure(properties.getModel());
            return degrade(candidates, topN);
        }
    }

    private List<ScoredChunk> degrade(List<RetrievedChunk> candidates, int topN) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble((RetrievedChunk c) -> c.getScore()).reversed())
                .limit(topN)
                .map(c -> new ScoredChunk(c, c.getScore()))
                .toList();
    }

    private HttpRequest buildHttpRequest(JSONObject body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/rerank"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8));
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.getApiKey());
        }
        return builder.build();
    }

    private List<ScoredChunk> parseRerankResponse(String responseBody, List<RetrievedChunk> candidates) {
        JSONObject json = JSON.parseObject(responseBody);
        JSONArray results = json.getJSONArray("results");
        if (results == null || results.isEmpty()) {
            return degrade(candidates, properties.getTopN());
        }
        List<ScoredChunk> scored = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) {
            JSONObject item = results.getJSONObject(i);
            int index = item.getIntValue("index", 0);
            float score = item.getFloatValue("relevance_score");
            if (index >= 0 && index < candidates.size()) {
                scored.add(new ScoredChunk(candidates.get(index), score));
            }
        }
        // Sort by rerank score descending
        scored.sort(Comparator.comparingDouble((ScoredChunk s) -> s.getRerankScore()).reversed());
        return scored;
    }
}