package org.opengoofy.index12306.customerservice.chat;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SseEmitterSender 单元测试：JSON 事件格式与生命周期。
 */
class SseEmitterSenderTests {

    // ==================== 生命周期（真实 emitter） ====================

    @Test
    void sseEmitterSender_onCompleteSetsCompleted() {
        SseEmitter emitter = new SseEmitter(60_000L);
        SseEmitterSender sender = new SseEmitterSender(emitter);

        sender.onComplete();
        assertTrue(sender.isCompleted());
    }

    @Test
    void sseEmitterSender_contentAfterCompleteIsIgnored() {
        SseEmitter emitter = new SseEmitter(60_000L);
        SseEmitterSender sender = new SseEmitterSender(emitter);

        sender.onComplete();
        assertTrue(sender.isCompleted());

        // 应被静默忽略，不应抛出异常
        sender.onContent("ignored content");
        assertTrue(sender.isCompleted());
    }

    @Test
    void sseEmitterSender_onErrorSetsCompleted() {
        SseEmitter emitter = new SseEmitter(60_000L);
        SseEmitterSender sender = new SseEmitterSender(emitter);

        sender.onError(new RuntimeException("test error"));
        assertTrue(sender.isCompleted());
    }

    @Test
    void sseEmitterSender_errorAfterCompleteIsIgnored() {
        SseEmitter emitter = new SseEmitter(60_000L);
        SseEmitterSender sender = new SseEmitterSender(emitter);

        sender.onComplete();
        sender.onError(new RuntimeException("late error"));
        assertTrue(sender.isCompleted());
    }

    @Test
    void sseEmitterSender_startAndCompleteCleansUp() {
        SseEmitter emitter = new SseEmitter(60_000L);
        SseEmitterSender sender = new SseEmitterSender(emitter);

        sender.start();
        sender.onComplete();
        assertTrue(sender.isCompleted());
    }

    @Test
    void sseEmitterSender_emitterAccessible() {
        SseEmitter emitter = new SseEmitter(60_000L);
        SseEmitterSender sender = new SseEmitterSender(emitter);

        assertSame(emitter, sender.emitter());
    }

    @Test
    void chatProperties_rateLimitDefaultIs10() {
        ChatProperties props = new ChatProperties();
        assertEquals(10, props.getRateLimitPerMinute());
    }

    // ==================== JSON 事件格式（mock emitter） ====================

    @Test
    void onContent_sendsTokenJson() {
        SseEmitter emitter = mock(SseEmitter.class);
        SseEmitterSender sender = new SseEmitterSender(emitter);

        sender.onContent("北京到上海");

        String json = captureSentData(emitter);
        assertTrue(json.contains("\"type\":\"token\""), json);
        assertTrue(json.contains("\"content\":\"北京到上海\""), json);
    }

    @Test
    void onToolCall_sendsToolCallJson() {
        SseEmitter emitter = mock(SseEmitter.class);
        SseEmitterSender sender = new SseEmitterSender(emitter);

        sender.onToolCall(new ToolCall("c1", "queryTicket", "{\"from\":\"BJ\",\"to\":\"SH\"}"));

        String json = captureSentData(emitter);
        assertTrue(json.contains("\"type\":\"tool_call\""), json);
        assertTrue(json.contains("\"name\":\"queryTicket\""), json);
        assertTrue(json.contains("\"from\":\"BJ\""), json);
    }

    @Test
    void onToolCall_emptyArgsSendsEmptyObject() {
        SseEmitter emitter = mock(SseEmitter.class);
        SseEmitterSender sender = new SseEmitterSender(emitter);

        sender.onToolCall(new ToolCall("c1", "queryStation", null));

        String json = captureSentData(emitter);
        assertTrue(json.contains("\"type\":\"tool_call\""), json);
        assertTrue(json.contains("\"args\":{}"), json);
    }

    @Test
    void onComplete_sendsDoneJsonWithSessionId() {
        SseEmitter emitter = mock(SseEmitter.class);
        SseEmitterSender sender = new SseEmitterSender(emitter);
        sender.setSessionId("sess-abc");

        sender.onComplete();

        assertTrue(sender.isCompleted());
        String json = captureSentData(emitter);
        assertTrue(json.contains("\"type\":\"done\""), json);
        assertTrue(json.contains("\"sessionId\":\"sess-abc\""), json);
        verify(emitter).complete();
    }

    @Test
    void onError_sendsErrorJson() {
        SseEmitter emitter = mock(SseEmitter.class);
        SseEmitterSender sender = new SseEmitterSender(emitter);

        sender.onError(new RuntimeException("限流"));

        assertTrue(sender.isCompleted());
        String json = captureSentData(emitter);
        assertTrue(json.contains("\"type\":\"error\""), json);
        assertTrue(json.contains("\"message\":\"限流\""), json);
        verify(emitter).complete();
    }

    @Test
    void onToolCall_afterCompleteIsIgnored() {
        SseEmitter emitter = mock(SseEmitter.class);
        SseEmitterSender sender = new SseEmitterSender(emitter);

        sender.onComplete();
        reset(emitter);
        sender.onToolCall(new ToolCall("c1", "echo", "{}"));

        verifyNoInteractions(emitter);
    }

    /** 从 mock emitter 中捕获唯一一次 send 调用的完整数据（拼接所有 SSE 条目） */
    @SuppressWarnings("unchecked")
    private String captureSentData(SseEmitter emitter) {
        try {
            ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
                    ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
            verify(emitter).send(captor.capture());
            Set<SseEmitter.DataWithMediaType> entries = captor.getValue().build();
            StringBuilder sb = new StringBuilder();
            for (SseEmitter.DataWithMediaType d : entries) {
                if (d.getData() != null) {
                    sb.append(d.getData());
                }
            }
            return sb.toString();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}