package org.opengoofy.index12306.customerservice.fc;

import org.opengoofy.index12306.customerservice.chat.ToolDefinition;

import java.util.Map;

/**
 * 工具执行器抽象接口。
 * 每个实现负责一个工具，执行时可通过 ctx 校验数据归属并截断结果。
 * 返回 JSON 字符串供 LLM 消费。
 */
public interface ToolExecutor {

    /**
     * 工具名称，与 LLM 请求的 function name 对应。
     */
    String name();

    /**
     * 工具定义（JSON Schema），暴露给 LLM。
     */
    ToolDefinition definition();

    /**
     * 执行工具，返回 JSON 字符串。
     * 实现内部负责归属校验与结果截断。
     *
     * @param args LLM 解析出的参数
     * @param ctx  当前用户身份上下文
     * @return JSON 字符串结果
     */
    String execute(Map<String, Object> args, ToolExecutionContext ctx);
}