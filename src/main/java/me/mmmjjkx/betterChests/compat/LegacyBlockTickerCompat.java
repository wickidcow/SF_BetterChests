package me.mmmjjkx.betterChests.compat;

import org.bukkit.block.Block;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;

import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;

/**
 * RC-37-compatible ticker bridge that keeps the deprecated CSCoreLib Config
 * signature out of BetterChests machine implementations.
 *
 * <p>Modern Slimefun Legacy dispatches its SlimefunBlockData ticker overload
 * through the retained legacy bridge, while RC-37 calls this method directly.</p>
 */
@SuppressWarnings("deprecation")
public abstract class LegacyBlockTickerCompat extends BlockTicker {

    @Override
    public final void tick(
            Block block,
            SlimefunItem item,
            me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config data) {
        tickCompat(block, item);
    }

    protected abstract void tickCompat(Block block, SlimefunItem item);
}
