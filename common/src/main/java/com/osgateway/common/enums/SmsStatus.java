package com.osgateway.common.enums;

public enum SmsStatus {
    PENDING,
    QUEUED,
    SENDING,
    SENT,
    DELIVERED,
    FAILED,
    SCHEDULED,
    /** SMS entrant reporté par un gateway. */
    RECEIVED
}
