package org.opengoofy.index12306.framework.convention.exception;

import org.opengoofy.index12306.framework.convention.errorcode.BaseErrorCode;
import org.opengoofy.index12306.framework.convention.errorcode.IErrorCode;

public class RemoteException extends AbstractException {

    public RemoteException(IErrorCode errorCode) {
        this(null, null, errorCode);
    }

    public RemoteException(String message) {
        this(message, null, BaseErrorCode.REMOTE_ERROR);
    }

    public RemoteException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public RemoteException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }

    @Override
    public String toString() {
        return "RemoteException{code='" + errorCode + "', message='" + errorMessage + "'}";
    }
}
