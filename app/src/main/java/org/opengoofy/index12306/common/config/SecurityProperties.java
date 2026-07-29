package org.opengoofy.index12306.common.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.opengoofy.index12306.common.toolkit.AESCryptUtil;
import org.opengoofy.index12306.common.toolkit.JWTUtil;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 安全相关配置（JWT 密钥、AES 密钥）。
 * 启动时从 application.yml 读取并注入到静态工具类，避免密钥硬编码在源码中。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    /**
     * JWT 签名密钥
     */
    private String jwtSecret = "SecretKey039245678901232039487623456783092349288901402967890140939827";

    /**
     * AES 加密密钥
     */
    private String aesSecret = "index12306secret";

    @PostConstruct
    public void init() {
        JWTUtil.setSecret(jwtSecret);
        AESCryptUtil.setKey(aesSecret);
    }
}
