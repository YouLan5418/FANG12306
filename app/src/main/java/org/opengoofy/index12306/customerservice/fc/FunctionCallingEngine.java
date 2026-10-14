package org.opengoofy.index12306.customerservice.fc;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.customerservice.chat.ChatClient;
import org.opengoofy.index12306.customerservice.chat.ChatMessage;
import org.opengoofy.index12306.customerservice.chat.ChatRequest;
import org.opengoofy.index12306.customerservice.chat.ChatResponse;
import org.opengoofy.index12306.customerservice.chat.StreamCallback;
import org.opengoofy.index12306.customerservice.chat.StreamCancellationHandle;
import org.opengoofy.index12306.customerservice.chat.ToolCall;
import org.opengoofy.index12306.customerservice.chat.ToolDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Function Calling 引擎，实现 Agent 循环。
 *
 * 每轮只调用一次 streamChatCollect，由客户端组装流式工具调用：
 * - 首个 delta 含 tool_calls -> TOOL_CALL 模式：执行工具，将结果回填 LLM，继续循环
 * - 无工具调用：保存完整回复，结束本轮
 *
 * 工具异常（未知工具、JSON 解析失败、执行异常、超时）全部 catch 后回填 LLM，对话不崩溃。
 * MAX_ITERATIONS 防止 LLM 反复调工具死循环。
 */
@Slf4j
public class FunctionCallingEngine {

    private static final int DEFAULT_MAX_ITERATIONS = 5;
    private static final long DEFAULT_TOOL_TIMEOUT_MS = 10_000;

    private static final ExecutorService TOOL_EXECUTOR = new ThreadPoolExecutor(4, 8, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), runnable -> {
                Thread thread = new Thread(runnable, "customer-service-tool");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

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
            checkCancellation(callback);
            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .tools(tools)
                    .userId(sanitizeUserId(ctx.getUserId()))
                    .build();
            ChatResponse resp = chatClient.streamChatCollect(request, callback);

            checkCancellation(callback);
            if (resp.hasToolCalls()) {
                // 将 LLM 的 tool_call 请求加入对话历史
                messages.add(ChatMessage.assistantWithToolCalls(resp.getContent(), resp.getToolCalls()));
                // 逐个执行工具，结果回填
                for (ToolCall tc : resp.getToolCalls()) {
                    checkCancellation(callback);
                    callback.onThinking("正在调用业务工具：" + tc.getName());
                    callback.onToolCall(tc);
                    String result = executeToolSafe(tc, ctx, callback);
                    callback.onToolResult(tc, result);
                    messages.add(ChatMessage.tool(tc.getId(), tc.getName(), result));
                }
                // 继续循环，让 LLM 基于工具结果生成下一轮
            } else {
                messages.add(ChatMessage.assistant(resp.getContent()));
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
    private void checkCancellation(StreamCallback callback) {
        if (callback.isCancelled() || Thread.currentThread().isInterrupted()) throw new CancellationException("客服请求已取消");
    }

    private String executeToolSafe(ToolCall tc, ToolExecutionContext ctx, StreamCallback callback) {
        ToolExecutor executor = toolRegistry.get(tc.getName());
        if (executor == null) return JSON.toJSONString(Map.of("error", "未知工具: " + tc.getName()));
        Future<String> future = null;
        StreamCancellationHandle registration = () -> {};
        try {
            Map<String, Object> args = parseArgs(tc.getArguments());
            future = TOOL_EXECUTOR.submit(() -> executor.execute(args, ctx));
            Future<String> task = future;
            registration = callback.onCancellation(() -> task.cancel(true));
            checkCancellation(callback);
            return future.get(toolTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("客服请求已取消");
        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            String message = e instanceof TimeoutException ? "工具执行超时" : "工具执行失败，请稍后重试";
            log.warn("工具 {} 执行失败", tc.getName(), e);
            return JSON.toJSONString(Map.of("error", message));
        } finally {
            registration.cancel();
            if (future != null && !future.isDone()) future.cancel(true);
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
            throw new IllegalArgumentException("工具参数必须为合法JSON对象", e);
        }
    }
}
