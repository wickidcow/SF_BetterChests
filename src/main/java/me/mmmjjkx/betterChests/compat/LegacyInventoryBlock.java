package me.mmmjjkx.betterChests.compat;

/**
 * Compatibility bridge for Slimefun's legacy inventory/cargo contract.
 *
 * <p>Slimefun currently deprecates InventoryBlock for addons but does not yet
 * provide a replacement contract. Extending it here preserves cargo and menu
 * type checks without suppressing deprecation warnings across whole machine
 * classes.</p>
 */
@SuppressWarnings("deprecation")
public interface LegacyInventoryBlock
        extends me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock {
}
