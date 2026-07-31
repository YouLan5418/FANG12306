package org.opengoofy.index12306.common.config;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.common.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/user-service/v1/login",
                        "/api/user-service/register",
                        "/api/user-service/check-login",
                        "/api/user-service/logout",
                        "/api/user-service/has-username",
                        "/api/ticket-service/ticket/query",
                        "/api/ticket-service/region-station/query",
                        "/api/ticket-service/station/all",
                        "/api/ticket-service/train-station/query",
                        "/api/pay-service/callback/**",
                        "/actuator/**"
                );
    }
}
