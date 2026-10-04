package com.medisphere.audit;

import com.medisphere.domain.AuditLog;
import com.medisphere.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService Unit Tests")
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository);
    }

    @Test
    @DisplayName("log() saves audit entry with correct fields")
    void log_savesCorrectAuditEntry() {
        AuditContext ctx = AuditContext.builder()
                .userId("PROV-001")
                .userName("Dr. A. Mehta")
                .userRole("CLINICIAN")
                .action("Login successful")
                .module("Authentication")
                .status("Success")
                .ipAddress("10.0.0.1")
                .build();

        when(auditLogRepository.save(org.mockito.ArgumentMatchers.any(AuditLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Call directly (bypasses @Async for unit test)
        auditService.log(ctx);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("PROV-001");
        assertThat(saved.getUserName()).isEqualTo("Dr. A. Mehta");
        assertThat(saved.getUserRole()).isEqualTo("CLINICIAN");
        assertThat(saved.getAction()).isEqualTo("Login successful");
        assertThat(saved.getModule()).isEqualTo("Authentication");
        assertThat(saved.getStatus()).isEqualTo("Success");
        assertThat(saved.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(saved.getTimestamp()).isNotNull();
        assertThat(saved.getId()).isNotBlank().startsWith("AU-");
    }

    @Test
    @DisplayName("log() with denied status writes Denied")
    void log_deniedStatus_writesDenied() {
        AuditContext ctx = AuditContext.denied("PROV-001", "Dr. A. Mehta", "CLINICIAN",
                "Export Patient Record", "Reports");

        when(auditLogRepository.save(org.mockito.ArgumentMatchers.any(AuditLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        auditService.log(ctx);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("Denied");
    }

    @Test
    @DisplayName("log() does NOT throw if repository.save() throws — audit must never crash app")
    void log_repositoryThrows_doesNotPropagateException() {
        AuditContext ctx = AuditContext.success("PROV-001", "Admin", "ADMIN",
                "View Patient", "Patients");

        when(auditLogRepository.save(org.mockito.ArgumentMatchers.any(AuditLog.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        // Must not throw — audit failure must be silent
        assertThatNoException().isThrownBy(() -> auditService.log(ctx));
    }

    @Test
    @DisplayName("AuditContext.success factory sets correct status")
    void auditContextSuccess_setsStatusSuccess() {
        AuditContext ctx = AuditContext.success("u1", "Name", "CLINICIAN", "action", "mod");
        assertThat(ctx.status()).isEqualTo("Success");
        assertThat(ctx.userId()).isEqualTo("u1");
    }

    @Test
    @DisplayName("AuditContext.denied factory sets correct status")
    void auditContextDenied_setsStatusDenied() {
        AuditContext ctx = AuditContext.denied("u1", "Name", "ANALYST", "action", "mod");
        assertThat(ctx.status()).isEqualTo("Denied");
    }
}
