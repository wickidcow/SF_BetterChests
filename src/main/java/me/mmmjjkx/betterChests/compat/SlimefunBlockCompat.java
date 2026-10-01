package me.mmmjjkx.betterChests.compat;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.block.Block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Preserves the registered block identity, loaded menu and historical metadata keys.
 * Only reflective method descriptors are cached, never block data or inventory values.
 * Identity is owned by normal block placement; this adapter only writes metadata.
 */
public final class SlimefunBlockCompat {
    private SlimefunBlockCompat() {}

    private static final class RuntimeAccess {
        private static final Access INSTANCE = createAccess(Slimefun.class);
    }

    @Nullable
    public static SlimefunItem getSlimefunItem(@Nonnull Block block) {
        return RuntimeAccess.INSTANCE.getSlimefunItem(block);
    }

    @Nullable
    public static BlockMenu getBlockMenu(@Nonnull Block block) {
        return getBlockMenu(block.getLocation());
    }

    @Nullable
    public static BlockMenu getBlockMenu(@Nonnull Location location) {
        return RuntimeAccess.INSTANCE.getBlockMenu(location);
    }

    @Nullable
    public static String getData(@Nonnull Block block, @Nonnull String key) {
        return getData(block.getLocation(), key);
    }

    @Nullable
    public static String getData(@Nonnull Location location, @Nonnull String key) {
        return RuntimeAccess.INSTANCE.getData(location, key);
    }

    public static void setData(@Nonnull Block block, @Nonnull String key, @Nullable String value) {
        setData(block.getLocation(), key, value);
    }

    /** Refuses missing/unreadable blocks instead of accepting an unpersisted write. */
    public static void setData(@Nonnull Location location, @Nonnull String key, @Nullable String value) {
        RuntimeAccess.INSTANCE.setData(location, key, value);
    }

    // Package-private resolution seam: tests exercise the actual reflective adapter.
    static Access createAccess(Class<?> api) {
        final Method database;
        try {
            database = api.getMethod("getDatabaseManager");
        } catch (NoSuchMethodException absent) {
            return new LegacyAccess();
        }
        try {
            return new ModernAccess(database);
        } catch (NoSuchMethodException incompatible) {
            throw new IllegalStateException("Incomplete Slimefun block-data API; refusing unsafe fallback", incompatible);
        }
    }

    interface Access {
        SlimefunItem getSlimefunItem(Block block);
        BlockMenu getBlockMenu(Location location);
        String getData(Location location, String key);
        void setData(Location location, String key, String value);
    }

    private static final class ModernAccess implements Access {
        private final Method database, controller, blockData, load, loaded, id, get, set, remove, menu;

        private ModernAccess(Method database) throws NoSuchMethodException {
            this.database = database;
            controller = database.getReturnType().getMethod("getBlockDataController");
            Class<?> controllerType = controller.getReturnType();
            blockData = controllerType.getMethod("getBlockData", Location.class);
            Class<?> dataType = blockData.getReturnType();
            load = controllerType.getMethod("loadBlockData", dataType);
            loaded = dataType.getMethod("isDataLoaded");
            id = dataType.getMethod("getSfId");
            get = dataType.getMethod("getData", String.class);
            set = dataType.getMethod("setData", String.class, String.class);
            remove = dataType.getMethod("removeData", String.class);
            menu = dataType.getMethod("getBlockMenu");
        }

        private Object getLoadedData(Location location) {
            Objects.requireNonNull(location, "location");
            Object currentController = call(controller, call(database, null));
            Object data = call(blockData, currentController, location);
            if (data != null && !Boolean.TRUE.equals(call(loaded, data))) {
                call(load, currentController, data);
                if (!Boolean.TRUE.equals(call(loaded, data))) {
                    throw new IllegalStateException("Slimefun block data remains unreadable at " + location);
                }
            }
            return data;
        }

        @Override
        public SlimefunItem getSlimefunItem(Block block) {
            Object data = getLoadedData(block.getLocation());
            String key = data == null ? null : (String) call(id, data);
            return key == null ? null : SlimefunItem.getById(key);
        }

        @Override
        public BlockMenu getBlockMenu(Location location) {
            Object data = getLoadedData(location);
            return data == null ? null : (BlockMenu) call(menu, data);
        }

        @Override
        public String getData(Location location, String key) {
            Objects.requireNonNull(key, "key");
            Object data = getLoadedData(location);
            if (data == null) return null;
            return (String) ("id".equals(key) ? call(id, data) : call(get, data, key));
        }

        @Override
        public void setData(Location location, String key, String value) {
            requireMetadataKey(key);
            Object data = getLoadedData(location);
            if (data == null) {
                throw new IllegalStateException("No registered Slimefun block data at " + location);
            }
            if (value == null) call(remove, data, key);
            else call(set, data, key, value);
        }
    }

    private static void requireMetadataKey(String key) {
        Objects.requireNonNull(key, "key");
        if ("id".equals(key)) {
            throw new IllegalArgumentException("Block identity must be changed through normal Slimefun placement");
        }
    }

    private static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException inaccessible) {
            throw new IllegalStateException("Cannot access Slimefun block-data API", inaccessible);
        } catch (InvocationTargetException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Slimefun block-data operation failed", cause);
        }
    }

    /** The original RC-37 ABI is deliberately retained only at this boundary. */
    @SuppressWarnings("deprecation")
    private static final class LegacyAccess implements Access {
        @Override
        public SlimefunItem getSlimefunItem(Block block) {
            return me.mrCookieSlime.Slimefun.api.BlockStorage.check(block);
        }
        @Override
        public BlockMenu getBlockMenu(Location location) {
            return me.mrCookieSlime.Slimefun.api.BlockStorage.getInventory(location);
        }
        @Override
        public String getData(Location location, String key) {
            return me.mrCookieSlime.Slimefun.api.BlockStorage.getLocationInfo(location, key);
        }
        @Override
        public void setData(Location location, String key, String value) {
            requireMetadataKey(key);
            if (!me.mrCookieSlime.Slimefun.api.BlockStorage.hasBlockInfo(location)) {
                throw new IllegalStateException("No registered Slimefun block data at " + location);
            }
            me.mrCookieSlime.Slimefun.api.BlockStorage.addBlockInfo(location, key, value);
        }
    }
}
