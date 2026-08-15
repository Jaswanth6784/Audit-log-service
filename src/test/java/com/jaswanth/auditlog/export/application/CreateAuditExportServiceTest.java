package com.jaswanth.auditlog.export.application;

import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditChainHeadEntity;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditChainHeadRepository;
import com.jaswanth.auditlog.audit.infrastructure.persistence.AuditEventRepository;
import com.jaswanth.auditlog.export.configuration.ExportProperties;
import com.jaswanth.auditlog.export.infrastructure.ExportSignatureService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreateAuditExportServiceTest {

    @Test
    void rejectsAChainAboveTheSynchronousLimitBeforeScanningEvents() {
        var heads = mock(AuditChainHeadRepository.class);
        var events = mock(AuditEventRepository.class);
        var signatures = mock(ExportSignatureService.class);
        var head = mock(AuditChainHeadEntity.class);
        when(head.getLastSequence()).thenReturn(3L);
        when(heads.findById((short) 1)).thenReturn(Optional.of(head));
        var service = new CreateAuditExportService(
                heads,
                events,
                signatures,
                new ExportProperties("test", "private", "public", 2),
                Clock.systemUTC());

        assertThatThrownBy(() -> service.create(new ExportScope("actor-1", null, null)))
                .isInstanceOf(ExportTooLargeException.class)
                .hasMessage("Audit chain exceeds the synchronous export limit of 2 events");
        verifyNoInteractions(events, signatures);
    }
}
