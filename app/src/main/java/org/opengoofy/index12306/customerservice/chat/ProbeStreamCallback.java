package org.opengoofy.index12306.customerservice.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FC 引擎首包分流回调。
 *
 * 在 OpenAI 兼容流式中，delta.tool_calls 和 delta.content 在每个 chunk 中互斥出现。
 * 有些模型会在同一轮中先输出说明文本，再输出 tool_calls。
 * 因此 content 会先缓冲：
 * - 最终没有工具调用：完成时将 content 推送给下游（用户看到回复）
 * - 出现工具调用：已缓冲/后续 content 作为 thinking 推送，FC 引擎执行工具并循环
 *
 * 这消除了「先同步 chat 判断 tool_calls，再 streamChat 输出」的双倍调用。
 */
public class ProbeStreamCallback implements StreamCallback {

    public enum StreamMode {
        UNDECIDED,
        CONTENT,
        TOOL_CALL
    }

    private final StreamCallback downstream;
    private volatile StreamMode mode = StreamMode.UNDECIDED;

    private final StringBuilder collectedContent = new StringBuilder();
    private final Map<Integer, ToolCallAssembler> toolCallAssemblers = new LinkedHashMap<>();

    public ProbeStreamCallback(StreamCallback downstream) {
        this.downstream = downstream;
    }

    @Override
    public void onContent(String content) {
        collectedContent.append(content);
        if (mode == StreamMode.TOOL_CALL) {
            downstream.onThinking(content);
        }
    }

    /**
     * ChatClient 在 SSE chunk 中检测到 delta.tool_calls 时调用。
     * tool_call 分片按 index 组装（首个 chunk 含 id/name，后续 chunk 续接 arguments）。
     */
    public void onToolCallDelta(int index, String id, String name, String argumentsDelta) {
        if (mode == StreamMode.UNDECIDED) {
            mode = StreamMode.TOOL_CALL;
            if (!collectedContent.isEmpty()) {
                downstream.onThinking(collectedContent.toString());
            }
        }
        ToolCallAssembler assembler = toolCallAssemblers.computeIfAbsent(index, k -> new ToolCallAssembler());
        if (id != null) {
            assembler.id = id;
        }
        if (name != null) {
            assembler.name = name;
        }
        if (argumentsDelta != null) {
            assembler.arguments.append(argumentsDelta);
        }
    }

    @Override
    public void onComplete() {
        if (mode != StreamMode.TOOL_CALL) {
            mode = StreamMode.CONTENT;
            if (!collectedContent.isEmpty()) {
                downstream.onContent(collectedContent.toString());
            }
            downstream.onComplete();
        }
        // TOOL_CALL 模式：不调 onComplete，FC 引擎会循环并产生最终回复
    }

    @Override
    public void onError(Throwable error) {
        downstream.onError(error);
    }

    public StreamMode getMode() {
        return mode;
    }

    public ChatResponse toChatResponse() {
        List<ToolCall> calls = new ArrayList<>();
        for (ToolCallAssembler assembler : toolCallAssemblers.values()) {
            calls.add(assembler.build());
        }
        return ChatResponse.builder()
                .content(collectedContent.toString())
                .toolCalls(calls)
                .build();
    }

    private static class ToolCallAssembler {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();

        ToolCall build() {
            return new ToolCall(id, name, arguments.toString());
        }
    }
}
