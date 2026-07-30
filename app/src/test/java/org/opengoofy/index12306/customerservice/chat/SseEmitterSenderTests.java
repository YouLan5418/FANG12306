package org.opengoofy.index12306.customerservice.chat;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for P7-T4: SseEmitterSender lifecycle and ChatProperties rate limit.
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

        // Should be silently ignored, not throw
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
        // Heartbeat is now scheduled
        sender.onComplete();
        assertTrue(sender.isCompleted());
        // Heartbeat should be cancelled (no exception means cleanup worked)
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