package com.osgateway.transaction.application;

import com.osgateway.common.util.UvBalanceEffect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UvBalanceEffectTest {

    @Test
    void retraitCreditsEvenWhenSeededAsDebit() {
        assertEquals("CREDIT", UvBalanceEffect.resolve("RETRAIT", "DEBIT"));
        assertEquals("CREDIT", UvBalanceEffect.resolve("retrait", null));
        assertEquals("CREDIT", UvBalanceEffect.resolve("ACHAT_UV", "DEBIT"));
    }

    @Test
    void outgoingOperationsDebit() {
        assertEquals("DEBIT", UvBalanceEffect.resolve("DEPOT", "DEBIT"));
        assertEquals("DEBIT", UvBalanceEffect.resolve("TRANSFERT", "CREDIT"));
        assertEquals("DEBIT", UvBalanceEffect.resolve("PAIEMENT", null));
        assertEquals("DEBIT", UvBalanceEffect.resolve("ACHAT_CREDIT", "DEBIT"));
    }

    @Test
    void balanceCheckAndExplicitNoneDoNotMoveUv() {
        assertEquals("NONE", UvBalanceEffect.resolve("SOLDE", "DEBIT"));
        assertEquals("NONE", UvBalanceEffect.resolve("RETRAIT", "NONE"));
        assertEquals("NONE", UvBalanceEffect.resolve("DEPOT", "NONE"));
    }
}
