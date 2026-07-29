package org.opengoofy.index12306.user.service.handler;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.convention.exception.ClientException;
import org.opengoofy.index12306.user.common.enums.UserRegisterErrorCodeEnum;
import org.opengoofy.index12306.user.dto.req.UserRegisterReqDTO;
import org.opengoofy.index12306.user.service.UserService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 证件号注销次数校验：同一证件多次注销则拉黑。
 */
@Component
@RequiredArgsConstructor
public final class UserRegisterCheckDeletionChainHandler implements UserRegisterChainHandler {

    @Lazy
    private final UserService userService;

    @Override
    public void handle(UserRegisterReqDTO requestParam) {
        Integer deletionNum = userService.queryUserDeletionNum(requestParam.getIdType(), requestParam.getIdCard());
        if (deletionNum != null && deletionNum >= 5) {
            throw new ClientException(UserRegisterErrorCodeEnum.DELETION_TOO_MANY);
        }
    }

    @Override
    public int getOrder() {
        return 2;
    }
}