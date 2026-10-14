package org.opengoofy.index12306.customerservice.chat;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * StreamCallback 适配器，将内容 token 推送到 SseEmitter。
 * 管理 15 秒心跳、60 秒超时，以及在完成/出错/客户端断连时清理资源。
 *
     * SSE 事件统一为 JSON：
     *   {"type":"token","content":"..."}
     *   {"type":"thinking","content":"..."}
     *   {"type":"tool_call","name":"...","args":{...}}
     *   {"type":"tool_result","name":"...","args":{...},"result":{},"sources":[]}
     *   {"type":"done","sessionId":"..."}
     *   {"type":"error","message":"..."}
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
    private String sessionId;
    private ScheduledFuture<?> heartbeatFuture;
    private volatile boolean completed = false;
    private volatile boolean cancelled;
    private boolean deferCompletion;
    private final java.util.List<Runnable> cancellationActions = new java.util.ArrayList<>();

    public void deferCompletion() { deferCompletion = true; }

    @Override public boolean isCancelled() { return cancelled || Thread.currentThread().isInterrupted(); }

    @Override public StreamCancellationHandle onCancellation(Runnable action) {
        synchronized (cancellationActions) {
            if (!cancelled) {
                cancellationActions.add(action);
                return () -> { synchronized (cancellationActions) { cancellationActions.remove(action); } };
            }
        }
        action.run();
        return () -> {};
    }

    private void cancelWork() {
        List<Runnable> actions;
        synchronized (cancellationActions) {
            if (cancelled) return;
            cancelled = true;
            completed = true;
            actions = new ArrayList<>(cancellationActions);
            cancellationActions.clear();
        }
        stopHeartbeat();
        for (Runnable action : actions) {
            try { action.run(); } catch (Exception e) { log.debug("取消客服任务失败", e); }
        }
    }

    public SseEmitterSender() {
        this.emitter = new SseEmitter(TIMEOUT_MS);
    }

    /** 测试用：传入预配置的 emitter */
    public SseEmitterSender(SseEmitter emitter) {
        this.emitter = emitter;
    }

    /** 设置会话 ID，用于 done 事件回传给前端 */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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
                        cancelWork();
                    }
                },
                HEARTBEAT_INTERVAL_S, HEARTBEAT_INTERVAL_S, TimeUnit.SECONDS);

        emitter.onCompletion(() -> {
            if (!completed) cancelWork();
            stopHeartbeat();
        });
        emitter.onError(error -> cancelWork());
        emitter.onTimeout(() -> {
            cancelWork();
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
            emitter.send(SseEmitter.event().data(jsonToken(content)));
        } catch (Exception e) {
            log.debug("SSE 发送失败，客户端可能已断连: {}", e.getMessage());
            cancelWork();
        }
    }

    @Override
    public void onThinking(String content) {
        if (completed) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data(jsonThinking(content)));
        } catch (Exception e) {
            log.debug("SSE 发送失败，客户端可能已断连: {}", e.getMessage());
            cancelWork();
        }
    }

    @Override
    public void onToolCall(ToolCall toolCall) {
        if (completed) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data(jsonToolCall(toolCall)));
        } catch (Exception e) {
            log.debug("SSE 发送失败，客户端可能已断连: {}", e.getMessage());
            cancelWork();
        }
    }

    @Override
    public void onToolResult(ToolCall toolCall, String result) {
        if (completed) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data(jsonToolResult(toolCall, result)));
        } catch (Exception e) {
            log.debug("SSE 发送失败，客户端可能已断连: {}", e.getMessage());
            cancelWork();
        }
    }

    @Override
    public void onComplete() {
        if (!deferCompletion) finish();
    }

    public void finish() {
        if (completed) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data(jsonDone()));
        } catch (Exception ignored) {
            // 客户端可能已断连，仍需完成 emitter 释放资源
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
        try {
            emitter.send(SseEmitter.event().data(jsonError(error)));
        } catch (Exception ignored) {
        }
        cancelWork();
        emitter.complete();
    }

    public boolean isCompleted() {
        return completed;
    }

    // ==================== JSON 事件构建 ====================

    private String jsonToken(String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "token");
        payload.put("content", content);
        return JSON.toJSONString(payload);
    }

    private String jsonThinking(String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "thinking");
        payload.put("content", content);
        return JSON.toJSONString(payload);
    }

    private String jsonToolCall(ToolCall toolCall) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "tool_call");
        payload.put("name", toolCall.getName());
        payload.put("args", parseArgs(toolCall.getArguments()));
        return JSON.toJSONString(payload);
    }

    private String jsonToolResult(ToolCall toolCall, String result) {
        Object parsedResult = parseJson(result);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "tool_result");
        payload.put("name", toolCall.getName());
        payload.put("args", parseArgs(toolCall.getArguments()));
        payload.put("result", parsedResult);
        payload.put("sources", extractSources(toolCall.getName(), parsedResult));
        return JSON.toJSONString(payload);
    }

    private String jsonDone() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "done");
        payload.put("sessionId", sessionId);
        return JSON.toJSONString(payload);
    }

    private String jsonError(Throwable error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("message", error.getMessage() != null ? error.getMessage() : "unknown error");
        return JSON.toJSONString(payload);
    }

    /** 解析工具参数 JSON 字符串为对象，解析失败则原样返回 */
    private Object parseArgs(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Collections.emptyMap();
        }
        return parseJson(arguments);
    }

    private Object parseJson(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Object parsed = JSON.parse(content);
            return parsed != null ? parsed : Collections.emptyMap();
        } catch (Exception e) {
            return content;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSources(String toolName, Object result) {
        if (!"searchKnowledge".equals(toolName) || !(result instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> sources = new ArrayList<>();
        int index = 1;
        for (Object item : list) {
            if (item instanceof Map<?, ?> raw) {
                Map<String, Object> source = new LinkedHashMap<>();
                source.put("title", "知识片段 " + index++);
                source.put("content", raw.get("content"));
                source.put("score", raw.get("score"));
                sources.add(source);
            }
        }
        return sources;
    }
}
