package com.jaswanth.auditlog.compliance.application;

import com.jaswanth.auditlog.audit.application.AppendAuditEventCommand;
import com.jaswanth.auditlog.audit.application.AppendAuditEventService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

@Service
public class RecordComplianceAccessService {

    public static final String EVENT_TYPE = "CLIENT_ACCOUNT_DATA_ACCESS";
    public static final String RESOURCE_TYPE = "CLIENT_ACCOUNT";

    private final AppendAuditEventService appendService;

    public RecordComplianceAccessService(AppendAuditEventService appendService) {
        this.appendService = appendService;
    }

    public RecordedComplianceAccessEvent record(ComplianceAccessCommand command) {
        var categories = command.dataCategories().stream()
                .distinct()
                .sorted()
                .toList();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("action", command.action().name());
        payload.put("outcome", command.outcome().name());
        payload.put("dataCategories", categories.stream().map(Enum::name).toList());
        payload.put("purposeCode", command.purposeCode().name());
        payload.put("sourceSystem", command.identity().sourceSystem());
        payload.put("correlationId", command.correlationId().toString());
        if (command.reasonCode() != null) {
            payload.put("reasonCode", command.reasonCode().name());
        }

        var event = appendService.append(new AppendAuditEventCommand(
                EVENT_TYPE,
                command.identity().actorId(),
                RESOURCE_TYPE,
                command.accountId(),
                payload,
                command.timestamp()));
        return new RecordedComplianceAccessEvent(
                event,
                command.action(),
                command.outcome(),
                categories,
                command.purposeCode(),
                command.identity().sourceSystem(),
                command.correlationId(),
                command.reasonCode());
    }
}
