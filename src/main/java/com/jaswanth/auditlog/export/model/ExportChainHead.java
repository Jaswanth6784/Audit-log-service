package com.jaswanth.auditlog.export.model;

public record ExportChainHead(
        long sequenceNumber,
        String recordHash) {
}
