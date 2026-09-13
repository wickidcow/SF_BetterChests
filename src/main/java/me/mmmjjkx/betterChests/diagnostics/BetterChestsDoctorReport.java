package me.mmmjjkx.betterChests.diagnostics;

import java.util.List;

/** Immutable result of a BetterChests Doctor reconciliation pass. */
final class BetterChestsDoctorReport {
    private final long scannedEntries;
    private final long issuesFound;
    private final long repairedEntries;
    private final long failures;
    private final List<String> details;

    BetterChestsDoctorReport(
            long scannedEntries,
            long issuesFound,
            long repairedEntries,
            long failures,
            List<String> details) {
        this.scannedEntries = scannedEntries;
        this.issuesFound = issuesFound;
        this.repairedEntries = repairedEntries;
        this.failures = failures;
        this.details = List.copyOf(details);
    }

    long getScannedEntries() {
        return scannedEntries;
    }

    long getIssuesFound() {
        return issuesFound;
    }

    long getRepairedEntries() {
        return repairedEntries;
    }

    long getFailures() {
        return failures;
    }

    List<String> getDetails() {
        return details;
    }
}
