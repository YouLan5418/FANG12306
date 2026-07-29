package org.opengoofy.index12306.framework.convention.exception;

import org.opengoofy.index12306.framework.convention.errorcode.BaseErrorCode;
import org.opengoofy.index12306.framework.convention.errorcode.IErrorCode;

public class ClientException extends AbstractException {

    public ClientException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    public ClientException(String message) {
        this(message, null, BaseErrorCode.CLIENT_ERROR);
    }

    public ClientException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public ClientException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }

    @Override
    public String toString() {
        return "ClientException{code='" + errorCode + "', message='" + errorMessage + "'}";
    }
}
