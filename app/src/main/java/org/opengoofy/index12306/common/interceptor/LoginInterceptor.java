package org.opengoofy.index12306.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.common.core.UserContext;
import org.opengoofy.index12306.common.core.UserInfoDTO;
import org.opengoofy.index12306.common.toolkit.JWTUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.alibaba.fastjson2.JSON;

import java.io.IOException;

import org.opengoofy.index12306.common.constant.UserConstant;

/**
 * 登录拦截器：校验 Authorization Header 中的 JWT，并从 Redis 确认登录态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            token = request.getParameter("accessToken");
        }
        UserInfoDTO userInfo = JWTUtil.parseJwtToken(token);
        if (userInfo == null) {
            log.warn("未登录或 Token 无效: {}", request.getRequestURI());
            writeError(response, 401, "A000002", "用户未登录或 Token 无效");
            return false;
        }
        Object loginInfo = stringRedisTemplate.opsForValue().get(UserConstant.LOGIN_TOKEN_KEY_PREFIX + token);
        if (loginInfo == null) {
            log.warn("登录态已过期: {}", token);
            writeError(response, 401, "A000002", "登录态已过期");
            return false;
        }
        userInfo.setToken(token);
        UserInfoDTO stored = JSON.parseObject(loginInfo.toString(), UserInfoDTO.class);
        if (stored != null) {
            userInfo.setUserId(stored.getUserId());
            userInfo.setUsername(stored.getUsername());
            userInfo.setRealName(stored.getRealName());
            if (stored.getRole() != null) {
                userInfo.setRole(stored.getRole());
            }
        }
        if (request.getRequestURI().startsWith("/api/admin/")) {
            if (userInfo.getRole() == null || userInfo.getRole() != 1) {
                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                try {
                    response.getWriter().write("{\"code\":\"A000003\",\"message\":\"无管理员权限\",\"success\":false}");
                } catch (IOException ignored) {
                }
                return false;
            }
        }
        UserContext.setUser(userInfo);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.removeUser();
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"success\":false}");
        } catch (IOException ignored) {
        }
    }
}
