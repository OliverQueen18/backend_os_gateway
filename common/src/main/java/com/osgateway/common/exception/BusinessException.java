package com.osgateway.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String details;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessException(ErrorCode errorCode, String details) {
        super(details != null ? details : errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.details = details;
    }

    public BusinessException(ErrorCode errorCode, String details, Throwable cause) {
        super(details != null ? details : errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.details = details;
    }
}
