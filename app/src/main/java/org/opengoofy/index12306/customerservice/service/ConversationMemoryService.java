package org.opengoofy.index12306.customerservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.customerservice.chat.ChatClient;
import org.opengoofy.index12306.customerservice.chat.ChatMessage;
import org.opengoofy.index12306.customerservice.chat.ChatRequest;
import org.opengoofy.index12306.customerservice.chat.ChatResponse;
import org.opengoofy.index12306.customerservice.chat.ToolCall;
import org.opengoofy.index12306.framework.convention.exception.ClientException;
import org.opengoofy.index12306.customerservice.dao.entity.ChatMessageDO;
import org.opengoofy.index12306.customerservice.dao.entity.ChatSessionDO;
import org.opengoofy.index12306.customerservice.dao.mapper.ChatMessageMapper;
import org.opengoofy.index12306.customerservice.dao.mapper.ChatSessionMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 对话历史服务（JDBC 直读 + 摘要加锁）。
 *
 * 放弃 Redis 缓存 + 异步持久化，改为直读 DB + 同步写入，
 * 一次性消除并发覆盖、TTL 堆积、异步丢数据三个问题。
 * token 超阈值时触发 LLM 摘要，Redisson 分布式锁防并发摘要。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatClient chatClient;
    private final RedissonClient redissonClient;

    /** 直读最近 N 条消息 */
    private static final int MAX_MESSAGES = 20;
    /** 启发式 token 阈值，超过则触发摘要 */
    private static final int SUMMARY_THRESHOLD = 2000;
    /** 摘要锁等待时间 */
    private static final long LOCK_WAIT_MS = 0;

    /**
     * 加载对话历史：直读 DB -> 检查摘要 -> 返回「摘要 + 水位线之后的消息」。
     *
     * @param sessionId 会话 ID
     * @return 对话消息列表（不含 system prompt，由调用方添加）
     */
    public void prepareSession(String sessionId, String username) {
        if (username == null || username.isBlank() || sessionId == null || !sessionId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new ClientException("用户或会话编号不合法");
        }
        ensureSession(sessionId, username);
    }

    public List<ChatMessage> loadHistory(String sessionId, String username) {
        ChatSessionDO session = getSession(sessionId);
        if (session == null || !Objects.equals(username, session.getUsername()) || username == null) {
            throw new ClientException("无权访问此客服会话");
        }
        return loadHistory(sessionId);
    }

    // 仅供本包内部处理和历史记录测试；外部入口必须使用带身份的重载。
    List<ChatMessage> loadHistory(String sessionId) {
        List<ChatMessageDO> recent = new ArrayList<>(chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageDO>()
                        .eq(ChatMessageDO::getSessionId, sessionId)
                        .orderByDesc(ChatMessageDO::getId)
                        .last("LIMIT " + MAX_MESSAGES)));
        Collections.reverse(recent);

        // 检查是否需要摘要
        if (heuristicTokenCount(recent) > SUMMARY_THRESHOLD) {
            summarize(recent, sessionId);
        }

        return buildMessagesWithSummary(recent, sessionId);
    }

    /**
     * 同步保存对话历史：确保会话存在 -> 逐条写入新消息。
     *
     * @param sessionId   会话 ID
     * @param username    用户名
     * @param newMessages 新增消息列表（user + assistant + tool）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveHistory(String sessionId, String username, List<ChatMessage> newMessages) {
        if (username == null || username.isBlank()) throw new ClientException("用户未登录");
        ensureSession(sessionId, username);
        Map<String, ToolCall> calls = new java.util.HashMap<>();
        for (ChatMessage msg : newMessages) {
            ChatMessageDO record = new ChatMessageDO();
            record.setSessionId(sessionId);
            record.setRole(msg.getRole().name().toLowerCase());
            record.setContent(msg.getContent());
            if (msg.getRole() == ChatMessage.Role.SYSTEM) continue;
            if (msg.getRole() == ChatMessage.Role.ASSISTANT && msg.getToolCalls() != null) {
                record.setToolArgs(JSON.toJSONString(Map.of("toolCalls", msg.getToolCalls())));
                for (ToolCall call : msg.getToolCalls()) calls.put(call.getId(), call);
            }
            if (msg.getRole() == ChatMessage.Role.TOOL) {
                record.setToolName(msg.getToolName());
                ToolCall call = calls.get(msg.getToolCallId());
                record.setToolArgs(JSON.toJSONString(Map.of("toolCallId", msg.getToolCallId(),
                        "arguments", call == null || call.getArguments() == null ? "{}" : call.getArguments())));
                record.setToolResult(msg.getContent());
            }
            chatMessageMapper.insert(record);
        }
        ChatSessionDO session = getSession(sessionId);
        if (session != null) {
            session.setUpdateTime(new java.util.Date());
            chatSessionMapper.updateById(session);
        }
    }

    /**
     * 摘要：Redisson 分布式锁防并发 -> LLM 生成摘要 -> 保存摘要 + 水位线。
     */
    private void summarize(List<ChatMessageDO> recent, String sessionId) {
        RLock lock = redissonClient.getLock("chat:summary:" + sessionId);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_MS, TimeUnit.MILLISECONDS);
            if (!locked) {
                return; // 其他线程正在摘要，跳过
            }

            ChatSessionDO session = getSession(sessionId);
            Long watermark = session != null ? session.getSummaryMessageId() : null;

            // 取水位线之后未摘要的消息
            List<ChatMessageDO> unsummarized = new ArrayList<>();
            for (ChatMessageDO d : recent) {
                if (watermark == null || d.getId() > watermark) {
                    unsummarized.add(d);
                }
            }

            if (heuristicTokenCount(unsummarized) <= SUMMARY_THRESHOLD) {
                return; // double-check
            }

            // 取前半部分生成摘要
            int half = unsummarized.size() / 2;
            if (half == 0) {
                return;
            }
            List<ChatMessageDO> toSummarize = unsummarized.subList(0, half);

            String summary = llmSummarize(toSummarize);
            if (summary == null || summary.isBlank()) {
                return;
            }

            // 追加到已有摘要
            ensureSession(sessionId, null);
            session = getSession(sessionId);
            String existing = session.getSummary();
            session.setSummary(existing != null && !existing.isBlank() ? existing + "\n" + summary : summary);
            session.setSummaryMessageId(toSummarize.get(toSummarize.size() - 1).getId());
            chatSessionMapper.updateById(session);
            log.info("对话摘要已更新: session={}, 水位线={}", sessionId, session.getSummaryMessageId());
        } catch (Exception e) {
            log.warn("摘要生成失败，不影响对话: {}", e.getMessage());
        } finally {
            if (locked) {
                try {
                    lock.unlock();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * LLM 生成摘要：将旧对话压缩为简洁文本。
     */
    private String llmSummarize(List<ChatMessageDO> messages) {
        try {
            List<ChatMessage> chatMessages = new ArrayList<>();
            chatMessages.add(ChatMessage.system(
                    "请将以下对话历史总结为简洁摘要，保留关键信息（用户意图、已查询的信息、工具调用结果）。直接输出摘要，不要多余解释。"));
            StringBuilder history = new StringBuilder();
            for (ChatMessageDO d : messages) {
                history.append(d.getRole()).append(": ").append(d.getContent()).append("\n");
            }
            chatMessages.add(ChatMessage.user(history.toString()));

            ChatResponse resp = chatClient.chat(ChatRequest.builder().messages(chatMessages).build());
            return resp.getContent();
        } catch (Exception e) {
            log.warn("LLM 摘要调用失败: {}", e.getMessage());
            return null;
        }
    }

    private List<ChatMessage> buildMessagesWithSummary(List<ChatMessageDO> recent, String sessionId) {
        List<ChatMessage> messages = new ArrayList<>();
        ChatSessionDO session = getSession(sessionId);

        if (session != null && session.getSummary() != null && !session.getSummary().isBlank()) {
            messages.add(ChatMessage.system("之前的对话摘要: " + session.getSummary()));
            Long watermark = session.getSummaryMessageId();
            for (ChatMessageDO d : recent) {
                if (watermark == null || d.getId() > watermark) {
                    messages.add(toChatMessage(d));
                }
            }
        } else {
            for (ChatMessageDO d : recent) {
                messages.add(toChatMessage(d));
            }
        }

        return sanitizeToolHistory(messages);
    }

    private List<ChatMessage> sanitizeToolHistory(List<ChatMessage> messages) {
        List<ChatMessage> safe = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message.getRole() == ChatMessage.Role.ASSISTANT && message.getToolCalls() != null
                    && !message.getToolCalls().isEmpty()) {
                var calls = message.getToolCalls();
                List<ChatMessage> results = new ArrayList<>();
                int next = i + 1;
                while (next < messages.size() && messages.get(next).getRole() == ChatMessage.Role.TOOL) {
                    results.add(messages.get(next++));
                }
                boolean complete = calls.stream().map(ToolCall::getId).distinct().count() == calls.size()
                        && calls.stream().allMatch(call -> call.getId() != null && !call.getId().isBlank() && results.stream()
                        .filter(result -> Objects.equals(call.getId(), result.getToolCallId())).count() == 1)
                        && calls.size() == results.size();
                if (complete) {
                    safe.add(message);
                    safe.addAll(results);
                }
                i = next - 1;
            } else if (message.getRole() != ChatMessage.Role.TOOL) {
                safe.add(message);
            }
            // 旧记录或截断后的孤立工具结果不发给模型，防止无对应调用的 tool 消息。
        }
        return safe;
    }

    private ChatMessage toChatMessage(ChatMessageDO d) {
        ChatMessage.Role role = ChatMessage.Role.fromString(d.getRole());
        if (role == ChatMessage.Role.TOOL) {
            String id = d.getToolArgs() == null ? null : JSON.parseObject(d.getToolArgs()).getString("toolCallId");
            return ChatMessage.tool(id, d.getToolName(), d.getContent());
        }
        if (role == ChatMessage.Role.ASSISTANT && d.getToolArgs() != null) {
            var json = JSON.parseObject(d.getToolArgs());
            if (json.containsKey("toolCalls")) {
                return ChatMessage.assistantWithToolCalls(d.getContent(), json.getList("toolCalls", ToolCall.class));
            }
        }
        return new ChatMessage(role, d.getContent());
    }

    /**
     * 启发式 token 计数：ASCII 4 字符 ≈ 1 token，CJK 1 字符 ≈ 1 token。
     */
    private int heuristicTokenCount(List<ChatMessageDO> messages) {
        double tokens = 0;
        for (ChatMessageDO msg : messages) {
            if (msg.getContent() == null) {
                continue;
            }
            for (int i = 0; i < msg.getContent().length(); i++) {
                if (msg.getContent().charAt(i) > 127) {
                    tokens += 1.0;
                } else {
                    tokens += 0.25;
                }
            }
        }
        return (int) Math.ceil(tokens);
    }

    private ChatSessionDO getSession(String sessionId) {
        return chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSessionDO>()
                        .eq(ChatSessionDO::getSessionId, sessionId));
    }

    private void ensureSession(String sessionId, String username) {
        ChatSessionDO session = getSession(sessionId);
        if (session == null) {
            if (username == null || username.isBlank()) throw new ClientException("客服会话不存在");
            session = new ChatSessionDO();
            session.setSessionId(sessionId);
            session.setUsername(username);
            chatSessionMapper.insert(session);
        } else if (username != null && !Objects.equals(username, session.getUsername())) {
            throw new ClientException("无权访问此客服会话");
        }
    }
}
