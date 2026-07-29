package org.opengoofy.index12306.user.service.handler;

import org.opengoofy.index12306.user.dto.req.UserRegisterReqDTO;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 注册责任链编排：按 order 顺序执行所有处理器。
 */
@Component
public final class UserRegisterChainContext {

    private final List<UserRegisterChainHandler> handlers;

    public UserRegisterChainContext(List<UserRegisterChainHandler> handlers) {
        this.handlers = handlers.stream()
                .sorted(Comparator.comparingInt(UserRegisterChainHandler::getOrder))
                .toList();
    }

    public void execute(UserRegisterReqDTO requestParam) {
        handlers.forEach(handler -> handler.handle(requestParam));
    }
}