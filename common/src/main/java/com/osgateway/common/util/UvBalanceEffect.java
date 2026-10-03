package com.osgateway.common.util;

import java.util.Locale;

/**
 * Sens du solde UV distributeur selon le type d'opération.
 * Le montant seul est mouvementé, jamais la commission.
 * NONE configuré sur le type reste sans impact.
 */
public final class UvBalanceEffect {

    private UvBalanceEffect() {
    }

    public static String resolve(String typeCode, String configuredEffect) {
        String configured = normalize(configuredEffect);
        if ("NONE".equals(configured)) {
            return "NONE";
        }
        return switch (normalize(typeCode)) {
            case "SOLDE" -> "NONE";
            case "RETRAIT", "ACHAT_UV" -> "CREDIT";
            case "DEPOT", "TRANSFERT", "PAIEMENT", "ACHAT_CREDIT" -> "DEBIT";
            default -> switch (configured) {
                case "CREDIT", "DEBIT" -> configured;
                default -> "DEBIT";
            };
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
