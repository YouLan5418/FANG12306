package org.opengoofy.index12306.user.common.enums;

import lombok.AllArgsConstructor;
import org.opengoofy.index12306.framework.convention.errorcode.IErrorCode;

/**
 * 用户注册错误码。
 */
@AllArgsConstructor
public enum UserRegisterErrorCodeEnum implements IErrorCode {

    USER_NAME_NOTNULL("B000100", "用户名不能为空"),
    PASSWORD_NOTNULL("B000101", "密码不能为空"),
    PHONE_NOTNULL("B000102", "手机号不能为空"),
    ID_TYPE_NOTNULL("B000103", "证件类型不能为空"),
    ID_CARD_NOTNULL("B000104", "证件号不能为空"),
    MAIL_NOTNULL("B000105", "邮箱不能为空"),
    REAL_NAME_NOTNULL("B000106", "真实姓名不能为空"),
    HAS_USERNAME_NOTNULL("B000107", "用户名已存在"),
    PHONE_REGISTERED("B000108", "手机号已被注册"),
    MAIL_REGISTERED("B000109", "邮箱已被注册"),
    USER_REGISTER_FAIL("B000110", "用户注册失败"),
    DELETION_TOO_MANY("B000111", "证件号多次注销账号已被加入黑名单");

    private final String code;
    private final String message;

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}