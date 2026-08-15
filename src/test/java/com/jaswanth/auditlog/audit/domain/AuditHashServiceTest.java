package com.jaswanth.auditlog.audit.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditHashServiceTest {

    private AuditHashService hashService;

    @BeforeEach
    void setUp() {
        hashService = new AuditHashService(new CanonicalEventSerializer(new ObjectMapper()));
    }

    @Test
    void producesStableGoldenHashesRegardlessOfPayloadKeyOrder() {
        var nestedForward = new LinkedHashMap<String, Object>();
        nestedForward.put("z", "last");
        nestedForward.put("a", "first");
        var forward = new LinkedHashMap<String, Object>();
        forward.put("z", 2);
        forward.put("nested", nestedForward);
        forward.put("a", 1);

        var nestedReverse = new LinkedHashMap<String, Object>();
        nestedReverse.put("a", "first");
        nestedReverse.put("z", "last");
        var reverse = new LinkedHashMap<String, Object>();
        reverse.put("a", 1);
        reverse.put("nested", nestedReverse);
        reverse.put("z", 2);

        var first = hashService.calculate(event(forward), AuditHashService.GENESIS_HASH);
        var second = hashService.calculate(event(reverse), AuditHashService.GENESIS_HASH);

        assertThat(first.contentHash())
                .isEqualTo("d4539fc67d640cc398fd34a744ba8cc5e224041b08edc8ffee01bd22fd263abb")
                .isEqualTo(second.contentHash());
        assertThat(first.recordHash())
                .isEqualTo("5baefcd136ccbbd3127bde32ab8e43bd690cf3c147ba7645984ac57e18440a09")
                .isEqualTo(second.recordHash());
        assertThat(first.canonicalPayload().keySet()).containsExactly("a", "nested", "z");
    }

    @Test
    void canonicalizesObjectsInsideArraysAndRejectsInvalidPreviousHash() {
        var payload = Map.<String, Object>of(
                "items", List.of(Map.of("z", 2, "a", 1)));

        var hashes = hashService.calculate(event(payload), AuditHashService.GENESIS_HASH);

        assertThat(hashes.hashVersion()).isEqualTo((short) 1);
        assertThat(hashes.contentHash()).matches("[0-9a-f]{64}");
        assertThatThrownBy(() -> hashService.calculate(event(payload), "INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64 lowercase hexadecimal");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void rejectsNonStringNestedObjectKeys() {
        Map invalidNestedMap = Map.of(7, "value");
        Map<String, Object> payload = Map.of("invalid", invalidNestedMap);

        assertThatThrownBy(() -> hashService.calculate(event(payload), AuditHashService.GENESIS_HASH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payload object keys must be strings");
    }

    private AuditEventContent event(Map<String, Object> payload) {
        return new AuditEventContent(
                "RECORD_UPDATED",
                "actor-7",
                "ACCOUNT",
                "resource-9",
                payload,
                Instant.parse("2026-08-15T10:15:30.123456Z"));
    }
}
