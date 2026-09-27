package me.mmmjjkx.betterChests.diagnostics;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Compatibility boundary for read-only BetterChests Doctor access to Slimefun block data.
 *
 * <p>Slimefun Legacy exposes the current BlockDataController API, while the historical
 * RC-37 compile/runtime floor only exposes BlockStorage. The Doctor must preserve the
 * exact existing persisted keys and loaded inventory state, so this adapter reads the
 * modern controller when available and isolates the RC-37 fallback in one place.</p>
 */
final class SlimefunBlockDataCompat {

    private static final Accessor ACCESSOR = createAccessor();

    private SlimefunBlockDataCompat() {
    }

    static Snapshot read(Block block, String key) {
        return ACCESSOR.read(block.getLocation(), key);
    }

    private static Accessor createAccessor() {
        try {
            Method getDatabaseManager = Slimefun.class.getMethod("getDatabaseManager");
            Method getBlockDataController =
                    getDatabaseManager.getReturnType().getMethod("getBlockDataController");
            Class<?> controllerType = getBlockDataController.getReturnType();
            Method getBlockData = controllerType.getMethod("getBlockData", Location.class);
            Class<?> blockDataType = getBlockData.getReturnType();
            Method isDataLoaded = blockDataType.getMethod("isDataLoaded");
            Method loadBlockData = controllerType.getMethod("loadBlockData", blockDataType);
            Method getData = blockDataType.getMethod("getData", String.class);
            Method getBlockMenu = blockDataType.getMethod("getBlockMenu");

            return (location, key) -> readModern(
                    location,
                    key,
                    getDatabaseManager,
                    getBlockDataController,
                    getBlockData,
                    isDataLoaded,
                    loadBlockData,
                    getData,
                    getBlockMenu);
        } catch (NoSuchMethodException | LinkageError ignored) {
            // Upstream RC-37 predates BlockDataController.
            return LegacyBlockStorageAccess::read;
        }
    }

    private static Snapshot readModern(
            Location location,
            String key,
            Method getDatabaseManager,
            Method getBlockDataController,
            Method getBlockData,
            Method isDataLoaded,
            Method loadBlockData,
            Method getData,
            Method getBlockMenu) {
        try {
            Object databaseManager = getDatabaseManager.invoke(null);
            Object controller = getBlockDataController.invoke(databaseManager);
            Object blockData = getBlockData.invoke(controller, location);
            if (blockData == null) {
                return new Snapshot(null, null);
            }

            if (!Boolean.TRUE.equals(isDataLoaded.invoke(blockData))) {
                loadBlockData.invoke(controller, blockData);
            }

            String value = (String) getData.invoke(blockData, key);
            BlockMenu menu = (BlockMenu) getBlockMenu.invoke(blockData);
            return new Snapshot(value, menu);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not access Slimefun Legacy block-data API", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Slimefun Legacy block-data lookup failed", cause);
        }
    }

    record Snapshot(String value, BlockMenu menu) {
    }

    @FunctionalInterface
    private interface Accessor {
        Snapshot read(Location location, String key);
    }

    /**
     * Intentional compatibility boundary for upstream Slimefun RC-37.
     * Slimefun Legacy does not select this path.
     */
    @SuppressWarnings("deprecation")
    private static final class LegacyBlockStorageAccess {

        private LegacyBlockStorageAccess() {
        }

        private static Snapshot read(Location location, String key) {
            me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config config =
                    me.mrCookieSlime.Slimefun.api.BlockStorage.getLocationInfo(location);
            String value = config == null ? null : config.getString(key);
            BlockMenu menu = me.mrCookieSlime.Slimefun.api.BlockStorage.getInventory(location);
            return new Snapshot(value, menu);
        }
    }
}
