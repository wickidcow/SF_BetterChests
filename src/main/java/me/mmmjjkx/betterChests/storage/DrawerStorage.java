package me.mmmjjkx.betterChests.storage;

import me.mmmjjkx.betterChests.BetterChests;
import me.mmmjjkx.betterChests.utils.MutableItemStacks;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Persistent drawer data layer.
 *
 * <p>Block contents are stored in Slimefun's block database, not Bukkit runtime
 * metadata. Portable drawer items use Bukkit/Paper PDC. The legacy metadata and
 * display-entity migration code exists so Dev-16 drawers can be upgraded in place.</p>
 */
public final class DrawerStorage {

    public static final String ITEM_KEY = "bc_drawer_item_v2";
    public static final String COUNT_KEY = "bc_drawer_count_v2";
    public static final String DATA_VERSION_KEY = "bc_drawer_data_version";
    private static final String DATA_VERSION = "2";

    private static final String LEGACY_ITEM_METADATA = "bc_drawer_item";
    private static final String LEGACY_COUNT_METADATA = "bc_drawer_count";

    private DrawerStorage() {
    }

    /**
     * Reads drawer contents without destroying malformed or not-yet-recoverable legacy evidence.
     * Recoverable Dev-16 state is still upgraded lazily, but unresolved legacy state and corrupt
     * v2 records are left untouched for Doctor/manual recovery.
     */
    public static DrawerData read(Block block) {
        Inspection inspection = inspect(block);
        if (inspection.status() == Status.LEGACY_RECOVERABLE) {
            write(block, inspection.data());
            BetterChests.INSTANCE.getLogger().info(
                    "Migrated a legacy drawer at " + format(block.getLocation()) + " with "
                            + inspection.data().count() + " stored items.");
            return inspection.data();
        }
        if (inspection.status() == Status.CORRUPT_MODERN) {
            BetterChests.INSTANCE.getLogger().warning(
                    "Drawer data at " + format(block.getLocation())
                            + " is inconsistent or unreadable; leaving the stored evidence untouched.");
        }
        return inspection.data();
    }

