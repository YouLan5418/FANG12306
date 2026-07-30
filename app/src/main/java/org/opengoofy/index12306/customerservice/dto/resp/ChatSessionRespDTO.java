package org.opengoofy.index12306.customerservice.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 对话会话列表响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionRespDTO {

    private String sessionId;
    private String title;
    private Date updateTime;
}