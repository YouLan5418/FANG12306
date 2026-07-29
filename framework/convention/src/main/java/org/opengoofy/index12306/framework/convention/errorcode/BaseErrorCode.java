package org.opengoofy.index12306.framework.convention.errorcode;

public enum BaseErrorCode implements IErrorCode {

    CLIENT_ERROR("A000001", "用户端错误"),

    USER_REGISTER_ERROR("A000100", "用户注册错误"),
    USER_NAME_VERIFY_ERROR("A000110", "用户名校验失败"),
    USER_NAME_EXIST_ERROR("A000111", "用户名已存在"),
    USER_NAME_SENSITIVE_ERROR("A000112", "用户名包含敏感词"),
    USER_NAME_SPECIAL_CHARACTER_ERROR("A000113", "用户名包含特殊字符"),
    PASSWORD_VERIFY_ERROR("A000120", "密码校验失败"),
    PASSWORD_SHORT_ERROR("A000121", "密码长度不够"),
    PHONE_VERIFY_ERROR("A000151", "手机格式校验失败"),
    MAIL_VERIFY_ERROR("A000161", "邮箱格式校验失败"),
    ID_CARD_VERIFY_ERROR("A000171", "证件号校验失败"),

    TICKET_PURCHASE_ERROR("A000300", "购票失败"),
    TRAIN_NOT_FOUND_ERROR("A000301", "车次不存在"),
    SEAT_INSUFFICIENT_ERROR("A000302", "余票不足"),
    SEAT_LOCK_ERROR("A000303", "选座锁座失败"),

    ORDER_NOT_FOUND_ERROR("A000400", "订单不存在"),
    ORDER_STATUS_ERROR("A000401", "订单状态异常"),

    PAY_ERROR("A000500", "支付失败"),
    REFUND_ERROR("A000501", "退款失败"),

    IDEMPOTENT_TOKEN_NULL_ERROR("A000200", "幂等Token为空"),
    IDEMPOTENT_TOKEN_DELETE_ERROR("A000201", "幂等Token已被使用或失效"),

    SERVICE_ERROR("B000001", "系统执行出错"),
    SERVICE_TIMEOUT_ERROR("B000100", "系统执行超时"),

    REMOTE_ERROR("C000001", "调用第三方服务出错");

    private final String code;
    private final String message;

    BaseErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
