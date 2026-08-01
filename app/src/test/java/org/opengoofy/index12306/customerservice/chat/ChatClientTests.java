package org.opengoofy.index12306.customerservice.chat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P7-T1 单元测试：ModelHealthStore 断路器、ProbeStreamCallback 首包分流、MockChatClient。
 */
class ChatClientTests {

    @Test
    void buildRequestBody_includesUserIdForDeepSeekIsolation() throws Exception {
        ChatProperties properties = new ChatProperties();
        properties.setModel("deepseek-v4-flash");
        OpenAIStyleChatClient client = new OpenAIStyleChatClient(properties, new ModelHealthStore(properties));
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("退票规则是什么")))
                .userId("u_12345")
                .build();

        Method method = OpenAIStyleChatClient.class.getDeclaredMethod("buildRequestBody", ChatRequest.class, boolean.class);
        method.setAccessible(true);
        String bodyJson = (String) method.invoke(client, request, true);
        JSONObject body = JSON.parseObject(bodyJson);

        assertEquals("u_12345", body.getString("user_id"));
        assertEquals(Boolean.TRUE, body.getBoolean("stream"));
    }

    // ==================== ModelHealthStore 断路器测试 ====================

    @Test
    void modelHealthStore_allowsCallWhenClosed() {
        ChatProperties props = new ChatProperties();
        props.setFailureThreshold(3);
        props.setOpenDurationMs(30_000);
        ModelHealthStore store = new ModelHealthStore(props);

        assertTrue(store.allowCall("test-model"));
        assertFalse(store.isUnavailable("test-model"));
    }

    @Test
    void modelHealthStore_opensAfterThresholdFailures() {
        ChatProperties props = new ChatProperties();
        props.setFailureThreshold(3);
        props.setOpenDurationMs(30_000);
        ModelHealthStore store = new ModelHealthStore(props);

        String modelId = "test-model";
        store.allowCall(modelId);
        store.markFailure(modelId);
        store.allowCall(modelId);
        store.markFailure(modelId);
        assertTrue(store.allowCall(modelId));
        store.markFailure(modelId);

        // 连续 3 次失败后触发熔断(OPEN)
        assertFalse(store.allowCall(modelId));
        assertTrue(store.isUnavailable(modelId));
    }

    @Test
    void modelHealthStore_halfOpenAfterDuration() throws InterruptedException {
        ChatProperties props = new ChatProperties();
        props.setFailureThreshold(2);
        props.setOpenDurationMs(200);
        ModelHealthStore store = new ModelHealthStore(props);

        String modelId = "test-model";
        store.allowCall(modelId);
        store.markFailure(modelId);
        store.allowCall(modelId);
        store.markFailure(modelId);

        // 此时已进入 OPEN 状态
        assertFalse(store.allowCall(modelId));

        // 等待熔断时长过去
        Thread.sleep(250);

        // HALF_OPEN：放行一次探针调用
        assertTrue(store.allowCall(modelId));

        // 探针成功 -> 恢复 CLOSED
        store.markSuccess(modelId);
        assertTrue(store.allowCall(modelId));
        assertFalse(store.isUnavailable(modelId));
    }

    @Test
    void modelHealthStore_halfOpenFailureReopens() throws InterruptedException {
        ChatProperties props = new ChatProperties();
        props.setFailureThreshold(2);
        props.setOpenDurationMs(200);
        ModelHealthStore store = new ModelHealthStore(props);

        String modelId = "test-model";
        store.allowCall(modelId);
        store.markFailure(modelId);
        store.allowCall(modelId);
        store.markFailure(modelId);

        assertFalse(store.allowCall(modelId));
        Thread.sleep(250);

        // HALF_OPEN 探针
        assertTrue(store.allowCall(modelId));
        // 探针失败 -> 重新回到 OPEN
        store.markFailure(modelId);
        assertFalse(store.allowCall(modelId));
    }

    @Test
    void modelHealthStore_successResetsFailureCount() {
        ChatProperties props = new ChatProperties();
        props.setFailureThreshold(3);
        props.setOpenDurationMs(30_000);
        ModelHealthStore store = new ModelHealthStore(props);

        String modelId = "test-model";
        store.allowCall(modelId);
        store.markFailure(modelId);
        store.markFailure(modelId);
        // 成功会重置失败计数
        store.markSuccess(modelId);
        // 需要再累计 3 次失败才会熔断
        store.allowCall(modelId);
        store.markFailure(modelId);
        store.allowCall(modelId);
        store.markFailure(modelId);
        assertTrue(store.allowCall(modelId));
    }

    @Test
    void aiConcurrencyLimiter_blocksSecondRequestForSameUser() {
        ChatProperties props = new ChatProperties();
        props.setGlobalMaxConcurrent(2);
        props.setUserMaxConcurrent(1);
        AiConcurrencyLimiter limiter = new AiConcurrencyLimiter(props);

        AiConcurrencyLimiter.Permit first = limiter.acquire("user1");
        RuntimeException error = assertThrows(RuntimeException.class, () -> limiter.acquire("user1"));
        assertTrue(error.getMessage().contains("当前账号已有客服请求处理中"));

        first.close();
        assertDoesNotThrow(() -> limiter.acquire("user1").close());
    }

    @Test
    void aiConcurrencyLimiter_blocksWhenGlobalLimitReached() {
        ChatProperties props = new ChatProperties();
        props.setGlobalMaxConcurrent(1);
        props.setUserMaxConcurrent(1);
        AiConcurrencyLimiter limiter = new AiConcurrencyLimiter(props);

        AiConcurrencyLimiter.Permit first = limiter.acquire("user1");
        RuntimeException error = assertThrows(RuntimeException.class, () -> limiter.acquire("user2"));
        assertTrue(error.getMessage().contains("智能客服繁忙"));

        first.close();
    }

    // ==================== ProbeStreamCallback 首包分流测试 ====================

    @Test
    void probeStreamCallback_contentMode_dispatchesToDownstream() throws InterruptedException {
        CountDownLatch completeLatch = new CountDownLatch(1);
        StringBuilder received = new StringBuilder();
        AtomicReference<ProbeStreamCallback.StreamMode> capturedMode = new AtomicReference<>();

        StreamCallback downstream = new StreamCallback() {
            @Override
            public void onContent(String content) {
                received.append(content);
            }

            @Override
            public void onComplete() {
                completeLatch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                fail("Unexpected error");
            }
        };

        ProbeStreamCallback probe = new ProbeStreamCallback(downstream);
        probe.onContent("Hello");
        probe.onContent(" world");
        probe.onComplete();
        capturedMode.set(probe.getMode());

        assertTrue(completeLatch.await(1, TimeUnit.SECONDS));
        assertEquals(ProbeStreamCallback.StreamMode.CONTENT, capturedMode.get());
        assertEquals("Hello world", received.toString());

        ChatResponse resp = probe.toChatResponse();
        assertEquals("Hello world", resp.getContent());
        assertFalse(resp.hasToolCalls());
    }

    @Test
    void probeStreamCallback_toolCallMode_suppressesContent() {
        AtomicReference<String> receivedContent = new AtomicReference<>("");
        AtomicReference<String> thinkingContent = new AtomicReference<>("");
        AtomicReference<Boolean> onCompleteCalled = new AtomicReference<>(false);

        StreamCallback downstream = new StreamCallback() {
            @Override
            public void onContent(String content) {
                receivedContent.updateAndGet(s -> s + content);
            }

            @Override
            public void onThinking(String content) {
                thinkingContent.updateAndGet(s -> s + content);
            }

            @Override
            public void onComplete() {
                onCompleteCalled.set(true);
            }

            @Override
            public void onError(Throwable error) {
                fail("Unexpected error");
            }
        };

        ProbeStreamCallback probe = new ProbeStreamCallback(downstream);
        probe.onToolCallDelta(0, "call_1", "queryTicket", "");
        probe.onToolCallDelta(0, null, null, "{\"departure\":\"BJ\"}");
        probe.onComplete();

        assertEquals(ProbeStreamCallback.StreamMode.TOOL_CALL, probe.getMode());
        assertEquals("", receivedContent.get());
        assertEquals("", thinkingContent.get());
        assertFalse(onCompleteCalled.get(), "onComplete should NOT be called in TOOL_CALL mode");

        ChatResponse resp = probe.toChatResponse();
        assertTrue(resp.hasToolCalls());
        assertEquals(1, resp.getToolCalls().size());
        ToolCall tc = resp.getToolCalls().get(0);
        assertEquals("call_1", tc.getId());
        assertEquals("queryTicket", tc.getName());
        assertEquals("{\"departure\":\"BJ\"}", tc.getArguments());
    }

    @Test
    void probeStreamCallback_contentBeforeToolCallBecomesThinking() {
        AtomicReference<String> receivedContent = new AtomicReference<>("");
        AtomicReference<String> thinkingContent = new AtomicReference<>("");
        AtomicReference<Boolean> onCompleteCalled = new AtomicReference<>(false);

        StreamCallback downstream = new StreamCallback() {
            @Override
            public void onContent(String content) {
                receivedContent.updateAndGet(s -> s + content);
            }

            @Override
            public void onThinking(String content) {
                thinkingContent.updateAndGet(s -> s + content);
            }

            @Override
            public void onComplete() {
                onCompleteCalled.set(true);
            }

            @Override
            public void onError(Throwable error) {
                fail("Unexpected error");
            }
        };

        ProbeStreamCallback probe = new ProbeStreamCallback(downstream);
        probe.onContent("我先查询知识库。");
        probe.onToolCallDelta(0, "call_1", "searchKnowledge", "");
        probe.onToolCallDelta(0, null, null, "{\"query\":\"退票\"}");
        probe.onComplete();

        assertEquals(ProbeStreamCallback.StreamMode.TOOL_CALL, probe.getMode());
        assertEquals("", receivedContent.get());
        assertEquals("我先查询知识库。", thinkingContent.get());
        assertFalse(onCompleteCalled.get(), "onComplete should NOT be called in TOOL_CALL mode");

        ChatResponse resp = probe.toChatResponse();
        assertTrue(resp.hasToolCalls());
        assertEquals("我先查询知识库。", resp.getContent());
        assertEquals("searchKnowledge", resp.getToolCalls().get(0).getName());
    }

    @Test
    void probeStreamCallback_multipleToolCallsAssembledByIndex() {
        StreamCallback noopDownstream = new StreamCallback() {
            @Override
            public void onContent(String content) {}
            @Override
            public void onComplete() {}
            @Override
            public void onError(Throwable error) {}
        };

        ProbeStreamCallback probe = new ProbeStreamCallback(noopDownstream);
        probe.onToolCallDelta(0, "call_1", "queryTicket", "{\"dep\":");
        probe.onToolCallDelta(1, "call_2", "queryOrder", "{\"sn\":");
        probe.onToolCallDelta(0, null, null, "\"A\"}");
        probe.onToolCallDelta(1, null, null, "\"B\"}");
        probe.onComplete();

        ChatResponse resp = probe.toChatResponse();
        assertEquals(2, resp.getToolCalls().size());
        assertEquals("{\"dep\":\"A\"}", resp.getToolCalls().get(0).getArguments());
        assertEquals("{\"sn\":\"B\"}", resp.getToolCalls().get(1).getArguments());
    }

    // ==================== MockChatClient 测试 ====================

    @Test
    void mockChatClient_chatReturnsScriptedResponse() {
        ChatResponse expected = ChatResponse.builder()
                .content("Hello from mock")
                .finishReason("stop")
                .build();
        MockChatClient client = new MockChatClient(expected);

        ChatResponse actual = client.chat(ChatRequest.builder()
                .message(ChatMessage.user("hi"))
                .build());

        assertEquals("Hello from mock", actual.getContent());
        assertEquals("stop", actual.getFinishReason());
    }

    @Test
    void mockChatClient_streamChatPushesContentTokens() throws InterruptedException {
        MockChatClient client = new MockChatClient(
                ChatResponse.builder().content("Hi").build()
        );

        StringBuilder received = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        client.streamChat(ChatRequest.builder()
                .message(ChatMessage.user("hi"))
                .build(), new StreamCallback() {
            @Override
            public void onContent(String content) {
                received.append(content);
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                fail("Unexpected error");
            }
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals("Hi", received.toString());
    }

    @Test
    void mockChatClient_streamChatCollectToolCallsSuppressesContent() {
        ChatResponse toolCallResp = ChatResponse.builder()
                .toolCall(new ToolCall("call_1", "queryTicket", "{\"departure\":\"BJ\"}"))
                .finishReason("tool_calls")
                .build();
        MockChatClient client = new MockChatClient(toolCallResp);

        StringBuilder received = new StringBuilder();
        AtomicReference<Boolean> onCompleteCalled = new AtomicReference<>(false);

        ChatResponse result = client.streamChatCollect(
                ChatRequest.builder().message(ChatMessage.user("query")).build(),
                new StreamCallback() {
                    @Override
                    public void onContent(String content) {
                        received.append(content);
                    }

                    @Override
                    public void onComplete() {
                        onCompleteCalled.set(true);
                    }

                    @Override
                    public void onError(Throwable error) {
                        fail("Unexpected error");
                    }
                });

        assertTrue(result.hasToolCalls());
        assertEquals("", received.toString());
        assertFalse(onCompleteCalled.get(), "onComplete should NOT be called for tool_call path");
    }
}
