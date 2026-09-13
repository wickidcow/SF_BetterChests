package me.mmmjjkx.betterChests.diagnostics;

import me.mmmjjkx.betterChests.BetterChests;
import me.mmmjjkx.betterChests.items.chests.SimpleDrawer;
import me.mmmjjkx.betterChests.items.chests.ie.IEStorageUnit;
import me.mmmjjkx.betterChests.storage.DrawerStorage;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** Loaded-only reconciliation for BetterChests persistent storage blocks. */
public final class BetterChestsDoctor {

    private static final int MAX_DETAILS = 40;

    private BetterChestsDoctor() {
    }

    public static BetterChestsDoctorReport run(boolean repair) {
        long scanned = 0L;
        long issues = 0L;
        long repaired = 0L;
        long failures = 0L;
        List<String> details = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (!chunk.isLoaded()) {
                    continue;
                }

                final BlockState[] tileEntities;
                try {
                    tileEntities = chunk.getTileEntities();
                } catch (RuntimeException | LinkageError exception) {
                    failures++;
                    BetterChests.INSTANCE.getLogger().log(
                            Level.WARNING,
                            "BetterChests Doctor could not inspect loaded chunk "
                                    + world.getName() + ':' + chunk.getX() + ',' + chunk.getZ() + '.',
                            exception);
                    continue;
                }

                for (BlockState state : tileEntities) {
                    Block block = state.getBlock();
                    try {
                        var slimefunItem = BlockStorage.check(block);
                        if (slimefunItem instanceof SimpleDrawer) {
                            scanned++;
                            DrawerResult result = inspectDrawer(block, repair, details);
                            issues += result.issues();
                            repaired += result.repaired();
                        } else if (slimefunItem instanceof IEStorageUnit unit) {
                            scanned++;
                            IEStorageDoctorInspector.Result result = IEStorageDoctorInspector.inspect(block, unit);
                            if (!result.healthy()) {
                                issues++;
                                addDetail(details, location(block) + " IE storage manual: " + result.detail());
                            }
                        }
                    } catch (RuntimeException | LinkageError exception) {
                        failures++;
                        addDetail(details, location(block) + " failure: storage inspection aborted safely.");
                        BetterChests.INSTANCE.getLogger().log(
                                Level.WARNING,
                                "BetterChests Doctor failed safely at " + location(block) + '.',
                                exception);
                    }
                }
            }
        }

        return new BetterChestsDoctorReport(scanned, issues, repaired, failures, details);
    }

    private static DrawerResult inspectDrawer(Block block, boolean repair, List<String> details) {
        long issues = 0L;
        long repaired = 0L;
        DrawerStorage.Inspection inspection = DrawerStorage.inspect(block);
        switch (inspection.status()) {
            case CURRENT_VALID, CURRENT_EMPTY, LEGACY_NO_EVIDENCE -> {
                // Healthy current data, a confirmed current empty drawer, or no positive
                // legacy evidence. None of these requires a destructive guess.
            }
            case CURRENT_VERSION_MISSING -> {
                issues++;
                if (repair && DrawerStorage.markCurrentVersionIfSafe(block)) {
                    repaired++;
                    addDetail(details, location(block)
                            + " repaired: valid v2 drawer data received the missing schema marker.");
                } else {
                    addDetail(details, location(block) + " issue: " + inspection.detail());
                }
            }
            case LEGACY_RECOVERABLE -> {
                issues++;
                if (repair && DrawerStorage.migrateLegacyIfRecoverable(block)) {
                    repaired++;
                    SimpleDrawer.repair(block);
                    addDetail(details, location(block)
                            + " migrated: recoverable Dev-16 item/count state was promoted to v2.");
                } else {
                    addDetail(details, location(block)
                            + " ready: recoverable Dev-16 drawer state can be migrated safely.");
                }
            }
            case CURRENT_VERSION_UNSUPPORTED -> {
                issues++;
                addDetail(details, location(block)
                        + " manual: unknown drawer schema marker; no downgrade/overwrite attempted.");
            }
            case CURRENT_INCONSISTENT -> {
                issues++;
                addDetail(details, location(block)
                        + " manual: inconsistent v2 item/count fields were preserved unchanged.");
            }
            case CURRENT_CORRUPT -> {
                issues++;
                addDetail(details, location(block)
                        + " manual: corrupt v2 stored-item payload was preserved unchanged.");
            }
            case LEGACY_PARTIAL -> {
                issues++;
                addDetail(details, location(block)
                        + " manual: only part of the Dev-16 item/count state is recoverable; no migration attempted.");
            }
            case LEGACY_WAITING_FOR_ENTITIES -> {
                issues++;
                addDetail(details, location(block)
                        + " deferred: legacy display entities are not loaded; Doctor did not force-load them.");
            }
        }
        return new DrawerResult(issues, repaired);
    }

    private static void addDetail(List<String> details, String detail) {
        if (details.size() < MAX_DETAILS) {
            details.add(detail);
        }
    }

    private static String location(Block block) {
        return String.format(
                Locale.ROOT,
                "%s:%d,%d,%d",
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ());
    }

    private record DrawerResult(long issues, long repaired) {
    }
}
