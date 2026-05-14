package org.puregxl.site.infra.http;

import lombok.Getter;

/**
 * 远程服务调用异常。
 */
@Getter
public class RemoteException extends RuntimeException {

    private final BaseErrorCode errorCode;

    public RemoteException(String message) {
        this(message, null, BaseErrorCode.REMOTE_ERROR);
    }

    public RemoteException(String message, BaseErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public RemoteException(String message, Throwable cause, BaseErrorCode errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
