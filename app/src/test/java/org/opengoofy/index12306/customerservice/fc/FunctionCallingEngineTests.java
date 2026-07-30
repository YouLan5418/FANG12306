package org.opengoofy.index12306.customerservice.fc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.opengoofy.index12306.customerservice.chat.ChatClient;
import org.opengoofy.index12306.customerservice.chat.ChatMessage;
import org.opengoofy.index12306.customerservice.chat.ChatResponse;
import org.opengoofy.index12306.customerservice.chat.StreamCallback;
import org.opengoofy.index12306.customerservice.chat.ToolCall;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * FunctionCallingEngine 单元测试：首包分流、工具循环、异常回填、超时、最大迭代。
 */
class FunctionCallingEngineTests {

    private ChatClient chatClient;
    private ToolExecutionContext ctx;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        ctx = new ToolExecutionContext("user1", "100", "session1");
    }

    /** 收集流式回调事件的回调 */
    private static class CollectingCallback implements StreamCallback {
        final StringBuilder content = new StringBuilder();
        final AtomicBoolean completed = new AtomicBoolean(false);
        volatile Throwable error;

        @Override
        public void onContent(String c) { content.append(c); }

        @Override
        public void onComplete() { completed.set(true); }

        @Override
        public void onError(Throwable e) { error = e; }
    }

    /** 模拟 LLM 返回纯文本内容（CONTENT 模式） */
    private Answer<ChatResponse> contentAnswer(String text) {
        return inv -> {
            StreamCallback cb = inv.getArgument(1);
            cb.onContent(text);
            cb.onComplete();
            return ChatResponse.builder().content(text).build();
        };
    }

    @Test
    void contentMode_directReplyNoDoubleCall() {
        when(chatClient.streamChatCollect(any(), any())).thenAnswer(contentAnswer("Hello"));

        CollectingCallback callback = new CollectingCallback();
        new FunctionCallingEngine(chatClient, new ToolRegistry(List.of()))
                .run(new ArrayList<>(List.of(ChatMessage.user("hi"))), List.of(), callback, ctx);

        assertEquals("Hello", callback.content.toString());
        assertTrue(callback.completed.get());
        // 无双倍调用：只调一次 streamChatCollect
        verify(chatClient, times(1)).streamChatCollect(any(), any());
    }

    @Test
    void toolCallMode_executesToolAndLoops() {
        // 第一轮返回 tool_call，第二轮返回最终回复
        when(chatClient.streamChatCollect(any(), any()))
                .thenReturn(ChatResponse.builder()
                        .toolCall(new ToolCall("c1", "echo", "{}"))
                        .build())
                .thenAnswer(contentAnswer("Done"));

        ToolExecutor echo = mock(ToolExecutor.class);
        when(echo.name()).thenReturn("echo");
        when(echo.execute(any(), any())).thenReturn("{\"result\":\"ok\"}");

        CollectingCallback callback = new CollectingCallback();
        new FunctionCallingEngine(chatClient, new ToolRegistry(List.of(echo)))
                .run(new ArrayList<>(List.of(ChatMessage.user("hi"))), List.of(), callback, ctx);

        assertEquals("Done", callback.content.toString());
        verify(echo, times(1)).execute(any(), any());
        verify(chatClient, times(2)).streamChatCollect(any(), any());
    }

    @Test
    void toolException_errorBackFilledToLlm() {
        when(chatClient.streamChatCollect(any(), any()))
                .thenReturn(ChatResponse.builder()
                        .toolCall(new ToolCall("c1", "broken", "{}"))
                        .build())
                .thenAnswer(contentAnswer("Recovered"));

        ToolExecutor broken = mock(ToolExecutor.class);
        when(broken.name()).thenReturn("broken");
        when(broken.execute(any(), any())).thenThrow(new RuntimeException("DB down"));

        CollectingCallback callback = new CollectingCallback();
        new FunctionCallingEngine(chatClient, new ToolRegistry(List.of(broken)))
                .run(new ArrayList<>(List.of(ChatMessage.user("hi"))), List.of(), callback, ctx);

        // 工具异常被回填，LLM 仍能生成最终回复
        assertEquals("Recovered", callback.content.toString());
    }

    @Test
    void toolTimeout_returnsTimeoutError() {
        when(chatClient.streamChatCollect(any(), any()))
                .thenReturn(ChatResponse.builder()
                        .toolCall(new ToolCall("c1", "slow", "{}"))
                        .build())
                .thenAnswer(contentAnswer("Done"));

        ToolExecutor slow = mock(ToolExecutor.class);
        when(slow.name()).thenReturn("slow");
        when(slow.execute(any(), any())).thenAnswer(inv -> {
            Thread.sleep(500);
            return "{}";
        });

        CollectingCallback callback = new CollectingCallback();
        // 100ms 超时
        new FunctionCallingEngine(chatClient, new ToolRegistry(List.of(slow)), 5, 100)
                .run(new ArrayList<>(List.of(ChatMessage.user("hi"))), List.of(), callback, ctx);

        // 超时后错误回填，LLM 生成最终回复
        assertEquals("Done", callback.content.toString());
    }

    @Test
    void maxIterations_callsOnError() {
        when(chatClient.streamChatCollect(any(), any()))
                .thenReturn(ChatResponse.builder()
                        .toolCall(new ToolCall("c1", "echo", "{}"))
                        .build());

        ToolExecutor echo = mock(ToolExecutor.class);
        when(echo.name()).thenReturn("echo");
        when(echo.execute(any(), any())).thenReturn("{}");

        CollectingCallback callback = new CollectingCallback();
        new FunctionCallingEngine(chatClient, new ToolRegistry(List.of(echo)), 2, 10_000)
                .run(new ArrayList<>(List.of(ChatMessage.user("hi"))), List.of(), callback, ctx);

        assertNotNull(callback.error);
        assertTrue(callback.error.getMessage().contains("最大工具调用轮数"));
        verify(chatClient, times(2)).streamChatCollect(any(), any());
    }

    @Test
    void unknownTool_returnsErrorResultAndContinues() {
        when(chatClient.streamChatCollect(any(), any()))
                .thenReturn(ChatResponse.builder()
                        .toolCall(new ToolCall("c1", "nonexistent", "{}"))
                        .build())
                .thenAnswer(contentAnswer("OK"));

        CollectingCallback callback = new CollectingCallback();
        // 空注册表，工具不存在
        new FunctionCallingEngine(chatClient, new ToolRegistry(List.of()))
                .run(new ArrayList<>(List.of(ChatMessage.user("hi"))), List.of(), callback, ctx);

        assertEquals("OK", callback.content.toString());
    }
}