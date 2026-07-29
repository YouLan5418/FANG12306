package org.opengoofy.index12306.user.service.handler;

import org.opengoofy.index12306.user.dto.req.UserRegisterReqDTO;
import org.springframework.core.Ordered;

/**
 * 用户注册责任链处理器。
 */
public interface UserRegisterChainHandler extends Ordered {

    void handle(UserRegisterReqDTO requestParam);
}