    /** Performs a read-only classification of one drawer block. */
    public static Inspection inspect(Block block) {
        String encoded = BlockStorage.getLocationInfo(block.getLocation(), ITEM_KEY);
        String countText = BlockStorage.getLocationInfo(block.getLocation(), COUNT_KEY);

        if (encoded == null && countText == null) {
            DrawerData legacy = readLegacyCandidate(block);
            if (!legacy.isEmpty()) {
                return new Inspection(
                        Status.LEGACY_RECOVERABLE,
                        legacy,
                        "Recoverable Dev-16 drawer state was found and can be migrated to v2 storage.");
            }
            return new Inspection(
                    Status.LEGACY_UNRESOLVED,
                    DrawerData.empty(),
                    block.getChunk().isEntitiesLoaded()
                            ? "No recoverable Dev-16 item/count pair is currently available."
                            : "Legacy display entities are not loaded, so recovery cannot be proven yet.");
        }

        if (encoded == null || countText == null) {
            return corrupt("Only one of the v2 item/count keys exists.");
        }

        Long count = parseStoredCount(countText);
        if (count == null) {
            return corrupt("The v2 count is not a valid non-negative number.");
        }

        if (encoded.isBlank() && count == 0L) {
            return new Inspection(Status.MODERN_EMPTY, DrawerData.empty(), "Valid empty v2 drawer state.");
        }
        if (encoded.isBlank() || count <= 0L) {
            return corrupt("The v2 item/count keys disagree about whether the drawer is empty.");
        }

        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            ItemStack item = ItemStack.deserializeBytes(bytes);
            if (item.getType() == Material.AIR) {
                return corrupt("The v2 stored item decodes to AIR.");
            }
            item.setAmount(1);
            return new Inspection(
                    Status.MODERN_VALID,
                    new DrawerData(item, count),
                    "Valid v2 drawer storage.");
        } catch (RuntimeException ex) {
            return corrupt("The v2 stored item payload could not be decoded safely.");
        }
    }

    /** Migrates only positively recovered legacy data. Unknown/corrupt states are never rewritten. */
    public static boolean migrateLegacyIfRecoverable(Block block) {
        Inspection inspection = inspect(block);
        if (inspection.status() != Status.LEGACY_RECOVERABLE) {
            return false;
        }
        write(block, inspection.data());
        BetterChests.INSTANCE.getLogger().info(
                "Doctor migrated a legacy drawer at " + format(block.getLocation()) + " with "
                        + inspection.data().count() + " stored items.");
        return true;
    }

    public static void write(Block block, DrawerData data) {
        if (data == null || data.isEmpty()) {
            BlockStorage.addBlockInfo(block, ITEM_KEY, "");
            BlockStorage.addBlockInfo(block, COUNT_KEY, "0");
            BlockStorage.addBlockInfo(block, DATA_VERSION_KEY, DATA_VERSION);
            return;
        }

        ItemStack item = data.item();
        if (item == null) {
            write(block, DrawerData.empty());
            return;
        }

        item.setAmount(1);
        String encoded = Base64.getEncoder().encodeToString(item.serializeAsBytes());
        BlockStorage.addBlockInfo(block, ITEM_KEY, encoded);
        BlockStorage.addBlockInfo(block, COUNT_KEY, Long.toString(data.count()));
        BlockStorage.addBlockInfo(block, DATA_VERSION_KEY, DATA_VERSION);
    }

    public static void clear(Block block) {
        write(block, DrawerData.empty());
    }

    public static void saveToPortableItem(ItemStack drawerItem, DrawerData data) {
        ItemMeta meta = drawerItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey itemKey = portableItemKey();
        NamespacedKey countKey = portableCountKey();

        pdc.remove(itemKey);
        pdc.remove(countKey);

        if (data != null && !data.isEmpty()) {
            ItemStack stored = data.item();
            if (stored != null) {
                stored.setAmount(1);
                pdc.set(itemKey, PersistentDataType.BYTE_ARRAY, stored.serializeAsBytes());
                pdc.set(countKey, PersistentDataType.LONG, data.count());
            }
        }

        drawerItem.setItemMeta(meta);
    }

    public static DrawerData loadFromPortableItem(ItemStack drawerItem) {
        if (drawerItem == null || !drawerItem.hasItemMeta()) {
            return DrawerData.empty();
        }

        PersistentDataContainer pdc = drawerItem.getItemMeta().getPersistentDataContainer();
        byte[] bytes = pdc.get(portableItemKey(), PersistentDataType.BYTE_ARRAY);
        Long count = pdc.get(portableCountKey(), PersistentDataType.LONG);
        if (bytes == null || count == null || count <= 0) {
            return DrawerData.empty();
        }

        try {
            ItemStack item = ItemStack.deserializeBytes(bytes);
            item.setAmount(1);
            return new DrawerData(item, count);
        } catch (RuntimeException ex) {
            BetterChests.INSTANCE.getLogger().warning("A portable drawer item contained invalid stored-item data.");
            return DrawerData.empty();
        }
    }

    private static DrawerData readLegacyCandidate(Block block) {
        ItemStack item = readLegacyMetadataItem(block);
        long count = readLegacyMetadataCount(block);

        // Runtime metadata is lost on restart. Dev-16 also mirrored the value in
        // persistent display entities, so recover from those when they are already loaded.
        if ((item == null || count <= 0) && block.getChunk().isEntitiesLoaded()) {
            LegacyDisplayData displayData = readLegacyDisplays(block);
            if (item == null) {
                item = displayData.item();
            }
            if (count <= 0) {
                count = displayData.count();
            }
        }

        if (item == null || item.getType() == Material.AIR || item.getType() == Material.BARRIER || count <= 0) {
            return DrawerData.empty();
        }

        item.setAmount(1);
        return new DrawerData(item, count);
    }

    private static @Nullable ItemStack readLegacyMetadataItem(Block block) {
        List<MetadataValue> values = block.getMetadata(LEGACY_ITEM_METADATA);
        for (MetadataValue value : values) {
            Object raw = value.value();
            if (raw instanceof ItemStack stack && stack.getType() != Material.AIR) {
                return MutableItemStacks.copyWithAmount(stack, 1);
            }
        }
        return null;
    }

    private static long readLegacyMetadataCount(Block block) {
        List<MetadataValue> values = block.getMetadata(LEGACY_COUNT_METADATA);
        for (MetadataValue value : values) {
            long count = value.asLong();
            if (count > 0) {
                return count;
            }
        }
        return 0;
    }

    private static LegacyDisplayData readLegacyDisplays(Block block) {
        ItemStack item = null;
        long count = 0;
        Location center = block.getLocation().add(0.5, 0.5, 0.5);

        for (Entity entity : block.getWorld().getNearbyEntities(center, 1.25, 1.25, 1.25)) {
            if (entity instanceof ItemDisplay display) {
                ItemStack candidate = display.getItemStack();
                if (candidate != null
                        && candidate.getType() != Material.AIR
                        && candidate.getType() != Material.BARRIER) {
                    item = MutableItemStacks.copyWithAmount(candidate, 1);
                }
            } else if (entity instanceof TextDisplay display) {
                String text = display.getText();
                long parsed = parseCount(text);
                if (parsed > count) {
                    count = parsed;
                }
            }
        }

        return new LegacyDisplayData(item, count);
    }

    private static @Nullable Long parseStoredCount(String value) {
        if (value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long parseCount(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            return Math.max(0, Long.parseLong(value.replace(",", "").trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Inspection corrupt(String detail) {
        return new Inspection(Status.CORRUPT_MODERN, DrawerData.empty(), detail);
    }

    private static NamespacedKey portableItemKey() {
        return new NamespacedKey(BetterChests.INSTANCE, "drawer_portable_item_v2");
    }

    private static NamespacedKey portableCountKey() {
        return new NamespacedKey(BetterChests.INSTANCE, "drawer_portable_count_v2");
    }

    private static String format(Location location) {
        return String.format(Locale.ROOT, "%s:%d,%d,%d",
                location.getWorld() == null ? "unknown" : location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public enum Status {
        MODERN_VALID,
        MODERN_EMPTY,
        LEGACY_RECOVERABLE,
        LEGACY_UNRESOLVED,
        CORRUPT_MODERN
    }

    public record Inspection(Status status, DrawerData data, String detail) {
    }

    private record LegacyDisplayData(@Nullable ItemStack item, long count) {
    }
}
