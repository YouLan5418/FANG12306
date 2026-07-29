package org.opengoofy.index12306.user.service.handler;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.convention.exception.ClientException;
import org.opengoofy.index12306.user.common.enums.UserRegisterErrorCodeEnum;
import org.opengoofy.index12306.user.dto.req.UserRegisterReqDTO;
import org.opengoofy.index12306.user.service.UserAvailabilityChecker;
import org.springframework.stereotype.Component;

/**
 * 用户名是否已存在校验（布隆过滤器 + 复用集合双重判断）。
 */
@Component
@RequiredArgsConstructor
public final class UserRegisterHasUsernameChainHandler implements UserRegisterChainHandler {

    private final UserAvailabilityChecker userAvailabilityChecker;

    @Override
    public void handle(UserRegisterReqDTO requestParam) {
        if (!userAvailabilityChecker.hasUsername(requestParam.getUsername())) {
            throw new ClientException(UserRegisterErrorCodeEnum.HAS_USERNAME_NOTNULL);
        }
    }

    @Override
    public int getOrder() {
        return 1;
    }
}