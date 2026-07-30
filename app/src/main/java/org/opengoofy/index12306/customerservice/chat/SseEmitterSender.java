package org.opengoofy.index12306.customerservice.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * StreamCallback 适配器，将内容 token 推送到 SseEmitter。
 * 管理 15 秒心跳、60 秒超时，以及在完成/出错/客户端断连时清理资源。
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

    /** 测试用：传入预配置的 emitter */
    public SseEmitterSender(SseEmitter emitter) {
        this.emitter = emitter;
    }

    /**
     * 启动心跳和生命周期回调。必须在流式开始前调用。
     */
    public SseEmitterSender start() {
        heartbeatFuture = HEARTBEAT_SCHEDULER.scheduleAtFixedRate(
                () -> {
                    try {
                        emitter.send(SseEmitter.event().comment("heartbeat"));
                    } catch (Exception ignored) {
                        // 客户端可能已断连
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

    /** 返回底层 SseEmitter，供 Controller 返回 */
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
            log.debug("SSE 发送失败，客户端可能已断连: {}", e.getMessage());
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