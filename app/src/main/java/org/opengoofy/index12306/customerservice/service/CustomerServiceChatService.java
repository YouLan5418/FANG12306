package org.opengoofy.index12306.customerservice.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.common.core.UserContext;
import org.opengoofy.index12306.customerservice.chat.AiConcurrencyLimiter;
import org.opengoofy.index12306.customerservice.chat.ChatClient;
import org.opengoofy.index12306.customerservice.chat.ChatMessage;
import org.opengoofy.index12306.customerservice.chat.ChatProperties;
import org.opengoofy.index12306.customerservice.chat.ChatRateLimiter;
import org.opengoofy.index12306.customerservice.chat.SseEmitterSender;
import org.opengoofy.index12306.customerservice.chat.SystemPromptProvider;
import org.opengoofy.index12306.customerservice.fc.FunctionCallingEngine;
import org.opengoofy.index12306.customerservice.fc.ToolExecutionContext;
import org.opengoofy.index12306.customerservice.fc.ToolRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客服对话编排服务。
 *
 * 流程：限流 -> 身份获取 -> SSE 连接 -> 加载历史 + 系统提示 -> FC 引擎运行 -> 保存历史。
 * 引擎在异步线程中运行，SSE 流式推送回复给前端。
 * 断连时 SseEmitterSender 自动取消心跳并清理资源。
 */
@Slf4j
@Service
public class CustomerServiceChatService {

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;
    private final ChatRateLimiter rateLimiter;
    private final ConversationMemoryService memoryService;
    private final AiConcurrencyLimiter concurrencyLimiter;
    private final SystemPromptProvider systemPromptProvider;
    private final ThreadPoolExecutor chatExecutor;

    public CustomerServiceChatService(ChatClient chatClient,
                                      ToolRegistry toolRegistry,
                                      ChatRateLimiter rateLimiter,
                                      ConversationMemoryService memoryService,
                                      AiConcurrencyLimiter concurrencyLimiter,
                                      SystemPromptProvider systemPromptProvider,
                                      ChatProperties chatProperties) {
        this.chatClient = chatClient;
        this.toolRegistry = toolRegistry;
        this.rateLimiter = rateLimiter;
        this.memoryService = memoryService;
        this.concurrencyLimiter = concurrencyLimiter;
        this.systemPromptProvider = systemPromptProvider;
        this.chatExecutor = buildChatExecutor(chatProperties);
    }

    /**
     * 流式对话：限流后异步运行 FC 引擎，SSE 推送回复。
     *
     * @param question 用户问题
     * @param sessionId 会话 ID，为空则新建
     * @param username  登录用户名（可为空，由 UserContext 兜底）
     * @return SseEmitter，前端通过 EventSource 接收
     */
    public SseEmitter streamChat(String question, String rawSessionId, String username) {
        String effectiveUsername = username != null ? username : UserContext.getUsername();
        if (effectiveUsername == null || effectiveUsername.isBlank()) {
            throw new RuntimeException("请先登录");
        }
        rateLimiter.acquire(effectiveUsername);
        AiConcurrencyLimiter.Permit permit = concurrencyLimiter.acquire(effectiveUsername);

        String userId = UserContext.getUserId();
        final String sessionId = (rawSessionId == null || rawSessionId.isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : rawSessionId;

        // SSE 连接 + 心跳
        SseEmitterSender sender = new SseEmitterSender();
        sender.setSessionId(sessionId);
        sender.start();

        ToolExecutionContext ctx = new ToolExecutionContext(effectiveUsername, userId, sessionId);

        try {
            CompletableFuture.runAsync(() -> {
                try {
                    List<ChatMessage> messages = new ArrayList<>(memoryService.loadHistory(sessionId));
                    if (messages.isEmpty()) {
                        messages.add(ChatMessage.system(systemPromptProvider.currentPrompt()));
                    }
                    int newStart = messages.size();
                    messages.add(ChatMessage.user(question));

                    FunctionCallingEngine engine = new FunctionCallingEngine(chatClient, toolRegistry);
                    engine.run(messages, toolRegistry.allDefinitions(), sender, ctx);

                    // 保存新增消息（system prompt 不保存）
                    memoryService.saveHistory(sessionId, effectiveUsername, messages.subList(newStart, messages.size()));
                } catch (Exception e) {
                    log.error("对话处理失败: session={}", sessionId, e);
                    sender.onError(e);
                } finally {
                    permit.close();
                }
            }, chatExecutor);
        } catch (RejectedExecutionException e) {
            permit.close();
            sender.onError(new RuntimeException("智能客服繁忙，请稍后再试"));
        }

        return sender.emitter();
    }

    @PreDestroy
    public void shutdown() {
        chatExecutor.shutdown();
    }

    private ThreadPoolExecutor buildChatExecutor(ChatProperties properties) {
        int coreSize = Math.max(1, properties.getWorkerCoreSize());
        int maxSize = Math.max(coreSize, properties.getWorkerMaxSize());
        int queueCapacity = Math.max(1, properties.getWorkerQueueCapacity());
        AtomicInteger threadIndex = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "customer-service-ai-" + threadIndex.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
