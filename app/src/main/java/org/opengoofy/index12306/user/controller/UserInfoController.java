package org.opengoofy.index12306.user.controller;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.opengoofy.index12306.user.dto.req.UserUpdateReqDTO;
import org.opengoofy.index12306.user.dto.resp.UserQueryActualRespDTO;
import org.opengoofy.index12306.user.dto.resp.UserQueryRespDTO;
import org.opengoofy.index12306.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户信息控制层。
 */
@RestController
@RequiredArgsConstructor
public class UserInfoController {

    private final UserService userService;

    @GetMapping("/api/user-service/query")
    public Result<UserQueryRespDTO> queryUserByUsername(@RequestParam("username") String username) {
        return Results.success(userService.queryUserByUsername(username));
    }

    @GetMapping("/api/user-service/actual/query")
    public Result<UserQueryActualRespDTO> queryActualUserByUsername(@RequestParam("username") String username) {
        return Results.success(userService.queryActualUserByUsername(username));
    }

    @PostMapping("/api/user-service/update")
    public Result<Void> update(@RequestBody UserUpdateReqDTO requestParam) {
        userService.update(requestParam);
        return Results.success();
    }
}