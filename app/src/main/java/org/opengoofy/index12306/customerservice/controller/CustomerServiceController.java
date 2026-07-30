package org.opengoofy.index12306.customerservice.controller;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.common.core.UserContext;
import org.opengoofy.index12306.customerservice.dto.req.ChatRequestDTO;
import org.opengoofy.index12306.customerservice.dto.resp.ChatMessageRespDTO;
import org.opengoofy.index12306.customerservice.dto.resp.ChatSessionRespDTO;
import org.opengoofy.index12306.customerservice.service.ChatSessionService;
import org.opengoofy.index12306.customerservice.service.CustomerServiceChatService;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能客服对话 Controller，暴露 SSE 流式接口与会话管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/customer-service")
@RequiredArgsConstructor
public class CustomerServiceController {

    private final CustomerServiceChatService chatService;
    private final ChatSessionService chatSessionService;

    // ==================== 对话 ====================

    /**
     * 流式对话，响应 text/event-stream。
     *
     * @param request 对话请求体（sessionId 可空、message 必填）
     * @return SSE 流
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequestDTO request) {
        try {
            String username = UserContext.getUsername();
            return chatService.streamChat(request.getMessage(), request.getSessionId(), username);
        } catch (RuntimeException e) {
            // 限流等异常：返回带 error 事件的 SSE
            log.warn("对话请求被拒绝: {}", e.getMessage());
            SseEmitter emitter = new SseEmitter();
            try {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("type", "error");
                err.put("message", e.getMessage() != null ? e.getMessage() : "unknown error");
                emitter.send(SseEmitter.event().data(JSON.toJSONString(err)));
                emitter.complete();
            } catch (Exception ignored) {
            }
            return emitter;
        }
    }

    // ==================== 会话管理 ====================

    /**
     * 查询当前用户的会话列表。
     */
    @GetMapping("/session/list")
    public Result<List<ChatSessionRespDTO>> listSessions() {
        String username = UserContext.getUsername();
        return Results.success(chatSessionService.listSessions(username));
    }

    /**
     * 查询会话消息详情。
     */
    @GetMapping("/session/{sessionId}/messages")
    public Result<List<ChatMessageRespDTO>> listMessages(@PathVariable String sessionId) {
        String username = UserContext.getUsername();
        return Results.success(chatSessionService.getMessages(sessionId, username));
    }

    /**
     * 删除会话及其全部消息。
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        String username = UserContext.getUsername();
        chatSessionService.deleteSession(sessionId, username);
        return Results.success();
    }
}