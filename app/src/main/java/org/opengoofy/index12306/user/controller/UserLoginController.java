package org.opengoofy.index12306.user.controller;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.convention.result.Result;
import org.opengoofy.index12306.framework.convention.result.Results;
import org.opengoofy.index12306.user.dto.req.UserDeletionReqDTO;
import org.opengoofy.index12306.user.dto.req.UserLoginReqDTO;
import org.opengoofy.index12306.user.dto.req.UserRegisterReqDTO;
import org.opengoofy.index12306.user.dto.resp.UserLoginRespDTO;
import org.opengoofy.index12306.user.dto.resp.UserRegisterRespDTO;
import org.opengoofy.index12306.user.service.UserLoginService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户登录/注册控制层。
 */
@RestController
@RequiredArgsConstructor
public class UserLoginController {

    private final UserLoginService userLoginService;

    @PostMapping("/api/user-service/v1/login")
    public Result<UserLoginRespDTO> login(@RequestBody UserLoginReqDTO requestParam) {
        return Results.success(userLoginService.login(requestParam));
    }

    @GetMapping("/api/user-service/check-login")
    public Result<UserLoginRespDTO> checkLogin(@RequestParam("accessToken") String accessToken) {
        return Results.success(userLoginService.checkLogin(accessToken));
    }

    @GetMapping("/api/user-service/logout")
    public Result<Void> logout(@RequestParam("accessToken") String accessToken) {
        userLoginService.logout(accessToken);
        return Results.success();
    }

    @PostMapping("/api/user-service/register")
    public Result<UserRegisterRespDTO> register(@RequestBody UserRegisterReqDTO requestParam) {
        return Results.success(userLoginService.register(requestParam));
    }

    @GetMapping("/api/user-service/has-username")
    public Result<Boolean> hasUsername(@RequestParam("username") String username) {
        return Results.success(userLoginService.hasUsername(username));
    }

    @PostMapping("/api/user-service/deletion")
    public Result<Void> deletion(@RequestBody UserDeletionReqDTO requestParam) {
        userLoginService.deletion(requestParam);
        return Results.success();
    }
}