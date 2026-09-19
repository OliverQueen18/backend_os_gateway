package com.osgateway.reporting.application;

import com.osgateway.common.exception.BusinessException;
import com.osgateway.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportingService {
    private static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";
    private static final char CSV_SEPARATOR = ';';
    private static final String UTF8_BOM = "\uFEFF";

    private static final List<String> COMMISSION_COLUMNS = List.of(
            "distributor_code",
            "distributor_name",
            "operator",
            "type",
            "count",
            "total_amount",
            "total_commission",
            "total_admin_commission",
            "total_distributor_commission");

    private static final List<String> TRANSACTION_COLUMNS = List.of(
            "operator",
            "type",
            "status",
            "count",
            "total_amount",
            "total_commission",
            "total_admin_commission",
            "total_distributor_commission");

    private static final List<String> SMS_COLUMNS = List.of("status", "count");

    private static final Map<String, String> CSV_HEADERS_FR = Map.ofEntries(
            Map.entry("distributor_code", "Code distributeur"),
            Map.entry("distributor_name", "Distributeur"),
            Map.entry("operator", "Opérateur"),
            Map.entry("type", "Type"),
            Map.entry("status", "Statut"),
            Map.entry("count", "Volume"),
            Map.entry("total_amount", "Montant"),
            Map.entry("total_commission", "Commission"),
            Map.entry("total_admin_commission", "Part admin"),
            Map.entry("total_distributor_commission", "Part distributeur"));

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> transactionsReport(LocalDate from, LocalDate to) {
        return transactionsReport(from, to, ReportFilters.empty());
    }

    public Map<String, Object> transactionsReport(LocalDate from, LocalDate to, ReportFilters filters) {
        ReportFilters f = filters == null ? ReportFilters.empty() : filters.normalized();
        StringBuilder sql = new StringBuilder("""
            SELECT operator, type, status, COUNT(*) AS count,
                   COALESCE(SUM(amount),0) AS total_amount,
                   COALESCE(SUM(commission),0) AS total_commission,
                   COALESCE(SUM(admin_commission),0) AS total_admin_commission,
                   COALESCE(SUM(distributor_commission),0) AS total_distributor_commission
            FROM transactions
            WHERE created_at::date BETWEEN ? AND ?
            """);
        List<Object> args = new ArrayList<>();
        args.add(from);
        args.add(to);
        appendCommonFilters(sql, args, f, "");
        sql.append("""
            GROUP BY operator, type, status
            ORDER BY operator, type
            """);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return Map.of(
                "from", from,
                "to", to,
                "rows", rows,
                "export", Map.of("csv", "/api/v1/reports/export/transactions.csv"));
    }

    public Map<String, Object> smsReport(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT status, COUNT(*) AS count FROM sms
            WHERE created_at::date BETWEEN ? AND ?
            GROUP BY status
            """, from, to);
        return Map.of(
                "from", from,
                "to", to,
                "rows", rows,
                "export", Map.of("csv", "/api/v1/reports/export/sms.csv"));
    }

    public Map<String, Object> commissionsReport(LocalDate from, LocalDate to) {
        return commissionsReport(from, to, ReportFilters.empty());
    }

    public Map<String, Object> commissionsReport(LocalDate from, LocalDate to, ReportFilters filters) {
        return commissionsReportInternal(from, to, filters, false);
    }

    public Map<String, Object> commissionsReportForUser(LocalDate from, LocalDate to, Long userId) {
        return commissionsReportForUser(from, to, userId, ReportFilters.empty());
    }

    public Map<String, Object> commissionsReportForUser(
            LocalDate from, LocalDate to, Long userId, ReportFilters filters) {
        Long distributorId = resolveDistributorId(userId);
        ReportFilters base = filters == null ? ReportFilters.empty() : filters.normalized();
        return commissionsReportInternal(
                from,
                to,
                new ReportFilters(distributorId, base.type(), base.gatewayId()).normalized(),
                true);
    }

    private Map<String, Object> commissionsReportInternal(
            LocalDate from, LocalDate to, ReportFilters filters, boolean mine) {
        ReportFilters f = filters == null ? ReportFilters.empty() : filters.normalized();
        StringBuilder sql = new StringBuilder("""
            SELECT t.distributor_id,
                   d.code AS distributor_code,
                   d.name AS distributor_name,
                   t.operator,
                   t.type,
                   COUNT(*) AS count,
                   COALESCE(SUM(t.amount),0) AS total_amount,
                   COALESCE(SUM(t.commission),0) AS total_commission,
                   COALESCE(SUM(t.admin_commission),0) AS total_admin_commission,
                   COALESCE(SUM(t.distributor_commission),0) AS total_distributor_commission
            FROM transactions t
            LEFT JOIN distributor_accounts d ON d.id = t.distributor_id
            WHERE t.status = 'SUCCESS' AND t.created_at::date BETWEEN ? AND ?
            """);
        List<Object> args = new ArrayList<>();
        args.add(from);
        args.add(to);
        appendCommonFilters(sql, args, f, "t.");
        sql.append("""
            GROUP BY t.distributor_id, d.code, d.name, t.operator, t.type
            ORDER BY d.code NULLS LAST, t.operator, t.type
            """);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());

        BigDecimal totalCommission = sumColumn(rows, "total_commission");
        BigDecimal totalAdmin = sumColumn(rows, "total_admin_commission");
        BigDecimal totalDistributor = sumColumn(rows, "total_distributor_commission");

        String exportKey = mine ? "commissions-me" : "commissions";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from);
        result.put("to", to);
        result.put("rows", rows);
        result.put("totals", Map.of(
                "totalCommission", totalCommission,
                "totalAdminCommission", totalAdmin,
                "totalDistributorCommission", totalDistributor));
        result.put("export", Map.of("csv", "/api/v1/reports/export/" + exportKey + ".csv"));
        return result;
    }

    private void appendCommonFilters(
            StringBuilder sql, List<Object> args, ReportFilters f, String prefix) {
        if (f.hasDistributor()) {
            sql.append(" AND ").append(prefix).append("distributor_id = ? ");
            args.add(f.distributorId());
        }
        if (f.hasType()) {
            sql.append(" AND UPPER(").append(prefix).append("type) = ? ");
            args.add(f.type());
        }
        if (f.hasGateway()) {
            sql.append(" AND ").append(prefix).append("gateway_id = ? ");
            args.add(f.gatewayId());
        }
    }

    public Map<String, Object> myStats(Long userId) {
        Long distributorId = resolveDistributorId(userId);
        LocalDate today = LocalDate.now();

        Map<String, Object> aggregates = jdbcTemplate.queryForMap("""
            SELECT COUNT(*) AS total_today,
                   COUNT(*) FILTER (WHERE status = 'SUCCESS') AS success_today,
                   COUNT(*) FILTER (WHERE status = 'FAILED') AS failed_today,
                   COALESCE(SUM(amount) FILTER (WHERE status = 'SUCCESS'), 0) AS volume_today,
                   COALESCE(SUM(distributor_commission) FILTER (WHERE status = 'SUCCESS'), 0) AS commission_today
            FROM transactions
            WHERE distributor_id = ? AND created_at::date = ?
            """, distributorId, today);

        List<Map<String, Object>> byTypeRows = jdbcTemplate.queryForList("""
            SELECT type, COUNT(*) AS count
            FROM transactions
            WHERE distributor_id = ? AND created_at::date = ?
            GROUP BY type
            """, distributorId, today);
        Map<String, Long> byType = byTypeRows.stream()
                .collect(Collectors.toMap(
                        r -> String.valueOf(r.get("type")),
                        r -> ((Number) r.get("count")).longValue(),
                        Long::sum,
                        LinkedHashMap::new));

        List<Map<String, Object>> byOperatorRows = jdbcTemplate.queryForList("""
            SELECT operator, COUNT(*) AS count
            FROM transactions
            WHERE distributor_id = ? AND created_at::date = ?
            GROUP BY operator
            """, distributorId, today);
        Map<String, Long> byOperator = byOperatorRows.stream()
                .collect(Collectors.toMap(
                        r -> String.valueOf(r.get("operator")),
                        r -> ((Number) r.get("count")).longValue(),
                        Long::sum,
                        LinkedHashMap::new));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalToday", ((Number) aggregates.get("total_today")).longValue());
        result.put("successToday", ((Number) aggregates.get("success_today")).longValue());
        result.put("failedToday", ((Number) aggregates.get("failed_today")).longValue());
        result.put("volumeToday", toDouble(aggregates.get("volume_today")));
        result.put("commissionToday", toDouble(aggregates.get("commission_today")));
        result.put("byType", byType);
        result.put("byOperator", byOperator);
        return result;
    }

    public Map<String, Object> operatorsReport() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT o.code, o.name, COUNT(t.id) AS tx_count
            FROM operators o
            LEFT JOIN transactions t ON t.operator = o.code
            GROUP BY o.code, o.name
            """);
        return Map.of("rows", rows);
    }

    public Map<String, Object> gatewaysReport() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT operator, status, COUNT(*) AS count, AVG(battery_level) AS avg_battery
            FROM gateways GROUP BY operator, status
            """);
        return Map.of("rows", rows);
    }

    public ExportFile exportReport(String format, String report, LocalDate from, LocalDate to, Long userId, String roles) {
        return exportReport(format, report, from, to, userId, roles, ReportFilters.empty());
    }

    public ExportFile exportReport(
            String format,
            String report,
            LocalDate from,
            LocalDate to,
            Long userId,
            String roles,
            ReportFilters filters) {
        String normalizedFormat = format == null ? "" : format.toLowerCase(Locale.ROOT);
        String normalizedReport = report == null ? "" : report.toLowerCase(Locale.ROOT);
        ReportFilters f = filters == null ? ReportFilters.empty() : filters.normalized();

        if (!"csv".equals(normalizedFormat)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Seul l'export CSV est disponible pour le moment");
        }

        if ("commissions".equals(normalizedReport) && isDistributorOnly(roles)) {
            normalizedReport = "commissions-me";
        }

        List<Map<String, Object>> rows;
        List<String> columns;
        String baseName;
        switch (normalizedReport) {
            case "commissions", "commissions-me" -> {
                Map<String, Object> data = "commissions-me".equals(normalizedReport)
                        ? commissionsReportForUser(from, to, userId, f)
                        : commissionsReport(from, to, f);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> commissionRows =
                        (List<Map<String, Object>>) data.getOrDefault("rows", List.of());
                rows = commissionRows;
                columns = COMMISSION_COLUMNS;
                baseName = "commissions-me".equals(normalizedReport) ? "mes_commissions" : "commissions";
            }
            case "transactions" -> {
                if (isDistributorOnly(roles)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN,
                            "Export transactions global réservé aux administrateurs");
                }
                Map<String, Object> data = transactionsReport(from, to, f);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> txRows =
                        (List<Map<String, Object>>) data.getOrDefault("rows", List.of());
                rows = txRows;
                columns = TRANSACTION_COLUMNS;
                baseName = "transactions";
            }
            case "sms" -> {
                if (isDistributorOnly(roles)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN,
                            "Export SMS réservé aux administrateurs");
                }
                Map<String, Object> data = smsReport(from, to);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> smsRows =
                        (List<Map<String, Object>>) data.getOrDefault("rows", List.of());
                rows = smsRows;
                columns = SMS_COLUMNS;
                baseName = "sms";
            }
            default -> throw new BusinessException(ErrorCode.NOT_FOUND, "Rapport inconnu: " + report);
        }

        String filename = baseName + "_" + from + "_" + to + ".csv";
        byte[] content = toCsv(rows, columns).getBytes(StandardCharsets.UTF_8);
        return new ExportFile(content, filename, CSV_CONTENT_TYPE);
    }

    /** @deprecated use exportReport with dates/roles */
    public byte[] exportPlaceholder(String format, String report) {
        return exportReport(format, report, LocalDate.now().minusDays(7), LocalDate.now(), null, "ADMIN")
                .content();
    }

    private boolean isDistributorOnly(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return false;
        }
        Set<String> roles = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .map(r -> r.toUpperCase(Locale.ROOT))
                .filter(r -> !r.isEmpty())
                .collect(Collectors.toSet());
        if (roles.contains("ADMIN") || roles.contains("SUPERVISOR")) {
            return false;
        }
        return roles.contains("DISTRIBUTEUR") || roles.contains("DISTRIBUTOR");
    }

    private Long resolveDistributorId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "X-User-Id header required");
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM distributor_accounts WHERE user_id = ? AND active = TRUE ORDER BY id LIMIT 1",
                (rs, rowNum) -> rs.getLong(1),
                userId);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "No distributor account for user " + userId);
        }
        return ids.getFirst();
    }

    private BigDecimal sumColumn(List<Map<String, Object>> rows, String key) {
        return rows.stream()
                .map(r -> r.get(key))
                .filter(Objects::nonNull)
                .map(v -> v instanceof BigDecimal bd ? bd : new BigDecimal(v.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private double toDouble(Object value) {
        if (value == null) return 0d;
        if (value instanceof Number n) return n.doubleValue();
        return Double.parseDouble(value.toString());
    }

    private String toCsv(List<Map<String, Object>> rows, List<String> columns) {
        StringBuilder sb = new StringBuilder(UTF8_BOM);
        sb.append(columns.stream()
                        .map(col -> csvEscape(CSV_HEADERS_FR.getOrDefault(col, col)))
                        .collect(Collectors.joining(String.valueOf(CSV_SEPARATOR))))
                .append('\n');
        if (rows == null) {
            return sb.toString();
        }
        for (Map<String, Object> row : rows) {
            sb.append(columns.stream()
                            .map(col -> csvEscape(formatCsvValue(row.get(col))))
                            .collect(Collectors.joining(String.valueOf(CSV_SEPARATOR))))
                    .append('\n');
        }
        return sb.toString();
    }

    private String formatCsvValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(CSV_SEPARATOR) >= 0 || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
