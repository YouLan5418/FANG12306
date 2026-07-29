package org.opengoofy.index12306.common.toolkit;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;

/**
 * AES 加解密工具，用于敏感字段（手机号/身份证/邮箱等）落库加密。
 * 密钥默认硬编码兜底，可由 SecurityProperties 在启动时注入覆盖。
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
        return AES_INSTANCE.encryptHex(plaintext);
    }

    public static String decrypt(String ciphertext) {
        if (StrUtil.isBlank(ciphertext)) {
            return null;
        }
        return AES_INSTANCE.decryptStr(ciphertext);
    }
}
