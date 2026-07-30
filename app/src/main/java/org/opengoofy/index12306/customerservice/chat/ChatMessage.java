package org.opengoofy.index12306.customerservice.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chat message model with role-based classification.
 * Supports SYSTEM / USER / ASSISTANT / TOOL roles for FC engine conversations.
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
            throw new IllegalArgumentException("Invalid role: " + value);
        }
    }

    private Role role;
    private String content;

    /** For TOOL role messages: which tool call produced this result. */
    private String toolCallId;

    /** For TOOL role messages: the tool name. */
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