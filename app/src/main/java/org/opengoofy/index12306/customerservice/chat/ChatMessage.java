package org.opengoofy.index12306.customerservice.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对话消息模型，按角色分类。
 * 支持 SYSTEM / USER / ASSISTANT / TOOL 四种角色，TOOL 角色携带 toolCallId。
 * ASSISTANT 角色携带 toolCalls 时表示 LLM 请求的工具调用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL;

        public static Role fromString(String value) {
            for (Role role : values()) {
                if (role.name().equalsIgnoreCase(value)) {
                    return role;
                }
            }
            throw new IllegalArgumentException("无效的角色: " + value);
        }
    }

    private Role role;
    private String content;

    /** TOOL 角色消息：产生该结果的工具调用 ID */
    private String toolCallId;

    /** TOOL 角色消息：工具名称 */
    private String toolName;

    /** ASSISTANT 角色消息：LLM 请求的工具调用列表 */
    private List<ToolCall> toolCalls;

    public ChatMessage(Role role, String content) {
        this.role = role;
        this.content = content;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }

    /**
     * 创建携带工具调用的 ASSISTANT 消息，FC 引擎将 LLM 的 tool_call 请求加入对话历史。
     */
    public static ChatMessage assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
        ChatMessage msg = new ChatMessage(Role.ASSISTANT, content);
        msg.toolCalls = toolCalls;
        return msg;
    }

    public static ChatMessage tool(String toolCallId, String toolName, String content) {
        ChatMessage msg = new ChatMessage(Role.TOOL, content);
        msg.toolCallId = toolCallId;
        msg.toolName = toolName;
        return msg;
    }
}