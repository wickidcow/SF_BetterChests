package me.mmmjjkx.betterChests.compat;

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
 * Compatibility access for Slimefun block identity, menus, and persisted key/value data.
 *
 * <p>Modern Slimefun Legacy uses BlockDataController/SlimefunBlockData. The RC-37
 * compatibility floor predates that API, so only those older runtimes use the
 * isolated BlockStorage fallback. Both paths preserve the same existing block
 * keys, menus, and Slimefun item ids.</p>
 */
public final class SlimefunBlockCompat {

    private static final Access ACCESS = createAccess();

    private SlimefunBlockCompat() {
    }

    @Nullable
    public static SlimefunItem getSlimefunItem(@Nonnull Block block) {
        return ACCESS.getSlimefunItem(block);
    }

    @Nullable
    public static BlockMenu getBlockMenu(@Nonnull Block block) {
        return getBlockMenu(block.getLocation());
    }

    @Nullable
    public static BlockMenu getBlockMenu(@Nonnull Location location) {
        return ACCESS.getBlockMenu(location);
    }

    @Nullable
    public static String getData(@Nonnull Block block, @Nonnull String key) {
        return getData(block.getLocation(), key);
    }

    @Nullable
    public static String getData(@Nonnull Location location, @Nonnull String key) {
        return ACCESS.getData(location, key);
    }

    public static void setData(@Nonnull Block block, @Nonnull String key, @Nullable String value) {
        setData(block.getLocation(), key, value);
    }

    public static void setData(@Nonnull Location location, @Nonnull String key, @Nullable String value) {
        ACCESS.setData(location, key, value);
    }

    @Nonnull
    private static Access createAccess() {
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
            Method setData = blockDataType.getMethod("setData", String.class, String.class);
            Method removeData = blockDataType.getMethod("removeData", String.class);
            Method getBlockMenu = blockDataType.getMethod("getBlockMenu");

            return new ModernAccess(
                    getDatabaseManager,
                    getBlockDataController,
                    getBlockData,
                    loadBlockData,
                    isDataLoaded,
                    getSfId,
                    getData,
                    setData,
                    removeData,
                    getBlockMenu);
        } catch (NoSuchMethodException | LinkageError ignored) {
            return new LegacyAccess();
        }
    }

    private interface Access {
        @Nullable
        SlimefunItem getSlimefunItem(@Nonnull Block block);

        @Nullable
        BlockMenu getBlockMenu(@Nonnull Location location);

        @Nullable
        String getData(@Nonnull Location location, @Nonnull String key);

        void setData(@Nonnull Location location, @Nonnull String key, @Nullable String value);
    }

    private static final class ModernAccess implements Access {

        private final Method getDatabaseManager;
        private final Method getBlockDataController;
        private final Method getBlockData;
        private final Method loadBlockData;
        private final Method isDataLoaded;
        private final Method getSfId;
        private final Method getData;
        private final Method setData;
        private final Method removeData;
        private final Method getBlockMenu;

        private ModernAccess(
                Method getDatabaseManager,
                Method getBlockDataController,
                Method getBlockData,
                Method loadBlockData,
                Method isDataLoaded,
                Method getSfId,
                Method getData,
                Method setData,
                Method removeData,
                Method getBlockMenu) {
            this.getDatabaseManager = getDatabaseManager;
            this.getBlockDataController = getBlockDataController;
            this.getBlockData = getBlockData;
            this.loadBlockData = loadBlockData;
            this.isDataLoaded = isDataLoaded;
            this.getSfId = getSfId;
            this.getData = getData;
            this.setData = setData;
            this.removeData = removeData;
            this.getBlockMenu = getBlockMenu;
        }

        @Override
        @Nullable
        public SlimefunItem getSlimefunItem(@Nonnull Block block) {
            Object data = getLoadedData(block.getLocation());
            if (data == null) {
                return null;
            }
            try {
                return SlimefunItem.getById((String) getSfId.invoke(data));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not access Slimefun Legacy block id", exception);
            } catch (InvocationTargetException exception) {
                throw rethrow("Slimefun Legacy block id lookup failed", exception);
            }
        }

        @Override
        @Nullable
        public BlockMenu getBlockMenu(@Nonnull Location location) {
            Object data = getLoadedData(location);
            if (data == null) {
                return null;
            }
            try {
                return (BlockMenu) getBlockMenu.invoke(data);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not access Slimefun Legacy block menu", exception);
            } catch (InvocationTargetException exception) {
                throw rethrow("Slimefun Legacy block menu lookup failed", exception);
            }
        }

        @Override
        @Nullable
        public String getData(@Nonnull Location location, @Nonnull String key) {
            Object data = getLoadedData(location);
            if (data == null) {
                return null;
            }
            try {
                return (String) getData.invoke(data, key);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not access Slimefun Legacy block data", exception);
            } catch (InvocationTargetException exception) {
                throw rethrow("Slimefun Legacy block data lookup failed", exception);
            }
        }

        @Override
        public void setData(@Nonnull Location location, @Nonnull String key, @Nullable String value) {
            Object data = getLoadedData(location);
            if (data == null) {
                return;
            }
            try {
                if (value == null) {
                    removeData.invoke(data, key);
                } else {
                    setData.invoke(data, key, value);
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not update Slimefun Legacy block data", exception);
            } catch (InvocationTargetException exception) {
                throw rethrow("Slimefun Legacy block data update failed", exception);
            }
        }

        @Nullable
        private Object getLoadedData(@Nonnull Location location) {
            try {
                Object databaseManager = getDatabaseManager.invoke(null);
                Object controller = getBlockDataController.invoke(databaseManager);
                Object data = getBlockData.invoke(controller, location);
                if (data != null && !((Boolean) isDataLoaded.invoke(data))) {
                    loadBlockData.invoke(controller, data);
                }
                return data;
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not access Slimefun Legacy block-data controller", exception);
            } catch (InvocationTargetException exception) {
                throw rethrow("Slimefun Legacy block-data lookup failed", exception);
            }
        }
    }

    /**
     * Intentional compatibility boundary for the upstream RC-37 API floor.
     * Modern Slimefun Legacy never selects this implementation.
     */
    @SuppressWarnings("deprecation")
    private static final class LegacyAccess implements Access {

        @Override
        @Nullable
        public SlimefunItem getSlimefunItem(@Nonnull Block block) {
            return me.mrCookieSlime.Slimefun.api.BlockStorage.check(block);
        }

        @Override
        @Nullable
        public BlockMenu getBlockMenu(@Nonnull Location location) {
            return me.mrCookieSlime.Slimefun.api.BlockStorage.getInventory(location);
        }

        @Override
        @Nullable
        public String getData(@Nonnull Location location, @Nonnull String key) {
            return me.mrCookieSlime.Slimefun.api.BlockStorage.getLocationInfo(location, key);
        }

        @Override
        public void setData(@Nonnull Location location, @Nonnull String key, @Nullable String value) {
            me.mrCookieSlime.Slimefun.api.BlockStorage.addBlockInfo(location, key, value);
        }
    }

    @Nonnull
    private static RuntimeException rethrow(
            @Nonnull String message,
            @Nonnull InvocationTargetException exception) {
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
