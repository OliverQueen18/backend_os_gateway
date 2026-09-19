package com.osgateway.reporting.application;

import com.osgateway.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {
    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks ReportingService reportingService;

    @Test
    void exportCsv_usesFrenchHeadersAndBom() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 8);
        when(jdbcTemplate.queryForList(anyString(), eq(from), eq(to)))
                .thenReturn(List.of(Map.of(
                        "operator", "ORANGE",
                        "type", "DEPOT",
                        "status", "SUCCESS",
                        "count", 2,
                        "total_amount", 1000,
                        "total_commission", 15,
                        "total_admin_commission", 6,
                        "total_distributor_commission", 9)));

        ExportFile file = reportingService.exportReport("csv", "transactions", from, to, 1L, "ADMIN");

        String csv = new String(file.content(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("Opérateur;Type;Statut;Volume"));
        assertTrue(file.filename().equals("transactions_2026-08-01_2026-08-08.csv"));
        assertTrue(file.contentType().startsWith("text/csv"));
    }

    @Test
    void exportPdf_isRejected() {
        assertThrows(BusinessException.class, () ->
                reportingService.exportReport(
                        "pdf",
                        "transactions",
                        LocalDate.now().minusDays(7),
                        LocalDate.now(),
                        1L,
                        "ADMIN"));
    }
}
