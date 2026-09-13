package me.mmmjjkx.betterChests.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result returned by a BetterChests reconciliation pass. */
public final class BetterChestsDoctorReport {

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
        this.details = Collections.unmodifiableList(new ArrayList<>(details));
    }

    public long getScannedEntries() {
        return scannedEntries;
    }

    public long getIssuesFound() {
        return issuesFound;
    }

    public long getRepairedEntries() {
        return repairedEntries;
    }

    public long getFailures() {
        return failures;
    }

    public List<String> getDetails() {
        return details;
    }
}
