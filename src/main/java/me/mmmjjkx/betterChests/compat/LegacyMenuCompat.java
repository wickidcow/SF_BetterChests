package me.mmmjjkx.betterChests.compat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

/**
 * Compatibility boundary for Slimefun's retained legacy ChestMenu click ABI.
 */
public final class LegacyMenuCompat {

    private LegacyMenuCompat() {
    }

    @FunctionalInterface
    public interface ClickHandler {
        boolean onClick(
                @Nonnull Player player,
                int slot,
                @Nullable ItemStack item,
                @Nonnull ClickActionView action);
    }

    public record ClickActionView(boolean rightClicked, boolean shiftClicked) {
        public boolean isRightClicked() {
            return rightClicked;
        }

        public boolean isShiftClicked() {
            return shiftClicked;
        }
    }

    @SuppressWarnings("deprecation")
    public static void addClickHandler(
            @Nonnull BlockMenu menu,
            int slot,
            @Nonnull ClickHandler handler) {
        menu.addMenuClickHandler(
                slot,
                (player, clickedSlot, item, action) -> handler.onClick(
                        player,
                        clickedSlot,
                        item,
                        new ClickActionView(action.isRightClicked(), action.isShiftClicked())));
    }
}
