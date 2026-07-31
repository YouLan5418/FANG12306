package org.opengoofy.index12306.common.toolkit;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES 加解密工具，用于敏感字段（手机号/身份证/邮箱等）落库加密。
 * 密钥默认硬编码兜底，可由 SecurityProperties 在启动时注入覆盖。
 * 编码格式为 Base64，与老项目 ShardingSphere AES 加密器保持兼容。
 */
public final class AESCryptUtil {

    private static String AES_KEY = "index12306secret";
    private static AES AES_INSTANCE = SecureUtil.aes(AES_KEY.getBytes());

    private AESCryptUtil() {
    }

    public static void setKey(String key) {
        AES_KEY = key;
        AES_INSTANCE = SecureUtil.aes(AES_KEY.getBytes());
    }

    public static String encrypt(String plaintext) {
        if (StrUtil.isBlank(plaintext)) {
            return null;
        }
        return AES_INSTANCE.encryptBase64(plaintext);
    }

    public static String decrypt(String ciphertext) {
        if (StrUtil.isBlank(ciphertext)) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(ciphertext);
        } catch (IllegalArgumentException e) {
            bytes = HexUtil.decodeHex(ciphertext);
        }
        return new String(AES_INSTANCE.decrypt(bytes), StandardCharsets.UTF_8);
    }
}