package org.opengoofy.index12306.customerservice.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified chat request carrying messages, optional tool definitions, and model parameters.
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
}