package dev.superseller.veltryx.model;

import java.util.UUID;

/** A player report submitted with /report. */
public record ReportEntry(String id, UUID target, String targetName, UUID reporter, String reporterName,
                          String reason, long issued, boolean resolved) {
}
