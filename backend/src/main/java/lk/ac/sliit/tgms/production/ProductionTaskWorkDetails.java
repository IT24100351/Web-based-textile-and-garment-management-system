package lk.ac.sliit.tgms.production;

import java.time.Instant;

/** Persisted Production Manager work details for one stable production task. */
public record ProductionTaskWorkDetails(
        long productionTaskId,
        String workDetails,
        String workAssignment,
        String workNotes,
        Instant createdAt,
        Instant updatedAt) {}
