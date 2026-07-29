package org.opengoofy.index12306.framework.idempotent.core;

import org.opengoofy.index12306.framework.convention.errorcode.BaseErrorCode;
import org.opengoofy.index12306.framework.convention.errorcode.IErrorCode;
import org.opengoofy.index12306.framework.convention.exception.AbstractException;
import org.opengoofy.index12306.framework.idempotent.enums.IdempotentSceneEnum;

/**
 * 重复消费/重复请求异常。
 * 继承 AbstractException 以便被 GlobalExceptionHandler 统一捕获，返回幂等场景错误码。
 */
public class RepeatConsumptionException extends AbstractException {

    private final IdempotentSceneEnum scene;

    public RepeatConsumptionException(String message, IdempotentSceneEnum scene) {
        this(message, null, BaseErrorCode.IDEMPOTENT_TOKEN_DELETE_ERROR, scene);
    }

    public RepeatConsumptionException(String message, Throwable throwable, IErrorCode errorCode, IdempotentSceneEnum scene) {
        super(message, throwable, errorCode);
        this.scene = scene;
    }

    public IdempotentSceneEnum getScene() {
        return scene;
    }

    @Override
    public String toString() {
        return "RepeatConsumptionException{scene=" + scene + ", code='" + errorCode + "', message='" + errorMessage + "'}";
    }
}
