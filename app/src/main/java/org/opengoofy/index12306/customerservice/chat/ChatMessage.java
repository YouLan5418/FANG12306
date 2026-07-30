package org.opengoofy.index12306.customerservice.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息模型，按角色分类。
 * 支持 SYSTEM / USER / ASSISTANT / TOOL 四种角色，TOOL 角色携带 toolCallId。
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

    public static ChatMessage tool(String toolCallId, String toolName, String content) {
        ChatMessage msg = new ChatMessage(Role.TOOL, content);
        msg.toolCallId = toolCallId;
        msg.toolName = toolName;
        return msg;
    }
}