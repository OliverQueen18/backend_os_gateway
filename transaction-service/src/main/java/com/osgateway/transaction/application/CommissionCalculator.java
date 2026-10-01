package com.osgateway.transaction.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Moteur de commission paramétrable.
 * BASE_THEN_SPLIT : base = MIN(MAX(montant × taux, plancher), plafond), puis parts sur la base.
 * DIRECT_ON_AMOUNT : chaque taux s'applique au montant (dépôt).
 */
public final class CommissionCalculator {
    public static final String BASE_THEN_SPLIT = "BASE_THEN_SPLIT";
    public static final String DIRECT_ON_AMOUNT = "DIRECT_ON_AMOUNT";

    private CommissionCalculator() {}

    public record Rule(
            String calculationMode,
            BigDecimal ratePercent,
            BigDecimal commissionMin,
            BigDecimal commissionMax,
            BigDecimal distributorRate,
            BigDecimal adminRate,
            BigDecimal operatorRate
    ) {}

    public record Split(
            BigDecimal total,
            BigDecimal admin,
            BigDecimal distributor,
            BigDecimal operatorShare
    ) {}

    public static Split apply(BigDecimal amount, Rule rule) {
        BigDecimal baseAmount = amount != null ? amount : BigDecimal.ZERO;
        if (DIRECT_ON_AMOUNT.equals(rule.calculationMode())) {
            return directOnAmount(baseAmount, rule);
        }
        return baseThenSplit(baseAmount, rule);
    }

    private static Split baseThenSplit(BigDecimal amount, Rule rule) {
        BigDecimal rate = rule.ratePercent() != null ? rule.ratePercent() : BigDecimal.ZERO;
        BigDecimal raw = amount.multiply(rate).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal base = clamp(raw, rule.commissionMin(), rule.commissionMax());
        BigDecimal distributor = percentOf(base, rule.distributorRate());
        BigDecimal admin = percentOf(base, rule.adminRate());
        BigDecimal operator = percentOf(base, rule.operatorRate());
        return new Split(distributor.add(admin).add(operator), admin, distributor, operator);
    }

    private static Split directOnAmount(BigDecimal amount, Rule rule) {
        BigDecimal distributor = percentOf(amount, rule.distributorRate());
        BigDecimal admin = percentOf(amount, rule.adminRate());
        BigDecimal operator = percentOf(amount, rule.operatorRate());
        BigDecimal total = distributor.add(admin).add(operator);
        BigDecimal clamped = clamp(total, rule.commissionMin(), rule.commissionMax());
        if (total.compareTo(BigDecimal.ZERO) > 0 && clamped.compareTo(total) != 0) {
            BigDecimal factor = clamped.divide(total, 8, RoundingMode.HALF_UP);
            distributor = distributor.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            admin = admin.multiply(factor).setScale(2, RoundingMode.HALF_UP);
            operator = clamped.subtract(distributor).subtract(admin).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            total = clamped;
        }
        return new Split(total.setScale(2, RoundingMode.HALF_UP), admin, distributor, operator);
    }

    private static BigDecimal percentOf(BigDecimal base, BigDecimal rate) {
        if (base == null || rate == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return base.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        BigDecimal result = value != null ? value : BigDecimal.ZERO;
        if (min != null && result.compareTo(min) < 0) {
            result = min;
        }
        if (max != null && result.compareTo(max) > 0) {
            result = max;
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }
}
