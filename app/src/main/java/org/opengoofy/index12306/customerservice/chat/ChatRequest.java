package org.opengoofy.index12306.customerservice.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一聊天请求，携带消息列表、可选工具定义和模型参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {

    @Singular
    private List<ChatMessage> messages = new ArrayList<>();

    @Singular
    private List<ToolDefinition> tools;

    private Double temperature;

    private Integer maxTokens;

    private String userId;
}
