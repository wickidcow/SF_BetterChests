package me.mmmjjkx.betterChests.diagnostics;

import me.mmmjjkx.betterChests.BetterChests;
import me.mmmjjkx.betterChests.items.chests.ie.IEStorageUnit;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Read-only integrity classification for BetterChests' historically stable IE-style storage schema. */
final class IEStorageDoctorInspector {

    private static final String STORED_AMOUNT = "stored";
    private static final int DISPLAY_SLOT = 13;
    private static final int OUTPUT_SLOT = 16;

    private IEStorageDoctorInspector() {
    }

    static Result inspect(Block block, IEStorageUnit unit) {
        Config config = BlockStorage.getLocationInfo(block.getLocation());
        String rawAmount = config == null ? null : config.getString(STORED_AMOUNT);

        final long amount;
        if (rawAmount == null || rawAmount.isBlank()) {
            amount = 0L;
        } else {
            try {
                amount = Long.parseLong(rawAmount.trim());
            } catch (NumberFormatException exception) {
                return new Result(
                        Status.MALFORMED_COUNT,
                        "IE storage count is malformed; the raw BlockStorage value was left unchanged.");
            }
        }

        if (amount < 0L) {
            return new Result(
                    Status.NEGATIVE_COUNT,
                    "IE storage count is negative; Doctor did not normalize or overwrite it.");
        }
        if (amount > unit.getCapacity()) {
            return new Result(
                    Status.OVER_CAPACITY,
                    "IE storage count exceeds this unit's capacity; Doctor did not clamp or overwrite it.");
        }

        BlockMenu menu = BlockStorage.getInventory(block);
        if (amount == 0L) {
            if (menu == null) {
                return new Result(Status.HEALTHY_EMPTY, "IE storage is empty; its block menu is not currently available.");
            }
            ItemStack display = menu.getItemInSlot(DISPLAY_SLOT);
            if (isRealItem(display)) {
                return new Result(
                        Status.ZERO_COUNT_WITH_DISPLAY,
                        "IE storage count is zero but the display slot still identifies an item; no state was changed.");
            }
            return new Result(Status.HEALTHY_EMPTY, "IE storage is empty.");
        }

        if (menu == null) {
            return new Result(
                    Status.MENU_UNAVAILABLE,
                    "IE storage has a positive count but its loaded block menu is unavailable; inspection was deferred.");
        }

        ItemStack display = menu.getItemInSlot(DISPLAY_SLOT);
        if (isRealItem(display)) {
            return new Result(Status.HEALTHY, "IE storage count and display-item identity are present.");
        }

        ItemStack output = menu.getItemInSlot(OUTPUT_SLOT);
        if (isRealItem(output)) {
            return new Result(
                    Status.MISSING_DISPLAY_WITH_OUTPUT_EVIDENCE,
                    "IE storage has a positive count and no display identity, but the output slot contains item evidence; manual recovery is required.");
        }

        return new Result(
                Status.MISSING_DISPLAY,
                "IE storage has a positive count but no recoverable display-item identity; no state was changed.");
    }

    private static boolean isRealItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(emptyKey(), PersistentDataType.BYTE)) {
            return false;
        }
        return true;
    }

    private static NamespacedKey emptyKey() {
        return new NamespacedKey(BetterChests.INSTANCE, "empty");
    }

    enum Status {
        HEALTHY,
        HEALTHY_EMPTY,
        MALFORMED_COUNT,
        NEGATIVE_COUNT,
        OVER_CAPACITY,
        MENU_UNAVAILABLE,
        MISSING_DISPLAY,
        MISSING_DISPLAY_WITH_OUTPUT_EVIDENCE,
        ZERO_COUNT_WITH_DISPLAY
    }

    record Result(Status status, String detail) {
        boolean healthy() {
            return status == Status.HEALTHY || status == Status.HEALTHY_EMPTY;
        }
    }
}
