package com.osgateway.reporting.application;

/**
 * Optional filters for transaction / commission reports.
 */
public record ReportFilters(Long distributorId, String type, Long gatewayId) {
    public static ReportFilters empty() {
        return new ReportFilters(null, null, null);
    }

    public ReportFilters normalized() {
        String t = type == null || type.isBlank() ? null : type.trim().toUpperCase();
        return new ReportFilters(distributorId, t, gatewayId);
    }

    public boolean hasDistributor() {
        return distributorId != null;
    }

    public boolean hasType() {
        return type != null && !type.isBlank();
    }

    public boolean hasGateway() {
        return gatewayId != null;
    }
}
