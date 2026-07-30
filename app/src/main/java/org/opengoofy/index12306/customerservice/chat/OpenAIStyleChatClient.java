package org.opengoofy.index12306.customerservice.chat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenAI-compatible chat client supporting Ollama and cloud APIs.
 * Implements synchronous chat, async streaming, and collect-mode streaming
 * with circuit breaker integration and first-packet tool-call routing.
 */
@Slf4j
@Component
public class OpenAIStyleChatClient implements ChatClient {

    private static final String FALLBACK_MESSAGE = "客服暂时不可用，请稍后再试";

    private final ChatProperties properties;
    private final ModelHealthStore modelHealthStore;
    private final HttpClient httpClient;
    private final ExecutorService streamExecutor;

    public OpenAIStyleChatClient(ChatProperties properties, ModelHealthStore modelHealthStore) {
        this.properties = properties;
        this.modelHealthStore = modelHealthStore;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
        this.streamExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "chat-stream");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public String provider() {
        return properties.getProvider();
    }

    // ==================== Synchronous chat ====================

    @Override
    public ChatResponse chat(ChatRequest request) {
        if (!modelHealthStore.allowCall(properties.getModel())) {
            return ChatResponse.builder()
                    .content(FALLBACK_MESSAGE)
                    .finishReason("fallback")
                    .build();
        }
        try {
            String bodyJson = buildRequestBody(request, false);
            HttpRequest httpRequest = buildHttpRequest(bodyJson);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("Chat request failed: status={}, body={}", response.statusCode(), response.body());
                modelHealthStore.markFailure(properties.getModel());
                throw new RuntimeException("Chat request failed: HTTP " + response.statusCode());
            }
            ChatResponse chatResponse = parseSyncResponse(response.body());
            modelHealthStore.markSuccess(properties.getModel());
            return chatResponse;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            modelHealthStore.markFailure(properties.getModel());
            throw new RuntimeException("Chat request failed", e);
        }
    }

    // ==================== Async streaming ====================

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
        if (!modelHealthStore.allowCall(properties.getModel())) {
            callback.onContent(FALLBACK_MESSAGE);
            callback.onComplete();
            return () -> {};
        }
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Future<?> future = streamExecutor.submit(() -> {
            try {
                String bodyJson = buildRequestBody(request, true);
                HttpRequest httpRequest = buildHttpRequest(bodyJson);
                HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    String errorBody = readAll(response.body());
                    log.warn("Stream chat failed: status={}, body={}", response.statusCode(), errorBody);
                    modelHealthStore.markFailure(properties.getModel());
                    callback.onError(new RuntimeException("Stream chat failed: HTTP " + response.statusCode()));
                    return;
                }
                readSseStream(response.body(), callback, null, cancelled);
                if (!cancelled.get()) {
                    modelHealthStore.markSuccess(properties.getModel());
                }
            } catch (Exception e) {
                if (!cancelled.get()) {
                    modelHealthStore.markFailure(properties.getModel());
                    callback.onError(e);
                } else {
                    log.info("Stream chat cancelled: {}", e.getMessage());
                }
            }
        });
        return () -> {
            cancelled.set(true);
            future.cancel(true);
        };
    }

    // ==================== Collect-mode streaming ====================

    @Override
    public ChatResponse streamChatCollect(ChatRequest request, StreamCallback callback) {
        if (!modelHealthStore.allowCall(properties.getModel())) {
            callback.onContent(FALLBACK_MESSAGE);
            callback.onComplete();
            return ChatResponse.builder()
                    .content(FALLBACK_MESSAGE)
                    .finishReason("fallback")
                    .build();
        }
        ProbeStreamCallback probe = new ProbeStreamCallback(callback);
        try {
            String bodyJson = buildRequestBody(request, true);
            HttpRequest httpRequest = buildHttpRequest(bodyJson);
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String errorBody = readAll(response.body());
                log.warn("Stream chat collect failed: status={}, body={}", response.statusCode(), errorBody);
                modelHealthStore.markFailure(properties.getModel());
                probe.onError(new RuntimeException("Stream chat failed: HTTP " + response.statusCode()));
                throw new RuntimeException("Stream chat failed: HTTP " + response.statusCode());
            }
            AtomicBoolean cancelled = new AtomicBoolean(false);
            readSseStream(response.body(), null, probe, cancelled);
            modelHealthStore.markSuccess(properties.getModel());
            return probe.toChatResponse();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            modelHealthStore.markFailure(properties.getModel());
            probe.onError(e);
            throw new RuntimeException("Stream chat collect failed", e);
        }
    }

    // ==================== SSE parsing ====================

    private void readSseStream(InputStream inputStream,
                               StreamCallback callback,
                               ProbeStreamCallback probe,
                               AtomicBoolean cancelled) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while (!cancelled.get() && (line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                parseSseLine(line, callback, probe);
            }
        }
        if (cancelled.get()) {
            return;
        }
        // If stream ended without [DONE], signal completion
        if (probe != null) {
            probe.onComplete();
        } else if (callback != null) {
            callback.onComplete();
        }
    }

    private void parseSseLine(String line, StreamCallback callback, ProbeStreamCallback probe) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("data:")) {
            return;
        }
        String payload = trimmed.substring(5).trim();
        if ("[DONE]".equals(payload)) {
            if (probe != null) {
                probe.onComplete();
            } else if (callback != null) {
                callback.onComplete();
            }
            return;
        }
        try {
            JSONObject json = JSON.parseObject(payload);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return;
            }
            JSONObject choice = choices.getJSONObject(0);
            JSONObject delta = choice.getJSONObject("delta");
            if (delta != null) {
                String content = delta.getString("content");
                if (content != null && !content.isEmpty()) {
                    if (probe != null) {
                        probe.onContent(content);
                    } else if (callback != null) {
                        callback.onContent(content);
                    }
                }
                JSONArray toolCalls = delta.getJSONArray("tool_calls");
                if (toolCalls != null && probe != null) {
                    for (int i = 0; i < toolCalls.size(); i++) {
                        JSONObject tc = toolCalls.getJSONObject(i);
                        int index = tc.getIntValue("index", 0);
                        String id = tc.getString("id");
                        JSONObject function = tc.getJSONObject("function");
                        String name = function != null ? function.getString("name") : null;
                        String args = function != null ? function.getString("arguments") : null;
                        probe.onToolCallDelta(index, id, name, args);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse SSE line: {}", line, e);
        }
    }

    // ==================== Request building ====================

    private String buildRequestBody(ChatRequest request, boolean stream) {
        JSONObject body = new JSONObject();
        body.put("model", properties.getModel());
        if (stream) {
            body.put("stream", true);
        }
        JSONArray messages = new JSONArray();
        for (ChatMessage msg : request.getMessages()) {
            JSONObject m = new JSONObject();
            m.put("role", toOpenAiRole(msg.getRole()));
            m.put("content", msg.getContent());
            if (msg.getRole() == ChatMessage.Role.TOOL && msg.getToolCallId() != null) {
                m.put("tool_call_id", msg.getToolCallId());
            }
            messages.add(m);
        }
        body.put("messages", messages);
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        } else {
            body.put("temperature", properties.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            JSONArray tools = new JSONArray();
            for (ToolDefinition tool : request.getTools()) {
                JSONObject t = new JSONObject();
                t.put("type", "function");
                JSONObject fn = new JSONObject();
                fn.put("name", tool.getName());
                fn.put("description", tool.getDescription());
                fn.put("parameters", tool.getParameters());
                t.put("function", fn);
                tools.add(t);
            }
            body.put("tools", tools);
        }
        return body.toJSONString();
    }

    private HttpRequest buildHttpRequest(String bodyJson) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8));
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.getApiKey());
        }
        return builder.build();
    }

    // ==================== Response parsing ====================

    private ChatResponse parseSyncResponse(String responseBody) {
        JSONObject json = JSON.parseObject(responseBody);
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return ChatResponse.builder().content("").finishReason("empty").build();
        }
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.getJSONObject("message");
        String content = message != null ? message.getString("content") : "";
        String finishReason = choice.getString("finish_reason");
        List<ToolCall> toolCalls = new ArrayList<>();
        if (message != null) {
            JSONArray tcArray = message.getJSONArray("tool_calls");
            if (tcArray != null) {
                for (int i = 0; i < tcArray.size(); i++) {
                    JSONObject tc = tcArray.getJSONObject(i);
                    String id = tc.getString("id");
                    JSONObject function = tc.getJSONObject("function");
                    String name = function != null ? function.getString("name") : null;
                    String args = function != null ? function.getString("arguments") : null;
                    toolCalls.add(new ToolCall(id, name, args));
                }
            }
        }
        return ChatResponse.builder()
                .content(content != null ? content : "")
                .toolCalls(toolCalls)
                .finishReason(finishReason)
                .build();
    }

    // ==================== Helpers ====================

    private String toOpenAiRole(ChatMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }

    private String readAll(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    @PreDestroy
    public void shutdown() {
        streamExecutor.shutdownNow();
    }
}