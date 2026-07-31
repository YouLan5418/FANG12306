package org.opengoofy.index12306.common.toolkit;

import com.alibaba.fastjson2.JSON;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.common.constant.UserConstant;
import org.opengoofy.index12306.common.core.UserInfoDTO;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类（jjwt 0.12+ 新 API）。
 * 密钥默认硬编码兜底，可由 SecurityProperties 在启动时注入覆盖。
 */
@Slf4j
public final class JWTUtil {

    private static final long EXPIRATION = 86400L;
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String ISS = "index12306";

    private static String SECRET = "SecretKey039245678901232039487623456783092349288901402967890140939827";
    private static SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    public static void setSecret(String secret) {
        SECRET = secret;
        KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    public static String generateAccessToken(UserInfoDTO userInfo) {
        Map<String, Object> customerUserMap = new HashMap<>();
        customerUserMap.put(UserConstant.USER_ID_KEY, userInfo.getUserId());
        customerUserMap.put(UserConstant.USER_NAME_KEY, userInfo.getUsername());
        customerUserMap.put(UserConstant.REAL_NAME_KEY, userInfo.getRealName());
        customerUserMap.put(UserConstant.USER_ROLE_KEY, userInfo.getRole() != null ? userInfo.getRole() : 0);
        String jwtToken = Jwts.builder()
                .issuedAt(new Date())
                .issuer(ISS)
                .subject(JSON.toJSONString(customerUserMap))
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION * 1000))
                .signWith(KEY, Jwts.SIG.HS512)
                .compact();
        return TOKEN_PREFIX + jwtToken;
    }

    public static UserInfoDTO parseJwtToken(String jwtToken) {
        if (StringUtils.hasText(jwtToken)) {
            String actualJwtToken = jwtToken.replace(TOKEN_PREFIX, "");
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(KEY)
                        .build()
                        .parseSignedClaims(actualJwtToken)
                        .getPayload();
                Date expiration = claims.getExpiration();
                if (expiration != null && expiration.after(new Date())) {
                    String subject = claims.getSubject();
                    UserInfoDTO userInfo = JSON.parseObject(subject, UserInfoDTO.class);
                    if (userInfo != null && userInfo.getRole() == null) {
                        userInfo.setRole((Integer) claims.getOrDefault(UserConstant.USER_ROLE_KEY, 0));
                    }
                    return userInfo;
                }
            } catch (ExpiredJwtException ignored) {
            } catch (Exception ex) {
                log.error("JWT Token 解析失败", ex);
            }
        }
        return null;
    }
}
