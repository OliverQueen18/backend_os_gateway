package com.osgateway.transaction.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommissionCalculatorTest {

    private static final CommissionCalculator.Rule RETRAIT = new CommissionCalculator.Rule(
            CommissionCalculator.BASE_THEN_SPLIT,
            new BigDecimal("1"),
            new BigDecimal("50"),
            new BigDecimal("10000"),
            new BigDecimal("70"),
            new BigDecimal("30"),
            null);

    private static final CommissionCalculator.Rule DEPOT = new CommissionCalculator.Rule(
            CommissionCalculator.DIRECT_ON_AMOUNT,
            null,
            null,
            null,
            new BigDecimal("0.50"),
            new BigDecimal("0.10"),
            null);

    @Test
    void retraitFloorAtFifty() {
        assertBase(new BigDecimal("1000"), "50.00");
        assertBase(new BigDecimal("2500"), "50.00");
        assertBase(new BigDecimal("5000"), "50.00");
    }

    @Test
    void retraitProportionalThenCap() {
        assertBase(new BigDecimal("10000"), "100.00");
        assertBase(new BigDecimal("100000"), "1000.00");
        assertBase(new BigDecimal("1000000"), "10000.00");
        assertBase(new BigDecimal("2000000"), "10000.00");
    }

    @Test
    void retraitSplitSeventyThirty() {
        CommissionCalculator.Split split = CommissionCalculator.apply(new BigDecimal("100000"), RETRAIT);
        assertEquals(new BigDecimal("700.00"), split.distributor());
        assertEquals(new BigDecimal("300.00"), split.admin());
        assertEquals(new BigDecimal("1000.00"), split.total());
    }

    @Test
    void depotRatesOnAmount() {
        CommissionCalculator.Split split = CommissionCalculator.apply(new BigDecimal("100000"), DEPOT);
        assertEquals(new BigDecimal("500.00"), split.distributor());
        assertEquals(new BigDecimal("100.00"), split.admin());
        assertEquals(new BigDecimal("600.00"), split.total());
    }

    private static void assertBase(BigDecimal amount, String expected) {
        CommissionCalculator.Split split = CommissionCalculator.apply(amount, RETRAIT);
        assertEquals(new BigDecimal(expected), split.total());
    }
}
