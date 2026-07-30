package org.opengoofy.index12306.customerservice.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 对话消息详情响应。toolName/toolArgs/toolResult 仅 role=tool 时有值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRespDTO {

    private String role;
    private String content;
    private String toolName;
    private String toolArgs;
    private String toolResult;
    private Date createTime;
}