package org.opengoofy.index12306.customerservice.dto.req;

import lombok.Data;

/**
 * 对话请求体。sessionId 为空表示新建会话。
 */
@Data
public class ChatRequestDTO {

    /** 会话 ID，为空则后端新建 */
    private String sessionId;

    /** 用户消息 */
    private String message;
}