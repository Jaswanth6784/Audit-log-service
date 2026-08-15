package com.jaswanth.auditlog.audit.domain;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
public class PayloadCommitmentService {

    private static final String LEAF_DOMAIN = "audit-payload-leaf-v1";
    private static final String REDACTED_MARKER = "$redacted";
    private static final int SALT_BYTES = 32;

    private final CanonicalEventSerializer serializer;
    private final SecureRandom secureRandom;

    public PayloadCommitmentService(CanonicalEventSerializer serializer) {
        this.serializer = serializer;
        this.secureRandom = new SecureRandom();
    }

    public CommittedPayload commit(Map<String, Object> source) {
        var payload = serializer.canonicalizePayload(source);
        var proofs = new TreeMap<String, Object>();
        var tree = buildNewTree(payload, "", proofs);
        return new CommittedPayload(payload, asMap(tree), proofs);
    }

    public Map<String, Object> verifyAndBuildTree(
            Map<String, Object> payload,
            Map<String, Object> proofs) {
        if (proofs == null) {
            throw new InvalidPayloadProofException("Payload proofs are missing");
        }
        var visited = new HashSet<String>();
        var tree = buildVerifiedTree(payload, "", proofs, visited);
        if (visited.size() != proofs.size()) {
            throw new InvalidPayloadProofException("Payload proof paths do not match the payload structure");
        }
        return asMap(tree);
    }

    public RedactedPayload redact(
            Map<String, Object> payload,
            Map<String, Object> proofs,
            Collection<String> paths) {
        verifyAndBuildTree(payload, proofs);
        var targets = Set.copyOf(paths);
        var mutableProofs = copyProofs(proofs);
        for (var path : targets) {
            var proof = proofAt(mutableProofs, path);
            if (proof.get("salt") == null) {
                throw new InvalidPayloadProofException("Payload path is already redacted: " + path);
            }
        }
        var redacted = redactValue(payload, "", targets, mutableProofs);
        return new RedactedPayload(asMap(redacted), mutableProofs);
    }

    private Object buildNewTree(Object value, String path, Map<String, Object> proofs) {
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((key, nested) -> result.put(
                    requireStringKey(key),
                    buildNewTree(nested, child(path, requireStringKey(key)), proofs)));
            return result;
        }
        if (value instanceof Collection<?> collection) {
            var result = new ArrayList<>(collection.size());
            var index = 0;
            for (var nested : collection) {
                result.add(buildNewTree(nested, child(path, Integer.toString(index++)), proofs));
            }
            return result;
        }
        var salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        var saltHex = HexFormat.of().formatHex(salt);
        var commitment = commitment(saltHex, value);
        proofs.put(path, proof(commitment, saltHex));
        return Map.of("$commitment", commitment);
    }

    private Object buildVerifiedTree(
            Object value,
            String path,
            Map<String, Object> proofs,
            Set<String> visited) {
        if (proofs.containsKey(path)) {
            var proof = proofAt(proofs, path);
            var expectedCommitment = requireHash(proof.get("commitment"), path);
            var salt = proof.get("salt");
            if (salt == null) {
                if (!(value instanceof Map<?, ?> marker)
                        || marker.size() != 1
                        || !expectedCommitment.equals(marker.get(REDACTED_MARKER))) {
                    throw new InvalidPayloadProofException("Invalid redaction marker at payload path: " + path);
                }
            } else if (!expectedCommitment.equals(commitment(requireSalt(salt, path), value))) {
                throw new InvalidPayloadProofException("Payload leaf commitment mismatch at path: " + path);
            }
            visited.add(path);
            return Map.of("$commitment", expectedCommitment);
        }
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((key, nested) -> result.put(
                    requireStringKey(key),
                    buildVerifiedTree(nested, child(path, requireStringKey(key)), proofs, visited)));
            return result;
        }
        if (value instanceof Collection<?> collection) {
            var result = new ArrayList<>(collection.size());
            var index = 0;
            for (var nested : collection) {
                result.add(buildVerifiedTree(nested, child(path, Integer.toString(index++)), proofs, visited));
            }
            return result;
        }
        throw new InvalidPayloadProofException("Missing payload proof at path: " + path);
    }

    private Object redactValue(
            Object value,
            String path,
            Set<String> targets,
            Map<String, Object> proofs) {
        if (targets.contains(path)) {
            var proof = proofAt(proofs, path);
            var commitment = requireHash(proof.get("commitment"), path);
            proof.put("salt", null);
            return Map.of(REDACTED_MARKER, commitment);
        }
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((key, nested) -> result.put(
                    requireStringKey(key),
                    redactValue(nested, child(path, requireStringKey(key)), targets, proofs)));
            return result;
        }
        if (value instanceof Collection<?> collection) {
            var result = new ArrayList<>(collection.size());
            var index = 0;
            for (var nested : collection) {
                result.add(redactValue(nested, child(path, Integer.toString(index++)), targets, proofs));
            }
            return result;
        }
        return serializer.canonicalizeValue(value);
    }

    private Map<String, Object> copyProofs(Map<String, Object> proofs) {
        var result = new TreeMap<String, Object>();
        proofs.forEach((path, value) -> result.put(path, new LinkedHashMap<>(proofAt(proofs, path))));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> proofAt(Map<String, Object> proofs, String path) {
        var value = proofs.get(path);
        if (!(value instanceof Map<?, ?> map)) {
            throw new InvalidPayloadProofException("Unknown payload leaf path: " + path);
        }
        return (Map<String, Object>) map;
    }

    private Map<String, Object> proof(String commitment, String salt) {
        var result = new LinkedHashMap<String, Object>();
        result.put("commitment", commitment);
        result.put("salt", salt);
        return result;
    }

    private String commitment(String saltHex, Object value) {
        var valueBytes = serializer.serializeCanonicalValue(value);
        var prefix = (LEAF_DOMAIN + "\n" + saltHex + "\n").getBytes(StandardCharsets.UTF_8);
        var input = new byte[prefix.length + valueBytes.length];
        System.arraycopy(prefix, 0, input, 0, prefix.length);
        System.arraycopy(valueBytes, 0, input, prefix.length, valueBytes.length);
        return sha256(input);
    }

    private String requireHash(Object value, String path) {
        if (!(value instanceof String hash) || !hash.matches("[0-9a-f]{64}")) {
            throw new InvalidPayloadProofException("Invalid commitment at payload path: " + path);
        }
        return hash;
    }

    private String requireSalt(Object value, String path) {
        if (!(value instanceof String salt) || !salt.matches("[0-9a-f]{64}")) {
            throw new InvalidPayloadProofException("Invalid salt at payload path: " + path);
        }
        return salt;
    }

    private String requireStringKey(Object key) {
        if (!(key instanceof String stringKey)) {
            throw new InvalidPayloadProofException("Payload object keys must be strings");
        }
        return stringKey;
    }

    private String child(String parent, String token) {
        return parent + "/" + token.replace("~", "~0").replace("/", "~1");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }
}
