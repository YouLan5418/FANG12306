package org.opengoofy.index12306.framework.convention.exception;

import org.opengoofy.index12306.framework.convention.errorcode.BaseErrorCode;
import org.opengoofy.index12306.framework.convention.errorcode.IErrorCode;

public class ServiceException extends AbstractException {

    public ServiceException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    public ServiceException(String message) {
        this(message, null, BaseErrorCode.SERVICE_ERROR);
    }

    public ServiceException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public ServiceException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }

    @Override
    public String toString() {
        return "ServiceException{code='" + errorCode + "', message='" + errorMessage + "'}";
    }
}
