package me.mmmjjkx.betterChests.compat;

import java.io.ByteArrayInputStream;

import javax.annotation.Nullable;

import org.bukkit.inventory.ItemStack;

/**
 * Exact legacy decoder for portable IE storage items written by Dev-16.
 *
 * <p>The deprecated Bukkit object stream is intentionally retained only here so
 * existing portable items keep their historical byte compatibility. New items
 * continue to use ItemStack#serializeAsBytes.</p>
 */
public final class LegacyItemStackSerialization {

    private LegacyItemStackSerialization() {
    }

    @Nullable
    @SuppressWarnings("deprecation")
    public static ItemStack deserialize(byte[] data) {
        try (var input =
                new org.bukkit.util.io.BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            Object value = input.readObject();
            return value instanceof ItemStack item ? item : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
