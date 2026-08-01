package org.opengoofy.index12306.customerservice.tool;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.customerservice.chat.ChatProperties;
import org.opengoofy.index12306.customerservice.chat.ToolDefinition;
import org.opengoofy.index12306.customerservice.fc.ToolExecutionContext;
import org.opengoofy.index12306.customerservice.fc.ToolExecutor;
import org.opengoofy.index12306.customerservice.rag.RagRetriever;
import org.opengoofy.index12306.customerservice.rerank.ScoredChunk;
import org.opengoofy.index12306.framework.cache.DistributedCache;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 知识检索工具：将 RAG 混合检索暴露为 LLM 可调用的工具。
 * 用户询问退改规则、购票流程、证件要求等知识时由 LLM 自主调用。
 * 返回 rerank 精排后的 top3 切片内容。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchKnowledgeTool implements ToolExecutor {

    private static final int TOP_K = 5;
    private static final String CACHE_KEY_PREFIX = "index12306:customer-service:rag:search:";

    private final RagRetriever ragRetriever;
    private final DistributedCache distributedCache;
    private final ChatProperties chatProperties;

    @Override
    public String name() {
        return "searchKnowledge";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name("searchKnowledge")
                .description("检索 12306 退改规则、购票流程、证件要求等知识库")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "要检索的问题")
                        ),
                        "required", List.of("query")
                ))
                .build();
    }

    @Override
    public String execute(Map<String, Object> args, ToolExecutionContext ctx) {
        String query = (String) args.get("query");
        if (query == null || query.isBlank()) {
            return "{\"error\":\"缺少必要参数: query\"}";
        }

        String normalizedQuery = normalizeQuery(query);
        String cached = getCachedResult(normalizedQuery);
        if (cached != null) {
            return cached;
        }

        List<ScoredChunk> chunks = ragRetriever.retrieve(normalizedQuery, null, TOP_K);

        JSONArray result = new JSONArray();
        for (ScoredChunk chunk : chunks) {
            JSONObject c = new JSONObject();
            c.put("content", chunk.getChunk().getContent());
            c.put("score", chunk.getRerankScore());
            result.add(c);
        }
        String json = result.toJSONString();
        putCachedResult(normalizedQuery, json);
        return json;
    }

    private String normalizeQuery(String query) {
        return query.trim().replaceAll("\\s+", " ");
    }

    private String cacheKey(String normalizedQuery) {
        String digest = DigestUtils.md5DigestAsHex(normalizedQuery.getBytes(StandardCharsets.UTF_8));
        return CACHE_KEY_PREFIX + digest;
    }

    private String getCachedResult(String normalizedQuery) {
        long ttlSeconds = chatProperties.getRagCacheTtlSeconds();
        if (ttlSeconds <= 0) {
            return null;
        }
        try {
            String cached = distributedCache.get(cacheKey(normalizedQuery), String.class);
            return cached == null || cached.isBlank() ? null : cached;
        } catch (Exception e) {
            log.debug("RAG cache read failed: {}", e.getMessage());
            return null;
        }
    }

    private void putCachedResult(String normalizedQuery, String json) {
        long ttlSeconds = chatProperties.getRagCacheTtlSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        try {
            distributedCache.put(cacheKey(normalizedQuery), json, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("RAG cache write failed: {}", e.getMessage());
        }
    }
}
