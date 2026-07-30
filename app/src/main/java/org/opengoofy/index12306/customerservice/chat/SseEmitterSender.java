package org.opengoofy.index12306.customerservice.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * StreamCallback adapter that pushes content tokens to an SseEmitter.
 * Manages 15s heartbeat, 60s timeout, and clean teardown on
 * completion / error / client disconnect.
 */
@Slf4j
public class SseEmitterSender implements StreamCallback {

    private static final long TIMEOUT_MS = 60_000L;
    private static final long HEARTBEAT_INTERVAL_S = 15L;

    private static final ScheduledExecutorService HEARTBEAT_SCHEDULER =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    private final SseEmitter emitter;
    private ScheduledFuture<?> heartbeatFuture;
    private volatile boolean completed = false;

    public SseEmitterSender() {
        this.emitter = new SseEmitter(TIMEOUT_MS);
    }

    /** For testing: use a pre-configured emitter. */
    public SseEmitterSender(SseEmitter emitter) {
        this.emitter = emitter;
    }

    /**
     * Initialize heartbeat and lifecycle callbacks.
     * Must be called before streaming starts.
     */
    public SseEmitterSender start() {
        heartbeatFuture = HEARTBEAT_SCHEDULER.scheduleAtFixedRate(
                () -> {
                    try {
                        emitter.send(SseEmitter.event().comment("heartbeat"));
                    } catch (Exception ignored) {
                        // client likely disconnected
                    }
                },
                HEARTBEAT_INTERVAL_S, HEARTBEAT_INTERVAL_S, TimeUnit.SECONDS);

        emitter.onCompletion(this::stopHeartbeat);
        emitter.onTimeout(() -> {
            stopHeartbeat();
            emitter.complete();
        });
        return this;
    }

    /**
     * Returns the underlying SseEmitter for controller return value.
     */
    public SseEmitter emitter() {
        return emitter;
    }

    private void stopHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(true);
            heartbeatFuture = null;
        }
    }

    @Override
    public void onContent(String content) {
        if (completed) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data(content));
        } catch (Exception e) {
            log.debug("SSE send failed, client may have disconnected: {}", e.getMessage());
            completed = true;
            stopHeartbeat();
        }
    }

    @Override
    public void onComplete() {
        if (completed) {
            return;
        }
        completed = true;
        stopHeartbeat();
        emitter.complete();
    }

    @Override
    public void onError(Throwable error) {
        if (completed) {
            return;
        }
        completed = true;
        stopHeartbeat();
        try {
            emitter.send(SseEmitter.event().name("error").data(error.getMessage() != null ? error.getMessage() : "unknown error"));
        } catch (Exception ignored) {
        }
        emitter.complete();
    }

    public boolean isCompleted() {
        return completed;
    }
}