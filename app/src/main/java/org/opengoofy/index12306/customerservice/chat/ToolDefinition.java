package org.opengoofy.index12306.customerservice.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具定义，以 OpenAI function-calling 格式暴露给 LLM。
 * parameters 遵循 JSON Schema 规范。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolDefinition {
    private String name;
    private String description;
    private Map<String, Object> parameters;
}