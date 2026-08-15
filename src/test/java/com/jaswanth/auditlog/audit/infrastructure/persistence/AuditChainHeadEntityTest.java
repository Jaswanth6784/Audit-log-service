package com.jaswanth.auditlog.audit.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditChainHeadEntityTest {

    @Test
    void advancesOnlyToAHigherSequence() {
        var chainHead = new AuditChainHeadEntity();

        chainHead.advance(1, "a".repeat(64));

        assertThat(chainHead.getLastSequence()).isEqualTo(1);
        assertThat(chainHead.getLastHash()).isEqualTo("a".repeat(64));
        assertThatThrownBy(() -> chainHead.advance(1, "b".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("monotonically");
    }
}
