package com.osgateway.common.enums;

public enum TransactionStatus {
    PENDING,
    QUEUED,
    ASSIGNED,
    PROCESSING,
    /** Attente SMS de confirmation opérateur (ex. retrait Orange Money). */
    WAITING_SMS_CONFIRMATION,
    SUCCESS,
    FAILED,
    CANCELLED,
    TIMEOUT
}
