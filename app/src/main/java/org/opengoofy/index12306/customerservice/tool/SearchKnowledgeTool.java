package org.opengoofy.index12306.customerservice.tool;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.customerservice.chat.ToolDefinition;
import org.opengoofy.index12306.customerservice.fc.ToolExecutionContext;
import org.opengoofy.index12306.customerservice.fc.ToolExecutor;
import org.opengoofy.index12306.customerservice.rag.RagRetriever;
import org.opengoofy.index12306.customerservice.rerank.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识检索工具：将 RAG 混合检索暴露为 LLM 可调用的工具。
 * 用户询问退改规则、购票流程、证件要求等知识时由 LLM 自主调用。
 * 返回 rerank 精排后的 top3 切片内容。
 */
@Component
@RequiredArgsConstructor
public class SearchKnowledgeTool implements ToolExecutor {

    private static final int TOP_K = 5;

    private final RagRetriever ragRetriever;

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

        List<ScoredChunk> chunks = ragRetriever.retrieve(query, null, TOP_K);

        JSONArray result = new JSONArray();
        for (ScoredChunk chunk : chunks) {
            JSONObject c = new JSONObject();
            c.put("content", chunk.getChunk().getContent());
            c.put("score", chunk.getRerankScore());
            result.add(c);
        }
        return result.toJSONString();
    }
}