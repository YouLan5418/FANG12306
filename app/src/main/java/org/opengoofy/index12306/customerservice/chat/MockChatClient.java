package org.opengoofy.index12306.customerservice.chat;

import java.util.Arrays;
import java.util.List;

/**
 * Mock ChatClient for testing FC engine, tool execution, and error handling
 * without a real LLM. Returns scripted responses in sequence.
 */
public class MockChatClient implements ChatClient {

    private final List<ChatResponse> script;
    private int callIndex = 0;

    public MockChatClient(ChatResponse... responses) {
        this.script = Arrays.asList(responses);
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        return nextResponse();
    }

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
        ChatResponse resp = nextResponse();
        if (resp.getContent() != null && !resp.getContent().isEmpty()) {
            for (char c : resp.getContent().toCharArray()) {
                callback.onContent(String.valueOf(c));
            }
        }
        callback.onComplete();
        return () -> {};
    }

    @Override
    public ChatResponse streamChatCollect(ChatRequest request, StreamCallback callback) {
        ChatResponse resp = nextResponse();
        if (resp.hasToolCalls()) {
            // Tool call path: don't stream content to downstream
            return resp;
        }
        // Content path: stream tokens then complete
        if (resp.getContent() != null && !resp.getContent().isEmpty()) {
            for (char c : resp.getContent().toCharArray()) {
                callback.onContent(String.valueOf(c));
            }
        }
        callback.onComplete();
        return resp;
    }

    private synchronized ChatResponse nextResponse() {
        if (callIndex >= script.size()) {
            return ChatResponse.builder().content("").finishReason("end").build();
        }
        return script.get(callIndex++);
    }
}