package org.opengoofy.index12306.customerservice.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天响应，包含生成内容和/或工具调用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {

    private String content;

    @Singular
    private List<ToolCall> toolCalls = new ArrayList<>();

    private String finishReason;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}