package org.opengoofy.index12306.common;

import org.junit.jupiter.api.Test;
import org.opengoofy.index12306.common.core.UserInfoDTO;
import org.opengoofy.index12306.common.toolkit.JWTUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JWTUtilTest {

    @Test
    void testGenerateAndParse() {
        UserInfoDTO user = UserInfoDTO.builder()
                .userId("123456")
                .username("testuser")
                .realName("测试用户")
                .build();
        String token = JWTUtil.generateAccessToken(user);
        assertNotNull(token);
        UserInfoDTO parsed = JWTUtil.parseJwtToken(token);
        assertNotNull(parsed);
        assertEquals("123456", parsed.getUserId());
        assertEquals("testuser", parsed.getUsername());
        assertEquals("测试用户", parsed.getRealName());
    }

    @Test
    void testInvalidToken() {
        UserInfoDTO parsed = JWTUtil.parseJwtToken("invalid.token.here");
        org.junit.jupiter.api.Assertions.assertNull(parsed);
    }
}
