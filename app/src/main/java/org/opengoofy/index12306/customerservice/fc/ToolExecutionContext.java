package org.opengoofy.index12306.customerservice.fc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行上下文，携带当前用户身份，供工具做归属校验。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionContext {

    /** 登录用户名 */
    private String username;

    /** 用户 ID */
    private String userId;

    /** 会话 ID */
    private String sessionId;
}