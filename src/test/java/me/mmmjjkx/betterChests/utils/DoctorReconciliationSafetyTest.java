package me.mmmjjkx.betterChests.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DoctorReconciliationSafetyTest {

    @Test
    void drawerReadNeverSealsUnresolvedOrCorruptStateAsEmpty() throws IOException {
        String storage = source("src/main/java/me/mmmjjkx/betterChests/storage/DrawerStorage.java");
        String readBody = between(
                storage,
                "public static DrawerData read(Block block)",
                "public static Inspection inspect(Block block)");

        assertTrue(readBody.contains("case LEGACY_RECOVERABLE"));
        assertTrue(readBody.contains("preserving the raw record"));
        assertFalse(readBody.contains("write(block, DrawerData.empty())"));
    }

    @Test
    void doctorRepairIsLoadedOnlyAndPositiveEvidenceOnly() throws IOException {
        String doctor = source("src/main/java/me/mmmjjkx/betterChests/diagnostics/BetterChestsDoctor.java");

        assertTrue(doctor.contains("world.getLoadedChunks()"));
        assertTrue(doctor.contains("chunk.getTileEntities()"));
        assertFalse(doctor.contains("loadChunk("));
        assertFalse(doctor.contains("getChunkAt("));
        assertTrue(doctor.contains("DrawerStorage.inspect(block)"));
        assertTrue(doctor.contains("DrawerStorage.migrateLegacyIfRecoverable(block)"));
        assertTrue(doctor.contains("DrawerStorage.markCurrentVersionIfSafe(block)"));
        assertFalse(doctor.contains("DrawerStorage.clear("));
        assertFalse(doctor.contains("DrawerStorage.write("));
        assertTrue(doctor.contains("CURRENT_CORRUPT"));
        assertTrue(doctor.contains("preserved unchanged"));
        assertTrue(doctor.contains("LEGACY_PARTIAL"));
        assertTrue(doctor.contains("no migration attempted"));
        assertTrue(doctor.contains("LEGACY_WAITING_FOR_ENTITIES"));
        assertTrue(doctor.contains("did not force-load"));
    }

    @Test
    void ieStorageInspectionIsReadOnlyAndNeverNormalizesUnknownState() throws IOException {
        String doctor = source("src/main/java/me/mmmjjkx/betterChests/diagnostics/BetterChestsDoctor.java");
        String inspector = source("src/main/java/me/mmmjjkx/betterChests/diagnostics/IEStorageDoctorInspector.java");

        assertTrue(doctor.contains("slimefunItem instanceof IEStorageUnit unit"));
        assertTrue(doctor.contains("IEStorageDoctorInspector.inspect(block, unit)"));
        assertTrue(inspector.contains("String STORED_AMOUNT = \"stored\""));
        assertTrue(inspector.contains("amount > unit.getCapacity()"));
        assertTrue(inspector.contains("MALFORMED_COUNT"));
        assertTrue(inspector.contains("NEGATIVE_COUNT"));
        assertTrue(inspector.contains("OVER_CAPACITY"));
        assertTrue(inspector.contains("MISSING_DISPLAY_WITH_OUTPUT_EVIDENCE"));
        assertTrue(inspector.contains("ZERO_COUNT_WITH_DISPLAY"));
        assertFalse(inspector.contains("BlockStorage.addBlockInfo"));
        assertFalse(inspector.contains("replaceExistingItem"));
        assertFalse(inspector.contains("setAmount("));
    }

    @Test
    void unsafeIeStorageIsFrozenBeforeTickerCargoOrBreakCanRewriteIt() throws IOException {
        String cache = source("src/main/java/me/mmmjjkx/betterChests/items/chests/ie/IEStorageCache.java");
        String unit = source("src/main/java/me/mmmjjkx/betterChests/items/chests/ie/IEStorageUnit.java");

        assertTrue(cache.contains("boolean persistentStateSafe = true"));
        assertTrue(cache.contains("markPersistentStateUnsafe(\"stored count is malformed"));
        assertTrue(cache.contains("markPersistentStateUnsafe(\"stored count is negative"));
        assertTrue(cache.contains("exceeds capacity"));
        assertTrue(cache.contains("if (!persistentStateSafe) {\n            return;\n        }\n\n        // input output"));
        assertTrue(cache.contains("return persistentStateSafe && this.amount == 0;"));
        assertTrue(cache.contains("recoveredIdentity = output.clone()"));
        assertTrue(cache.contains("the output stack was left untouched"));
        assertFalse(cache.contains("menu.replaceExistingItem(OUTPUT_SLOT, null)"));

        int unsafeGuard = unit.indexOf("if (!cache.isPersistentStateSafe())");
        int removeCache = unit.indexOf("caches.remove(menu.getLocation())", unsafeGuard);
        int dropMenu = unit.indexOf("menu.dropItems(menu.getLocation(), INPUT_SLOT, OUTPUT_SLOT)", unsafeGuard);
        assertTrue(unsafeGuard >= 0);
        assertTrue(removeCache > unsafeGuard);
        assertTrue(dropMenu > removeCache);
        assertTrue(unit.substring(unsafeGuard, removeCache).contains("e.setCancelled(true)"));
        assertTrue(unit.substring(unsafeGuard, removeCache).contains("return;"));
    }

    @Test
    void legacyDoctorBridgeRemainsOptionalAndReflective() throws IOException {
        String bridge = source("src/main/java/me/mmmjjkx/betterChests/diagnostics/LegacyDoctorBridge.java");
        String plugin = source("src/main/java/me/mmmjjkx/betterChests/BetterChests.java");

        assertTrue(bridge.contains("Class.forName(DOCTOR_API, false, loader)"));
        assertFalse(bridge.contains("import io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctor"));
        assertTrue(bridge.contains("BetterChestsDoctor.run(repair)"));
        assertTrue(bridge.contains("unregisterAll(plugin)"));
        assertTrue(plugin.contains("LegacyDoctorBridge.register(this);"));
        assertTrue(plugin.contains("LegacyDoctorBridge.unregister(this);"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of(relative));
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0, "start marker missing: " + startMarker);
        assertTrue(end > start, "end marker missing: " + endMarker);
        return source.substring(start, end);
    }
}
