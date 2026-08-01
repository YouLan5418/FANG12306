package org.opengoofy.index12306.customerservice.fc;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.customerservice.chat.ChatClient;
import org.opengoofy.index12306.customerservice.chat.ChatMessage;
import org.opengoofy.index12306.customerservice.chat.ChatRequest;
import org.opengoofy.index12306.customerservice.chat.ChatResponse;
import org.opengoofy.index12306.customerservice.chat.ProbeStreamCallback;
import org.opengoofy.index12306.customerservice.chat.StreamCallback;
import org.opengoofy.index12306.customerservice.chat.ToolCall;
import org.opengoofy.index12306.customerservice.chat.ToolDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Function Calling 引擎，实现 Agent 循环。
 *
 * 始终使用 streamChatCollect + ProbeStreamCallback 做首包分流，消除双倍调用：
 * - 首个 delta 含 tool_calls -> TOOL_CALL 模式：执行工具，将结果回填 LLM，继续循环
 * - 首个 delta 含 content -> CONTENT 模式：已透传给用户，直接结束
 *
 * 工具异常（未知工具、JSON 解析失败、执行异常、超时）全部 catch 后回填 LLM，对话不崩溃。
 * MAX_ITERATIONS 防止 LLM 反复调工具死循环。
 */
@Slf4j
public class FunctionCallingEngine {

    private static final int DEFAULT_MAX_ITERATIONS = 5;
    private static final long DEFAULT_TOOL_TIMEOUT_MS = 10_000;

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;
    private final int maxIterations;
    private final long toolTimeoutMs;

    public FunctionCallingEngine(ChatClient chatClient, ToolRegistry toolRegistry) {
        this(chatClient, toolRegistry, DEFAULT_MAX_ITERATIONS, DEFAULT_TOOL_TIMEOUT_MS);
    }

    public FunctionCallingEngine(ChatClient chatClient, ToolRegistry toolRegistry,
                                 int maxIterations, long toolTimeoutMs) {
        this.chatClient = chatClient;
        this.toolRegistry = toolRegistry;
        this.maxIterations = maxIterations;
        this.toolTimeoutMs = toolTimeoutMs;
    }

    /**
     * 运行 Agent 循环。
     *
     * @param messages 对话历史（会被引擎追加 assistant tool_call 和 tool result 消息）
     * @param tools    可用工具定义列表
     * @param callback 流式回调（接收最终回复的 content）
     * @param ctx      工具执行上下文（用户身份）
     */
    public void run(List<ChatMessage> messages, List<ToolDefinition> tools,
                    StreamCallback callback, ToolExecutionContext ctx) {
        for (int i = 0; i < maxIterations; i++) {
            ProbeStreamCallback probe = new ProbeStreamCallback(callback);
            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .tools(tools)
                    .userId(sanitizeUserId(ctx.getUserId()))
                    .build();
            ChatResponse resp = chatClient.streamChatCollect(request, probe);

            if (resp.hasToolCalls()) {
                // 将 LLM 的 tool_call 请求加入对话历史
                messages.add(ChatMessage.assistantWithToolCalls(resp.getContent(), resp.getToolCalls()));
                // 逐个执行工具，结果回填
                for (ToolCall tc : resp.getToolCalls()) {
                    callback.onThinking("正在调用业务工具：" + tc.getName());
                    callback.onToolCall(tc);
                    String result = executeToolSafe(tc, ctx);
                    callback.onToolResult(tc, result);
                    messages.add(ChatMessage.tool(tc.getId(), tc.getName(), result));
                }
                // 继续循环，让 LLM 基于工具结果生成下一轮
            } else {
                // CONTENT 模式：最终回复已通过 probe 透传给 callback，onComplete 已调用
                return;
            }
        }
        callback.onError(new RuntimeException("超过最大工具调用轮数(" + maxIterations + ")"));
    }

    private String sanitizeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        String sanitized = ("u_" + userId).replaceAll("[^a-zA-Z0-9\\-_]", "_");
        return sanitized.length() <= 512 ? sanitized : sanitized.substring(0, 512);
    }

    /**
     * 安全执行工具：超时控制 + 异常回填。
     * 任何异常都转化为 JSON 错误信息返回给 LLM，不中断对话。
     */
    private String executeToolSafe(ToolCall tc, ToolExecutionContext ctx) {
        ToolExecutor executor = toolRegistry.get(tc.getName());
        if (executor == null) {
            return "{\"error\":\"未知工具: " + tc.getName() + "\"}";
        }
        Map<String, Object> args = parseArgs(tc.getArguments());
        try {
            return CompletableFuture
                    .supplyAsync(() -> executor.execute(args, ctx))
                    .orTimeout(toolTimeoutMs, TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                log.warn("工具执行超时: {}", tc.getName());
                return "{\"error\":\"工具执行超时\"}";
            }
            String msg = cause != null ? cause.getMessage() : e.getMessage();
            log.warn("工具执行失败: {} -> {}", tc.getName(), msg);
            return "{\"error\":\"工具执行失败: " + msg + "\"}";
        } catch (Exception e) {
            log.warn("工具执行失败: {} -> {}", tc.getName(), e.getMessage());
            return "{\"error\":\"工具执行失败: " + e.getMessage() + "\"}";
        }
    }

    private Map<String, Object> parseArgs(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(arguments);
            return parsed != null ? parsed : new HashMap<>();
        } catch (Exception e) {
            log.warn("工具参数解析失败: {}", arguments);
            return new HashMap<>();
        }
    }
}
