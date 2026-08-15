package com.jaswanth.auditlog;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("h2")
class AuditLogApplicationTest {

    @Autowired
    private OpenAPI openAPI;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void contextStartsWithFlywayAndOpenApiConfiguration() {
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Audit Log Service API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1");
        assertThat(openAPI.getComponents().getSecuritySchemes())
                .containsKeys("basicAuth", "bearerAuth");

        var chainHead = jdbcClient.sql("""
                        SELECT last_sequence, last_hash
                        FROM audit_chain_head
                        WHERE chain_id = 1
                        """)
                .query((resultSet, rowNumber) -> new ChainHead(
                        resultSet.getLong("last_sequence"),
                        resultSet.getString("last_hash")))
                .single();

        assertThat(chainHead.lastSequence()).isZero();
        assertThat(chainHead.lastHash()).isEqualTo("0".repeat(64));

        var auditEventCount = jdbcClient.sql("SELECT COUNT(*) FROM audit_event")
                .query(Long.class)
                .single();
        assertThat(auditEventCount).isZero();
    }

    private record ChainHead(long lastSequence, String lastHash) {
    }
}
