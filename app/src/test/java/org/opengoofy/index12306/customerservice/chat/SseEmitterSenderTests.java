package org.opengoofy.index12306.customerservice.chat;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P7-T4 单元测试：SseEmitterSender 生命周期和 ChatProperties 限流配置。
 */
class SseEmitterSenderTests {

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
        // 心跳任务已调度
        sender.onComplete();
        assertTrue(sender.isCompleted());
        // 心跳应被取消（无异常即代表清理成功）
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
}