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

/** Loaded-only reconciliation for BetterChests persistent storage blocks. */
final class BetterChestsDoctor {

    private static final int MAX_DETAILS = 40;

    private BetterChestsDoctor() {
    }

    static BetterChestsDoctorReport run(boolean repair) {
        long scanned = 0;
        long issues = 0;
        long repaired = 0;
        long failures = 0;
        List<String> details = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (!chunk.isLoaded()) {
                    continue;
                }

                for (BlockState state : chunk.getTileEntities()) {
                    Block block = state.getBlock();
                    try {
                        var slimefunItem = BlockStorage.check(block);
                        if (slimefunItem instanceof SimpleDrawer) {
                            scanned++;
                            DrawerStorage.Inspection inspection = DrawerStorage.inspect(block);
                            switch (inspection.status()) {
                                case MODERN_VALID, MODERN_EMPTY -> {
                                    // Healthy current-format drawer.
                                }
                                case LEGACY_RECOVERABLE -> {
                                    issues++;
                                    addDetail(details, block,
                                            "recoverable Dev-16 drawer state: "
                                                    + inspection.data().count() + " stored item(s)");
                                    if (repair && DrawerStorage.migrateLegacyIfRecoverable(block)) {
                                        repaired++;
                                        SimpleDrawer.repair(block);
                                    }
                                }
                                case LEGACY_UNRESOLVED -> {
                                    issues++;
                                    addDetail(details, block,
                                            "legacy/uninitialized drawer needs manual verification: "
                                                    + inspection.detail());
                                }
                                case CORRUPT_MODERN -> {
                                    issues++;
                                    addDetail(details, block,
                                            "corrupt/inconsistent v2 drawer left untouched: "
                                                    + inspection.detail());
                                }
                            }
                        } else if (slimefunItem instanceof IEStorageUnit unit) {
                            scanned++;
                            IEStorageDoctorInspector.Result result = IEStorageDoctorInspector.inspect(block, unit);
                            if (!result.healthy()) {
                                issues++;
                                addDetail(details, block, "IE storage manual: " + result.detail());
                            }
                        }
                    } catch (RuntimeException | LinkageError exception) {
                        failures++;
                        BetterChests.INSTANCE.getLogger().warning(
                                "Doctor could not inspect BetterChests storage at " + format(block) + ": "
                                        + exception.getMessage());
                        addDetail(details, block, "inspection failed safely; storage left untouched");
                    }
                }
            }
        }

        return new BetterChestsDoctorReport(scanned, issues, repaired, failures, details);
    }

    private static void addDetail(List<String> details, Block block, String message) {
        if (details.size() < MAX_DETAILS) {
            details.add(format(block) + " - " + message);
        }
    }

    private static String format(Block block) {
        return String.format(
                Locale.ROOT,
                "%s:%d,%d,%d",
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ());
    }
}
