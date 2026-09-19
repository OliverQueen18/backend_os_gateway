package com.osgateway.transaction.application;

import com.osgateway.common.enums.TransactionStatus;
import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.exception.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TransactionWorkflow {
    private static final Map<TransactionStatus, Set<TransactionStatus>> TRANSITIONS = new EnumMap<>(TransactionStatus.class);

    static {
        TRANSITIONS.put(TransactionStatus.PENDING, EnumSet.of(
                TransactionStatus.QUEUED, TransactionStatus.FAILED, TransactionStatus.CANCELLED));
        TRANSITIONS.put(TransactionStatus.QUEUED, EnumSet.of(
                TransactionStatus.ASSIGNED, TransactionStatus.FAILED, TransactionStatus.CANCELLED));
        TRANSITIONS.put(TransactionStatus.ASSIGNED, EnumSet.of(
                TransactionStatus.PROCESSING,
                TransactionStatus.WAITING_SMS_CONFIRMATION,
                TransactionStatus.SUCCESS,
                TransactionStatus.FAILED,
                TransactionStatus.TIMEOUT,
                TransactionStatus.CANCELLED));
        TRANSITIONS.put(TransactionStatus.PROCESSING, EnumSet.of(
                TransactionStatus.WAITING_SMS_CONFIRMATION,
                TransactionStatus.SUCCESS,
                TransactionStatus.FAILED,
                TransactionStatus.TIMEOUT));
        TRANSITIONS.put(TransactionStatus.WAITING_SMS_CONFIRMATION, EnumSet.of(
                TransactionStatus.SUCCESS,
                TransactionStatus.FAILED,
                TransactionStatus.TIMEOUT));
        TRANSITIONS.put(TransactionStatus.SUCCESS, EnumSet.noneOf(TransactionStatus.class));
        TRANSITIONS.put(TransactionStatus.FAILED, EnumSet.noneOf(TransactionStatus.class));
        TRANSITIONS.put(TransactionStatus.CANCELLED, EnumSet.noneOf(TransactionStatus.class));
        // SMS opérateur en retard : confirmation après TIMEOUT soft / réseau
        TRANSITIONS.put(TransactionStatus.TIMEOUT, EnumSet.of(
                TransactionStatus.SUCCESS,
                TransactionStatus.FAILED));
    }

    private TransactionWorkflow() {}

    public static void assertTransition(TransactionStatus from, TransactionStatus to) {
        Set<TransactionStatus> allowed = TRANSITIONS.getOrDefault(from, EnumSet.noneOf(TransactionStatus.class));
        if (!allowed.contains(to)) {
            throw new BusinessException(ErrorCode.INVALID_TRANSACTION_STATUS,
                    "Cannot transition from " + from + " to " + to);
        }
    }

    public static boolean isCancellableStatus(TransactionStatus status) {
        return status == TransactionStatus.PENDING
                || status == TransactionStatus.QUEUED
                || status == TransactionStatus.ASSIGNED;
    }
}
