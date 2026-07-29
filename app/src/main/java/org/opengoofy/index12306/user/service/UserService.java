package org.opengoofy.index12306.user.service;

import org.opengoofy.index12306.user.dto.req.UserUpdateReqDTO;
import org.opengoofy.index12306.user.dto.resp.UserQueryActualRespDTO;
import org.opengoofy.index12306.user.dto.resp.UserQueryRespDTO;

/**
 * 用户信息服务。
 */
public interface UserService {

    UserQueryRespDTO queryUserByUsername(String username);

    UserQueryActualRespDTO queryActualUserByUsername(String username);

    Integer queryUserDeletionNum(Integer idType, String idCard);

    void update(UserUpdateReqDTO requestParam);
}