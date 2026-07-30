package org.opengoofy.index12306.customerservice.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * First-packet routing callback for FC engine.
 *
 * In OpenAI-compatible streaming, delta.tool_calls and delta.content are mutually
 * exclusive per chunk. The first chunk carrying either field definitively determines
 * the response path:
 * - CONTENT: flush content to downstream (user sees the answer)
 * - TOOL_CALL: suppress content (FC engine will execute tools and loop)
 *
 * This eliminates the "sync chat to check tool_calls, then streamChat" double-call.
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
        if (mode == StreamMode.UNDECIDED) {
            mode = StreamMode.CONTENT;
        }
        if (mode == StreamMode.CONTENT) {
            downstream.onContent(content);
        }
        // TOOL_CALL: suppress content
    }

    /**
     * Called by ChatClient when delta.tool_calls is detected in an SSE chunk.
     * Tool call fragments are assembled by index (id/name in first chunk,
     * arguments continued in subsequent chunks).
     */
    public void onToolCallDelta(int index, String id, String name, String argumentsDelta) {
        if (mode == StreamMode.UNDECIDED) {
            mode = StreamMode.TOOL_CALL;
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
            downstream.onComplete();
        }
        // TOOL_CALL: don't call onComplete - FC engine will loop and produce final answer
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