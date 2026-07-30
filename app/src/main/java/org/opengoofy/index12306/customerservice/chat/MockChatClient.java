package org.opengoofy.index12306.customerservice.chat;

import java.util.Arrays;
import java.util.List;

/**
 * Mock 聊天客户端，按预设脚本依次返回响应。
 * 用于测试 FC 引擎、工具执行、异常容错，不依赖真实 LLM。
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
            // 工具调用路径：不向下游推送 content
            return resp;
        }
        // 内容路径：推送 token 后完成
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