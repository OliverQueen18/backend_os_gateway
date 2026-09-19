package com.osgateway.transaction.application;

import com.osgateway.common.enums.TransactionStatus;
import com.osgateway.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionWorkflowTest {
    @Test
    void allowsValidTransition() {
        assertDoesNotThrow(() -> TransactionWorkflow.assertTransition(TransactionStatus.PENDING, TransactionStatus.QUEUED));
        assertDoesNotThrow(() -> TransactionWorkflow.assertTransition(TransactionStatus.PROCESSING, TransactionStatus.SUCCESS));
        assertDoesNotThrow(() -> TransactionWorkflow.assertTransition(
                TransactionStatus.PROCESSING, TransactionStatus.WAITING_SMS_CONFIRMATION));
        assertDoesNotThrow(() -> TransactionWorkflow.assertTransition(
                TransactionStatus.WAITING_SMS_CONFIRMATION, TransactionStatus.SUCCESS));
        assertDoesNotThrow(() -> TransactionWorkflow.assertTransition(
                TransactionStatus.WAITING_SMS_CONFIRMATION, TransactionStatus.TIMEOUT));
        assertDoesNotThrow(() -> TransactionWorkflow.assertTransition(
                TransactionStatus.TIMEOUT, TransactionStatus.SUCCESS));
        assertDoesNotThrow(() -> TransactionWorkflow.assertTransition(
                TransactionStatus.ASSIGNED, TransactionStatus.WAITING_SMS_CONFIRMATION));
    }

    @Test
    void rejectsInvalidTransition() {
        assertThrows(BusinessException.class,
                () -> TransactionWorkflow.assertTransition(TransactionStatus.SUCCESS, TransactionStatus.PENDING));
        assertThrows(BusinessException.class,
                () -> TransactionWorkflow.assertTransition(
                        TransactionStatus.WAITING_SMS_CONFIRMATION, TransactionStatus.QUEUED));
        assertThrows(BusinessException.class,
                () -> TransactionWorkflow.assertTransition(TransactionStatus.TIMEOUT, TransactionStatus.QUEUED));
    }
}