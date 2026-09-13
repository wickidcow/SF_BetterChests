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
     * Reads a drawer without destroying malformed or incomplete persisted data.
     *
     * <p>Recoverable Dev-16 state is still lazily promoted to the v2 schema, but an
     * unresolved legacy drawer is deliberately left untouched. This prevents a read
     * that happens before legacy display entities are available from permanently
     * sealing the drawer as an empty v2 record.</p>
     */
    public static DrawerData read(Block block) {
        Inspection inspection = inspect(block);
        return switch (inspection.status()) {
            case CURRENT_VALID, CURRENT_VERSION_MISSING -> inspection.data();
            case LEGACY_RECOVERABLE -> {
                DrawerData migrated = inspection.data();
                write(block, migrated);
                BetterChests.INSTANCE.getLogger().info(
                        "Migrated a legacy drawer at " + format(block.getLocation())
                                + " with " + migrated.count() + " stored items.");
                yield migrated;
            }
            case CURRENT_CORRUPT, CURRENT_INCONSISTENT, CURRENT_VERSION_UNSUPPORTED -> {
                BetterChests.INSTANCE.getLogger().warning(
                        "Drawer data at " + format(block.getLocation()) + " is "
                                + inspection.status().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                                + "; preserving the raw record for Doctor/manual recovery.");
                yield DrawerData.empty();
            }
            default -> DrawerData.empty();
        };
    }

    /** Performs a read-only classification of the current or Dev-16 drawer state. */
    public static Inspection inspect(Block block) {
        String encoded = BlockStorage.getLocationInfo(block.getLocation(), ITEM_KEY);
        String countText = BlockStorage.getLocationInfo(block.getLocation(), COUNT_KEY);
        String version = BlockStorage.getLocationInfo(block.getLocation(), DATA_VERSION_KEY);

        if (encoded != null || countText != null) {
            return inspectCurrent(encoded, countText, version);
        }

        return inspectLegacy(block);
    }

    /**
     * Promotes Dev-16 data only when both the stored item and exact positive count
     * can be recovered. Partial or unavailable evidence is never overwritten.
     */
    public static boolean migrateLegacyIfRecoverable(Block block) {
        Inspection inspection = inspect(block);
        if (inspection.status() != InspectionStatus.LEGACY_RECOVERABLE || inspection.data().isEmpty()) {
            return false;
        }

        write(block, inspection.data());
        BetterChests.INSTANCE.getLogger().info(
                "Doctor migrated a legacy drawer at " + format(block.getLocation())
                        + " with " + inspection.data().count() + " stored items.");
        return true;
    }

    /** Adds the current schema marker only when the existing item/count pair is already valid. */
    public static boolean markCurrentVersionIfSafe(Block block) {
        Inspection inspection = inspect(block);
        if (inspection.status() != InspectionStatus.CURRENT_VERSION_MISSING) {
            return false;
        }

        BlockStorage.addBlockInfo(block, DATA_VERSION_KEY, DATA_VERSION);
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

    private static Inspection inspectCurrent(@Nullable String encoded, @Nullable String countText, @Nullable String version) {
        if (encoded == null || countText == null) {
            return new Inspection(
                    InspectionStatus.CURRENT_INCONSISTENT,
                    DrawerData.empty(),
                    "Only one v2 drawer storage key is present; raw data was preserved.");
        }

        Long count = parseCountStrict(countText);
        if (count == null || count < 0) {
            return new Inspection(
                    InspectionStatus.CURRENT_INCONSISTENT,
                    DrawerData.empty(),
                    "The v2 drawer count is malformed; raw data was preserved.");
        }

        if (encoded.isBlank() && count == 0L) {
            InspectionStatus status = versionStatus(version, true);
            return new Inspection(status, DrawerData.empty(), detailForVersion(status, true));
        }

        if (encoded.isBlank() || count <= 0L) {
            return new Inspection(
                    InspectionStatus.CURRENT_INCONSISTENT,
                    DrawerData.empty(),
                    "The v2 stored-item and count fields disagree; raw data was preserved.");
        }

        final ItemStack item;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            item = ItemStack.deserializeBytes(bytes);
        } catch (RuntimeException exception) {
            return new Inspection(
                    InspectionStatus.CURRENT_CORRUPT,
                    DrawerData.empty(),
                    "The v2 stored-item payload cannot be decoded; raw data was preserved.");
        }

        if (item == null || item.getType() == Material.AIR) {
            return new Inspection(
                    InspectionStatus.CURRENT_CORRUPT,
                    DrawerData.empty(),
                    "The v2 stored-item payload resolved to an empty item; raw data was preserved.");
        }

        item.setAmount(1);
        DrawerData data = new DrawerData(item, count);
        InspectionStatus status = versionStatus(version, false);
        return new Inspection(status, data, detailForVersion(status, false));
    }

    private static InspectionStatus versionStatus(@Nullable String version, boolean empty) {
        if (version == null || version.isBlank()) {
            return InspectionStatus.CURRENT_VERSION_MISSING;
        }
        if (!DATA_VERSION.equals(version.trim())) {
            return InspectionStatus.CURRENT_VERSION_UNSUPPORTED;
        }
        return empty ? InspectionStatus.CURRENT_EMPTY : InspectionStatus.CURRENT_VALID;
    }

    private static String detailForVersion(InspectionStatus status, boolean empty) {
        return switch (status) {
            case CURRENT_VALID -> "Current v2 drawer data is valid.";
            case CURRENT_EMPTY -> "Current v2 drawer is explicitly empty.";
            case CURRENT_VERSION_MISSING -> empty
                    ? "The empty v2 item/count pair is valid but is missing its schema marker."
                    : "The v2 item/count pair is valid but is missing its schema marker.";
            case CURRENT_VERSION_UNSUPPORTED -> "Drawer data uses an unknown schema marker; it was not modified.";
            default -> "Drawer state requires review.";
        };
    }

    private static Inspection inspectLegacy(Block block) {
        ItemStack item = readLegacyMetadataItem(block);
        long count = readLegacyMetadataCount(block);
        boolean entitiesLoaded = block.getChunk().isEntitiesLoaded();

        if ((item == null || count <= 0) && entitiesLoaded) {
            LegacyDisplayData displayData = readLegacyDisplays(block);
            if (item == null) {
                item = displayData.item();
            }
            if (count <= 0) {
                count = displayData.count();
            }
        }

        if (item != null
                && item.getType() != Material.AIR
                && item.getType() != Material.BARRIER
                && count > 0) {
            item.setAmount(1);
            return new Inspection(
                    InspectionStatus.LEGACY_RECOVERABLE,
                    new DrawerData(item, count),
                    "Dev-16 drawer item and exact count are recoverable and can be promoted safely.");
        }

        if (!entitiesLoaded) {
            return new Inspection(
                    InspectionStatus.LEGACY_WAITING_FOR_ENTITIES,
                    DrawerData.empty(),
                    "No v2 record exists and legacy display entities are not loaded; recovery was deferred.");
        }

        if (item != null || count > 0) {
            return new Inspection(
                    InspectionStatus.LEGACY_PARTIAL,
                    DrawerData.empty(),
                    "Only part of the Dev-16 drawer state is recoverable; automatic migration is blocked.");
        }

        return new Inspection(
                InspectionStatus.LEGACY_NO_EVIDENCE,
                DrawerData.empty(),
                "No v2 record or recoverable Dev-16 item/count evidence was found.");
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

    private static @Nullable Long parseCountStrict(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(value.replace(",", "").trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long parseCount(@Nullable String value) {
        Long parsed = parseCountStrict(value);
        return parsed == null ? 0L : Math.max(0L, parsed);
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

    public enum InspectionStatus {
        CURRENT_VALID,
        CURRENT_EMPTY,
        CURRENT_VERSION_MISSING,
        CURRENT_VERSION_UNSUPPORTED,
        CURRENT_INCONSISTENT,
        CURRENT_CORRUPT,
        LEGACY_RECOVERABLE,
        LEGACY_PARTIAL,
        LEGACY_WAITING_FOR_ENTITIES,
        LEGACY_NO_EVIDENCE
    }

    public record Inspection(InspectionStatus status, DrawerData data, String detail) {
        public Inspection {
            if (status == null) {
                throw new IllegalArgumentException("status cannot be null");
            }
            if (data == null) {
                data = DrawerData.empty();
            }
            if (detail == null || detail.isBlank()) {
                detail = "Drawer state inspected.";
            }
        }
    }

    private record LegacyDisplayData(@Nullable ItemStack item, long count) {
    }
}
