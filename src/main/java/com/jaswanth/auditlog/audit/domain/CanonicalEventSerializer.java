package com.jaswanth.auditlog.audit.domain;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

@Component
public class CanonicalEventSerializer {

    private final ObjectMapper objectMapper;

    public CanonicalEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CanonicalEvent serialize(AuditEventContent event) {
        var canonicalPayload = canonicalizeMap(event.payload());
        return serialize(event, canonicalPayload);
    }

    CanonicalEvent serialize(AuditEventContent event, Map<String, Object> payloadRepresentation) {
        var canonicalEvent = new TreeMap<String, Object>();
        canonicalEvent.put("actorId", event.actorId());
        canonicalEvent.put("eventType", event.eventType());
        canonicalEvent.put("payload", payloadRepresentation);
        canonicalEvent.put("resourceId", event.resourceId());
        canonicalEvent.put("resourceType", event.resourceType());
        canonicalEvent.put("timestamp", event.timestamp().toString());

        try {
            return new CanonicalEvent(objectMapper.writeValueAsBytes(canonicalEvent), payloadRepresentation);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Event payload cannot be serialized canonically", exception);
        }
    }

    Map<String, Object> canonicalizePayload(Map<String, Object> source) {
        return canonicalizeMap(source);
    }

    byte[] serializeCanonicalValue(Object value) {
        try {
            return objectMapper.writeValueAsBytes(canonicalizeValue(value));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Payload value cannot be serialized canonically", exception);
        }
    }

    private Map<String, Object> canonicalizeMap(Map<String, Object> source) {
        var result = new TreeMap<String, Object>();
        source.forEach((key, value) -> result.put(key, canonicalizeValue(value)));
        return result;
    }

    Object canonicalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((key, nestedValue) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException("Payload object keys must be strings");
                }
                result.put(stringKey, canonicalizeValue(nestedValue));
            });
            return result;
        }
        if (value instanceof Collection<?> collection) {
            var result = new ArrayList<>(collection.size());
            collection.forEach(item -> result.add(canonicalizeValue(item)));
            return result;
        }
        return value;
    }

    public record CanonicalEvent(byte[] bytes, Map<String, Object> payload) {
    }
}
