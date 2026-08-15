package com.jaswanth.auditlog.export.infrastructure;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

@Component
public class CanonicalExportSerializer {

    private final ObjectMapper objectMapper;

    public CanonicalExportSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] serialize(Object value) {
        try {
            var converted = objectMapper.convertValue(value, Map.class);
            return objectMapper.writeValueAsBytes(canonicalize(converted));
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Export bundle cannot be serialized canonically", exception);
        }
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((key, nested) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException("Export object keys must be strings");
                }
                result.put(stringKey, canonicalize(nested));
            });
            return result;
        }
        if (value instanceof Collection<?> collection) {
            var result = new ArrayList<>(collection.size());
            collection.forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        return value;
    }
}
