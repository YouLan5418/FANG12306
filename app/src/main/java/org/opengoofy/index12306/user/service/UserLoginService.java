package org.opengoofy.index12306.user.service;

import org.opengoofy.index12306.user.dto.req.UserDeletionReqDTO;
import org.opengoofy.index12306.user.dto.req.UserLoginReqDTO;
import org.opengoofy.index12306.user.dto.req.UserRegisterReqDTO;
import org.opengoofy.index12306.user.dto.resp.UserLoginRespDTO;
import org.opengoofy.index12306.user.dto.resp.UserRegisterRespDTO;

/**
 * 用户登录/注册服务。
 */
public interface UserLoginService {

    UserLoginRespDTO login(UserLoginReqDTO requestParam);

    UserLoginRespDTO checkLogin(String accessToken);

    void logout(String accessToken);

    /**
     * 用户名是否可用：布隆过滤器 + 复用集合双重判断
     */
    Boolean hasUsername(String username);

    UserRegisterRespDTO register(UserRegisterReqDTO requestParam);

    void deletion(UserDeletionReqDTO requestParam);
}