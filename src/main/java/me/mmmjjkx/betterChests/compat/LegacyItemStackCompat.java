package me.mmmjjkx.betterChests.compat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Exact-behavior compatibility helpers for legacy mutable ItemStack callers.
 */
public final class LegacyItemStackCompat {

    private LegacyItemStackCompat() {
    }

    /**
     * Preserves the historical in-place AIR mutation used by old Cargo callers.
     * The operation is deprecated by modern Paper but has no equivalent that
     * preserves the same object identity for legacy integrations.
     */
    @SuppressWarnings("deprecation")
    public static void clearToAir(ItemStack item) {
        item.setType(Material.AIR);
    }
}
