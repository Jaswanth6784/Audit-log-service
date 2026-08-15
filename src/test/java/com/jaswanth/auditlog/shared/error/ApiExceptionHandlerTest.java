package com.jaswanth.auditlog.shared.error;

import com.jaswanth.auditlog.export.application.ExportTooLargeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    @Test
    void mapsOversizedExportsToHttp413() {
        var problem = new ApiExceptionHandler().handleExportTooLarge(new ExportTooLargeException(100));

        assertThat(problem.getStatus()).isEqualTo(413);
        assertThat(problem.getTitle()).isEqualTo("Audit export is too large");
    }
}
