package org.opengoofy.index12306.customerservice.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 请求的工具/函数调用。
 * arguments 是从流式增量中拼接而成的 JSON 字符串。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {
    private String id;
    private String name;
    private String arguments;
}