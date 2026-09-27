package me.mmmjjkx.betterChests.compat;

/**
 * Compatibility boundary for Slimefun's legacy cargo inventory contract.
 *
 * <p>Slimefun still consumes InventoryBlock for classic Cargo interoperability,
 * but the interface is deprecated because the inventory system is intended to
 * be replaced. BetterChests must keep implementing that ABI until a supported
 * replacement exists, so the deprecated type is isolated here rather than
 * suppressed across the actual drawer implementations.</p>
 */
@SuppressWarnings("deprecation")
public interface LegacyInventoryBlock
        extends me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock {
}
