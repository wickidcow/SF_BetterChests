package me.mmmjjkx.betterChests.diagnostics;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Location;
import org.bukkit.block.Block;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

/**
 * Read-only compatibility access for Doctor inspections.
 *
 * <p>Slimefun Legacy exposes the current BlockDataController while the RC-37
 * compile/runtime compatibility floor only exposes BlockStorage. Doctor must
 * support both without changing persisted BetterChests data.</p>
 */
final class SlimefunDoctorBlockAccess {

    private static final Reader READER = createReader();

    private SlimefunDoctorBlockAccess() {
    }

    @Nullable
    static SlimefunItem getSlimefunItem(@Nonnull Block block) {
        return READER.getSlimefunItem(block);
    }

    @Nonnull
    static BlockValue readValueAndMenu(@Nonnull Block block, @Nonnull String key) {
        return READER.readValueAndMenu(block, key);
    }

    @Nonnull
    private static Reader createReader() {
        try {
            Method getDatabaseManager = Slimefun.class.getMethod("getDatabaseManager");
            Method getBlockDataController = getDatabaseManager.getReturnType().getMethod("getBlockDataController");
            Class<?> controllerType = getBlockDataController.getReturnType();
            Method getBlockData = controllerType.getMethod("getBlockData", Location.class);
            Class<?> blockDataType = getBlockData.getReturnType();
            Method loadBlockData = controllerType.getMethod("loadBlockData", blockDataType);
            Method isDataLoaded = blockDataType.getMethod("isDataLoaded");
            Method getSfId = blockDataType.getMethod("getSfId");
            Method getData = blockDataType.getMethod("getData", String.class);
            Method getBlockMenu = blockDataType.getMethod("getBlockMenu");

            return new ModernReader(
                    getDatabaseManager,
                    getBlockDataController,
                    getBlockData,
                    loadBlockData,
                    isDataLoaded,
                    getSfId,
                    getData,
                    getBlockMenu);
        } catch (NoSuchMethodException | LinkageError ignored) {
            return new LegacyReader();
        }
    }

    record BlockValue(@Nullable String value, @Nullable BlockMenu menu) {
    }

    private interface Reader {
        @Nullable
        SlimefunItem getSlimefunItem(@Nonnull Block block);

        @Nonnull
        BlockValue readValueAndMenu(@Nonnull Block block, @Nonnull String key);
    }

    private static final class ModernReader implements Reader {

        private final Method getDatabaseManager;
        private final Method getBlockDataController;
        private final Method getBlockData;
        private final Method loadBlockData;
        private final Method isDataLoaded;
        private final Method getSfId;
        private final Method getData;
        private final Method getBlockMenu;

        private ModernReader(
                Method getDatabaseManager,
                Method getBlockDataController,
                Method getBlockData,
                Method loadBlockData,
                Method isDataLoaded,
                Method getSfId,
                Method getData,
                Method getBlockMenu) {
            this.getDatabaseManager = getDatabaseManager;
            this.getBlockDataController = getBlockDataController;
            this.getBlockData = getBlockData;
            this.loadBlockData = loadBlockData;
            this.isDataLoaded = isDataLoaded;
            this.getSfId = getSfId;
            this.getData = getData;
            this.getBlockMenu = getBlockMenu;
        }

        @Override
        @Nullable
        public SlimefunItem getSlimefunItem(@Nonnull Block block) {
            Object blockData = getLoadedBlockData(block.getLocation());
            if (blockData == null) {
                return null;
            }

            try {
                return SlimefunItem.getById((String) getSfId.invoke(blockData));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not access Slimefun Legacy block id", exception);
            } catch (InvocationTargetException exception) {
                throw rethrow("Slimefun Legacy block id lookup failed", exception);
            }
        }

        @Override
        @Nonnull
        public BlockValue readValueAndMenu(@Nonnull Block block, @Nonnull String key) {
            Object blockData = getLoadedBlockData(block.getLocation());
            if (blockData == null) {
                return new BlockValue(null, null);
            }

            try {
                return new BlockValue(
                        (String) getData.invoke(blockData, key),
                        (BlockMenu) getBlockMenu.invoke(blockData));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not access Slimefun Legacy block data", exception);
            } catch (InvocationTargetException exception) {
                throw rethrow("Slimefun Legacy block data lookup failed", exception);
            }
        }

        @Nullable
        private Object getLoadedBlockData(@Nonnull Location location) {
            try {
                Object databaseManager = getDatabaseManager.invoke(null);
                Object controller = getBlockDataController.invoke(databaseManager);
                Object blockData = getBlockData.invoke(controller, location);
                if (blockData != null && !((Boolean) isDataLoaded.invoke(blockData))) {
                    loadBlockData.invoke(controller, blockData);
                }
                return blockData;
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not access Slimefun Legacy block-data controller", exception);
            } catch (InvocationTargetException exception) {
                throw rethrow("Slimefun Legacy block-data lookup failed", exception);
            }
        }

        @Nonnull
        private static RuntimeException rethrow(@Nonnull String message, @Nonnull InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                return runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            return new IllegalStateException(message, cause);
        }
    }

    /**
     * Intentional compatibility boundary for the upstream RC-37 API floor.
     * Modern Slimefun Legacy never selects this implementation.
     */
    @SuppressWarnings("deprecation")
    private static final class LegacyReader implements Reader {

        @Override
        @Nullable
        public SlimefunItem getSlimefunItem(@Nonnull Block block) {
            return me.mrCookieSlime.Slimefun.api.BlockStorage.check(block);
        }

        @Override
        @Nonnull
        public BlockValue readValueAndMenu(@Nonnull Block block, @Nonnull String key) {
            String value = me.mrCookieSlime.Slimefun.api.BlockStorage
                    .getLocationInfo(block.getLocation())
                    .getString(key);
            BlockMenu menu = me.mrCookieSlime.Slimefun.api.BlockStorage.getInventory(block);
            return new BlockValue(value, menu);
        }
    }
}
