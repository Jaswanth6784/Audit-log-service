package com.jaswanth.auditlog.export.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportScopeTest {

    @Test
    void createsActorAndResourceDescriptors() {
        assertThat(new ExportScope("actor-1", null, null).descriptor())
                .extracting("type", "actorId", "resourceType", "resourceId")
                .containsExactly("ACTOR", "actor-1", null, null);
        assertThat(new ExportScope(null, "ACCOUNT", "resource-1").descriptor())
                .extracting("type", "actorId", "resourceType", "resourceId")
                .containsExactly("RESOURCE", null, "ACCOUNT", "resource-1");
    }

    @Test
    void rejectsMissingPartialAndAmbiguousScopes() {
        assertThatThrownBy(() -> new ExportScope(null, null, null))
                .isInstanceOf(InvalidExportScopeException.class);
        assertThatThrownBy(() -> new ExportScope(null, "ACCOUNT", null))
                .isInstanceOf(InvalidExportScopeException.class);
        assertThatThrownBy(() -> new ExportScope("actor-1", "ACCOUNT", "resource-1"))
                .isInstanceOf(InvalidExportScopeException.class);
        assertThatThrownBy(() -> new ExportScope(" ", null, null))
                .isInstanceOf(InvalidExportScopeException.class);
    }
}
