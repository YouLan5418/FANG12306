package org.opengoofy.index12306.customerservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.common.core.UserContext;
import org.opengoofy.index12306.customerservice.service.CustomerServiceChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 智能客服对话 Controller，暴露 SSE 流式接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/customerservice")
@RequiredArgsConstructor
public class CustomerServiceController {

    private final CustomerServiceChatService chatService;

    /**
     * 流式对话，响应 text/event-stream。
     *
     * @param question  用户问题
     * @param sessionId 会话 ID，为空则新建（首次对话）
     * @return SSE 流
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestParam String question,
                           @RequestParam(required = false) String sessionId) {
        try {
            String username = UserContext.getUsername();
            return chatService.streamChat(question, sessionId, username);
        } catch (RuntimeException e) {
            // 限流等异常：返回带 error 事件的 SSE
            log.warn("对话请求被拒绝: {}", e.getMessage());
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                emitter.complete();
            } catch (Exception ignored) {
            }
            return emitter;
        }
    }
}