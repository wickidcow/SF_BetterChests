package me.mmmjjkx.betterChests.items.cargo;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import me.mmmjjkx.betterChests.BCGroups;
import me.mmmjjkx.betterChests.compat.LegacyInventoryBlock;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * Compatibility shell for the unfinished Dev-16 point-to-point transfer.
 *
 * <p>Dev-16 constructed this item during {@code BCItems} class initialization,
 * even though it never registered the item with Slimefun. Its ticker contained
 * destructive source-slot and destination-location mistakes. Keeping this safe,
 * inert class preserves binary compatibility without enabling the broken machine.
 * It can be redesigned and registered in a later release after dedicated tests.</p>
 */
public final class P2PTransfer extends SlimefunItem implements LegacyInventoryBlock {

    public P2PTransfer(SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(BCGroups.CARGO, item, recipeType, recipe);
    }

    @Override
    public int[] getInputSlots() {
        return new int[0];
    }

    @Override
    public int[] getOutputSlots() {
        return new int[0];
    }

    @Override
    public Collection<ItemStack> getDrops() {
        return List.of(getItem().clone());
    }
}
