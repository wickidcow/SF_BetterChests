package me.mmmjjkx.betterChests.items.chests;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotHopperable;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.cargo.CargoNode;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import it.unimi.dsi.fastutil.Pair;
import me.mmmjjkx.betterChests.BCGroups;
import me.mmmjjkx.betterChests.BetterChests;
import me.mmmjjkx.betterChests.storage.DrawerData;
import me.mmmjjkx.betterChests.storage.DrawerDisplayManager;
import me.mmmjjkx.betterChests.storage.DrawerStorage;
import me.mmmjjkx.betterChests.compat.LegacyInventoryBlock;
import me.mmmjjkx.betterChests.compat.SlimefunBlockCompat;
import me.mmmjjkx.betterChests.utils.MutableItemStacks;
import me.mmmjjkx.betterChests.compat.LegacyItemStackCompat;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A single-item bulk drawer.
 *
 * <p>This implementation is restart-safe, uses long arithmetic throughout,
 * stores portable contents in PDC, and never drops an invalid multi-million
 * item stack when the block is broken.</p>
 */
public class SimpleDrawer extends SlimefunItem implements NotHopperable, LegacyInventoryBlock {

    private static final int CARGO_INPUT_SLOT = 0;
    private static final int CARGO_OUTPUT_SLOT = 1;
    private static final int[] NO_CARGO_SLOTS = new int[0];

    private static final Map<String, Object> LOCKS = new ConcurrentHashMap<>();
    private static final Set<String> CARGO_SYNC = ConcurrentHashMap.newKeySet();
    private static final Set<String> CARGO_REFRESH_PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<String, Integer> CARGO_OFFERED = new ConcurrentHashMap<>();

    private final long capacity;
    private final ThreadLocal<ItemStack> pendingBreakDrop = new ThreadLocal<>();

    public SimpleDrawer(SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe, long capacity) {
        super(BCGroups.STORAGES, item, recipeType, recipe);
        if (capacity < 1) {
            throw new IllegalArgumentException("Drawer capacity must be positive");
        }
        this.capacity = capacity;

        registerCargoMenu();

        addItemHandler(new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@NotNull BlockPlaceEvent event) {
                Block block = event.getBlockPlaced();
                DrawerData portable = DrawerStorage.loadFromPortableItem(event.getItemInHand());
                DrawerData restored = clamp(portable);
                DrawerStorage.write(block, restored);

                // Slimefun and Paper finish their placement bookkeeping after this
                // handler. Rebuild the display on the following server tick.
                Bukkit.getScheduler().runTask(BetterChests.INSTANCE, () -> {
                    if (block.getType() != Material.AIR) {
                        DrawerDisplayManager.update(block, DrawerStorage.read(block));
                    }
                });
            }
        });

        addItemHandler((BlockUseHandler) event -> {
            Player player = event.getPlayer();
            ItemStack held = player.getInventory().getItemInMainHand();

            // Cargo nodes are placed by right-clicking the side of the target block.
            // Do not cancel or treat the node itself as an item deposit.
            if (isCargoNode(held)) {
                return;
            }

            event.cancel();

            Optional<Block> clickedBlock = event.getClickedBlock();
            if (clickedBlock.isEmpty()) {
                return;
            }

            Block block = clickedBlock.get();
            if (!Slimefun.getProtectionManager().hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK)) {
                player.sendMessage("§cYou do not have permission to interact with this block.");
                return;
            }

            synchronized (lock(block.getLocation())) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR || hand.getAmount() < 1) {
                    withdrawToPlayer(block, player);
                } else {
                    depositFromPlayer(block, player, hand);
                }
            }
        });

        addItemHandler(new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(
                    @NotNull BlockBreakEvent event,
                    @NotNull ItemStack tool,
                    @NotNull List<ItemStack> drops
            ) {
                Block block = event.getBlock();
                synchronized (lock(block.getLocation())) {
                    DrawerData data = DrawerStorage.read(block);

                    // Slimefun appends getDrops() after this handler returns. Stage
                    // exactly one block-specific portable drop for that call instead
                    // of adding a drop here and receiving a second default drawer.
                    drops.removeIf(SimpleDrawer.this::isItem);
                    ItemStack portableDrop = MutableItemStacks.copy(getItem());
                    portableDrop.setAmount(1);
                    DrawerStorage.saveToPortableItem(portableDrop, data);
                    pendingBreakDrop.set(portableDrop);

                    // The output slot is only a mirror of DrawerStorage and must never drop.
                    // Preserve only an exceptional unprocessed input buffer, if one exists.
                    BlockMenu cargoMenu = SlimefunBlockCompat.getBlockMenu(block);
                    if (cargoMenu != null) {
                        ItemStack buffered = cargoMenu.getItemInSlot(CARGO_INPUT_SLOT);
                        if (buffered != null && !buffered.getType().isAir() && buffered.getAmount() > 0) {
                            drops.add(MutableItemStacks.copy(buffered));
                        }
                        clearCargoMenu(block, cargoMenu);
                    }

                    DrawerDisplayManager.remove(block);
                    String key = locationKey(block.getLocation());
                    LOCKS.remove(key);
                    CARGO_SYNC.remove(key);
                    CARGO_REFRESH_PENDING.remove(key);
                    CARGO_OFFERED.remove(key);
                }
            }
        });
    }

    private void registerCargoMenu() {
        new BlockMenuPreset(getId(), getItemName()) {
            @Override
            public void init() {
                setSize(9);
                drawBackground(ChestMenuUtils.getBackground(), new int[]{2, 3, 4, 5, 6, 7, 8});
            }

            @Override
            public boolean canOpen(@NotNull Block block, @NotNull Player player) {
                // Drawers intentionally use direct right-click interaction, not a GUI.
                return false;
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return flow == ItemTransportFlow.WITHDRAW
                        ? new int[]{CARGO_OUTPUT_SLOT}
                        : new int[]{CARGO_INPUT_SLOT};
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(
                    DirtyChestMenu menu,
                    ItemTransportFlow flow,
                    ItemStack item
            ) {
                if (!(menu instanceof BlockMenu blockMenu)) {
                    return NO_CARGO_SLOTS;
                }

                Block block = blockMenu.getLocation().getBlock();

                // Core Slimefun provides null for withdrawal and a candidate stack
                // for insertion. This also tolerates forks that reverse the flow enum.
                if (item == null) {
                    syncCargoOutput(block, menu);
                    DrawerData data = DrawerStorage.read(block);
                    return data.isEmpty()
                            ? NO_CARGO_SLOTS
                            : new int[]{CARGO_OUTPUT_SLOT};
                }

                flushCargoInput(block, menu);
                if (menu.getItemInSlot(CARGO_INPUT_SLOT) != null) {
                    return NO_CARGO_SLOTS;
                }

                return canAcceptWholeCargoStack(block, item)
                        ? new int[]{CARGO_INPUT_SLOT}
                        : NO_CARGO_SLOTS;
            }

            @Override
            protected ItemStack onItemStackChange(
                    @NotNull DirtyChestMenu menu,
                    int slot,
                    @Nullable ItemStack previous,
                    @Nullable ItemStack next
            ) {
                if (!(menu instanceof BlockMenu blockMenu)) {
                    return next;
                }

                Block block = blockMenu.getLocation().getBlock();
                String key = locationKey(block.getLocation());
                if (CARGO_SYNC.contains(key)) {
                    return next;
                }

                if (slot == CARGO_INPUT_SLOT) {
                    return acceptCargoInput(block, menu, next);
                }

                if (slot == CARGO_OUTPUT_SLOT) {
                    return handleCargoOutputChange(block, previous, next);
                }

                return next;
            }

            @Override
            public void newInstance(@NotNull BlockMenu menu, @NotNull Location location) {
                Block block = location.getBlock();
                flushCargoInput(block, menu);
                syncCargoOutput(block, menu);
            }
        };
    }

    private boolean canAcceptWholeCargoStack(Block block, ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() < 1) {
            return false;
        }

        synchronized (lock(block.getLocation())) {
            DrawerData data = DrawerStorage.read(block);
            ItemStack stored = data.item();

            // Slimefun deliberately passes an immutable ItemStackWrapper here.
            // Compare it directly using Slimefun's wrapper-aware comparator;
            // do not clone or reconstruct it during the cargo slot probe.
            boolean compatible = stored == null
                    || SlimefunUtils.isItemSimilar(stored, item, true, false);
            return compatible && capacity - data.count() >= item.getAmount();
        }
    }

    private ItemStack acceptCargoInput(Block block, DirtyChestMenu menu, ItemStack incoming) {
        if (incoming == null || incoming.getType().isAir() || incoming.getAmount() < 1) {
            return null;
        }

        ItemStack remainder;
        synchronized (lock(block.getLocation())) {
            DrawerData data = DrawerStorage.read(block);
            ItemStack stored = data.item();
            ItemStack candidate = one(incoming);

            if (stored != null && !stored.isSimilar(candidate)) {
                return MutableItemStacks.copy(incoming);
            }

            long free = capacity - data.count();
            int accepted = (int) Math.min(Math.max(0L, free), incoming.getAmount());
            if (accepted <= 0) {
                return MutableItemStacks.copy(incoming);
            }

            DrawerData updated = new DrawerData(
                    stored == null ? candidate : stored,
                    data.count() + accepted
            );
            DrawerStorage.write(block, updated);
            DrawerDisplayManager.update(block, updated);

            int remaining = incoming.getAmount() - accepted;
            if (remaining > 0) {
                remainder = MutableItemStacks.copy(incoming);
                remainder.setAmount(remaining);
            } else {
                remainder = null;
            }
        }

        syncCargoOutput(block, menu);
        return remainder;
    }

    /**
     * Handles changes to the drawer's virtual cargo output slot.
     *
     * <p>The slot must remain empty after a full withdrawal. Slimefun withdraws
     * an item first and only then attempts to deliver it. If delivery fails, it
     * restores the remainder to the original slot. Refilling the mirror inside
     * this callback prevents that rollback and can cause the item to be deleted
     * when cargo overflow deletion is enabled.</p>
     */
    private ItemStack handleCargoOutputChange(
            Block block,
            @Nullable ItemStack previous,
            @Nullable ItemStack next
    ) {
        if (isEmptyStack(previous)) {
            if (isEmptyStack(next)) {
                return null;
            }

            // Cargo could not deliver all or part of a withdrawn stack and is
            // returning it to the original slot. Absorb it back into persistent
            // drawer storage and keep the virtual slot empty.
            ItemStack remainder = restoreCargoRollback(block, next);
            scheduleCargoOutputSync(block);
            return remainder;
        }

        String key = locationKey(block.getLocation());
        int fallbackOffer = previous.getAmount();
        int offered = CARGO_OFFERED.getOrDefault(key, fallbackOffer);
        int remainingInSlot = isEmptyStack(next) ? 0 : next.getAmount();
        int removed = Math.max(0, offered - remainingInSlot);

        synchronized (lock(block.getLocation())) {
            DrawerData data = DrawerStorage.read(block);
            if (removed > 0 && !data.isEmpty()) {
                long actual = Math.min(data.count(), removed);
                DrawerData updated = new DrawerData(data.item(), data.count() - actual);
                DrawerStorage.write(block, updated);
                DrawerDisplayManager.update(block, updated);
            }
        }

        // Preserve the real post-withdrawal slot state. In particular, a full
        // withdrawal must leave null here so CargoNetworkTask can roll an
        // undelivered stack back into this exact slot.
        CARGO_OFFERED.put(key, remainingInSlot);
        scheduleCargoOutputSync(block);
        return next;
    }

    /**
     * Restores an undelivered cargo remainder to persistent drawer storage.
     * Returns only an amount that could not be restored, which should normally
     * be null because the exact amount was just withdrawn from this drawer.
     */
    private ItemStack restoreCargoRollback(Block block, ItemStack returned) {
        synchronized (lock(block.getLocation())) {
            DrawerData data = DrawerStorage.read(block);
            ItemStack stored = data.item();
            ItemStack candidate = one(returned);

            if (stored != null && !stored.isSimilar(candidate)) {
                return MutableItemStacks.copy(returned);
            }

            long free = Math.max(0L, capacity - data.count());
            int restored = (int) Math.min(free, returned.getAmount());
            if (restored <= 0) {
                return MutableItemStacks.copy(returned);
            }

            DrawerData updated = new DrawerData(
                    stored == null ? candidate : stored,
                    data.count() + restored
            );
            DrawerStorage.write(block, updated);
            DrawerDisplayManager.update(block, updated);

            int remaining = returned.getAmount() - restored;
            if (remaining <= 0) {
                return null;
            }

            ItemStack remainder = MutableItemStacks.copy(returned);
            remainder.setAmount(remaining);
            return remainder;
        }
    }

    private void scheduleCargoOutputSync(Block block) {
        String key = locationKey(block.getLocation());
        if (!CARGO_REFRESH_PENDING.add(key)) {
            return;
        }

        Bukkit.getScheduler().runTask(BetterChests.INSTANCE, () -> {
            CARGO_REFRESH_PENDING.remove(key);

            if (!(SlimefunBlockCompat.getSlimefunItem(block) instanceof SimpleDrawer)) {
                CARGO_OFFERED.remove(key);
                return;
            }

            BlockMenu currentMenu = SlimefunBlockCompat.getBlockMenu(block);
            if (currentMenu != null) {
                syncCargoOutput(block, currentMenu);
            }
        });
    }

    private static boolean isEmptyStack(@Nullable ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() < 1;
    }

    private void flushCargoInput(Block block, DirtyChestMenu menu) {
        ItemStack buffered = menu.getItemInSlot(CARGO_INPUT_SLOT);
        if (buffered == null || buffered.getType().isAir() || buffered.getAmount() < 1) {
            return;
        }

        ItemStack remainder = acceptCargoInput(block, menu, MutableItemStacks.copy(buffered));
        replaceCargoSlot(block, menu, CARGO_INPUT_SLOT, remainder);
    }

    private void syncCargoOutput(Block block, DirtyChestMenu menu) {
        DrawerData data = DrawerStorage.read(block);
        ItemStack output = createCargoOutput(data);
        String key = locationKey(block.getLocation());
        CARGO_OFFERED.put(key, output == null ? 0 : output.getAmount());
        replaceCargoSlot(block, menu, CARGO_OUTPUT_SLOT, output);
    }

    private static ItemStack createCargoOutput(DrawerData data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        ItemStack output = MutableItemStacks.copy(data.item());
        output.setAmount((int) Math.min(data.count(), output.getMaxStackSize()));
        return output;
    }

    private static void replaceCargoSlot(
            Block block,
            DirtyChestMenu menu,
            int slot,
            ItemStack item
    ) {
        String key = locationKey(block.getLocation());
        CARGO_SYNC.add(key);
        try {
            menu.replaceExistingItem(slot, item);
        } finally {
            CARGO_SYNC.remove(key);
        }
    }

    private static void clearCargoMenu(Block block, DirtyChestMenu menu) {
        String key = locationKey(block.getLocation());
        CARGO_SYNC.add(key);
        try {
            menu.replaceExistingItem(CARGO_INPUT_SLOT, null);
            menu.replaceExistingItem(CARGO_OUTPUT_SLOT, null);
        } finally {
            CARGO_SYNC.remove(key);
        }
    }

    private static boolean isCargoNode(ItemStack item) {
        return item != null
                && !item.getType().isAir()
                && SlimefunItem.getByItem(item) instanceof CargoNode;
    }

    @Override
    public int[] getInputSlots() {
        return new int[]{CARGO_INPUT_SLOT};
    }

    @Override
    public int[] getOutputSlots() {
        return new int[]{CARGO_OUTPUT_SLOT};
    }

    /**
     * The break handler stages a block-specific portable drawer immediately
     * before Slimefun asks for this item's drops. Consume that one-shot drop;
     * outside a handled drawer break, retain Slimefun's normal default drop.
     */
    @Override
    public @NotNull Collection<ItemStack> getDrops() {
        ItemStack portable = consumePendingBreakDrop();
        return portable == null ? super.getDrops() : List.of(portable);
    }

    @Override
    public @NotNull Collection<ItemStack> getDrops(Player player) {
        ItemStack portable = consumePendingBreakDrop();
        return portable == null ? super.getDrops(player) : List.of(portable);
    }

    private @Nullable ItemStack consumePendingBreakDrop() {
        ItemStack portable = pendingBreakDrop.get();
        pendingBreakDrop.remove();
        return portable;
    }

    public long getCapacity() {
        return capacity;
    }

    /** Safe display name used by SlimeHUD and display entities. */
    public static Component getItemName(@Nullable ItemStack item) {
        return DrawerDisplayManager.getItemName(item);
    }

    /** Returns true when the block is one of this addon's drawers. */
    public static boolean isDrawer(Block block) {
        return SlimefunBlockCompat.getSlimefunItem(block) instanceof SimpleDrawer;
    }

    /** Rebuilds a drawer's visual entities and migrates old Dev-16 data if found. */
    public static void repair(Block block) {
        if (SlimefunBlockCompat.getSlimefunItem(block) instanceof SimpleDrawer) {
            DrawerDisplayManager.repair(block);
        }
    }

    /** Removes only this addon's tagged display entities. */
    public static void removeDisplays(Block block) {
        DrawerDisplayManager.remove(block);
    }

    private void withdrawToPlayer(Block block, Player player) {
        DrawerData data = DrawerStorage.read(block);
        ItemStack stored = data.item();
        if (stored == null || data.count() <= 0) {
            player.sendMessage("§cThere is no item in this drawer.");
            DrawerDisplayManager.update(block, DrawerData.empty());
            return;
        }

        int requested = (int) Math.min(data.count(), stored.getMaxStackSize());
        ItemStack outgoing = MutableItemStacks.copy(stored);
        outgoing.setAmount(requested);

        PlayerInventory inventory = player.getInventory();
        Map<Integer, ItemStack> leftovers = inventory.addItem(outgoing);
        int remaining = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        int accepted = requested - remaining;
        if (accepted <= 0) {
            player.sendMessage("§cYour inventory is full.");
            return;
        }

        DrawerData updated = new DrawerData(stored, data.count() - accepted);
        DrawerStorage.write(block, updated);
        DrawerDisplayManager.update(block, updated);
        player.updateInventory();
    }

    private void depositFromPlayer(Block block, Player player, ItemStack hand) {
        DrawerData data = DrawerStorage.read(block);
        ItemStack stored = data.item();
        ItemStack candidate = one(hand);

        if (stored != null && !stored.isSimilar(candidate)) {
            player.sendMessage("§cThe drawer already contains another item.");
            return;
        }

        long free = capacity - data.count();
        if (free <= 0) {
            player.sendMessage("§cThis drawer is full.");
            return;
        }

        int accepted = (int) Math.min(free, hand.getAmount());
        if (accepted <= 0) {
            player.sendMessage("§cThis drawer is full.");
            return;
        }

        DrawerData updated = new DrawerData(stored == null ? candidate : stored, data.count() + accepted);
        DrawerStorage.write(block, updated);
        DrawerDisplayManager.update(block, updated);

        int newAmount = hand.getAmount() - accepted;
        if (newAmount <= 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            hand.setAmount(newAmount);
        }
        player.updateInventory();
    }

    private DrawerData clamp(DrawerData data) {
        if (data == null || data.isEmpty()) {
            return DrawerData.empty();
        }
        return new DrawerData(data.item(), Math.min(data.count(), capacity));
    }

    /**
     * Adds as many items as possible and mutates {@code item} to the remainder.
     * The returned integer is the remaining amount, preserving the Dev-16 hook API.
     */
    public Pair<Boolean, Integer> addItem(Location barrelLoc, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() < 1) {
            return Pair.of(false, 0);
        }

        Block block = barrelLoc.getBlock();
        synchronized (lock(barrelLoc)) {
            DrawerData data = DrawerStorage.read(block);
            ItemStack stored = data.item();
            ItemStack candidate = one(item);
            if (stored != null && !stored.isSimilar(candidate)) {
                return Pair.of(false, item.getAmount());
            }

            long free = capacity - data.count();
            if (free <= 0) {
                return Pair.of(false, item.getAmount());
            }

            int accepted = (int) Math.min(free, item.getAmount());
            DrawerData updated = new DrawerData(stored == null ? candidate : stored, data.count() + accepted);
            DrawerStorage.write(block, updated);
            DrawerDisplayManager.update(block, updated);

            int remainder = item.getAmount() - accepted;
            // The return value is authoritative. Some Slimefun cargo paths pass
            // immutable ItemStackWrapper instances, so mutating the caller's
            // stack is best-effort only for legacy integrations.
            try {
                if (remainder <= 0) {
                    LegacyItemStackCompat.clearToAir(item);
                } else {
                    item.setAmount(remainder);
                }
            } catch (UnsupportedOperationException ignored) {
                // Immutable wrapper: callers must use the returned remainder.
            }
            return Pair.of(accepted > 0, Math.max(0, remainder));
        }
    }

    /** Returns the stored item with amount one, or null when empty. */
    public @Nullable ItemStack getStoringItem(Location barrelLoc) {
        return DrawerStorage.read(barrelLoc.getBlock()).item();
    }

    /** Returns the exact long count. */
    public long getStoringItemCount(Location barrelLoc) {
        return DrawerStorage.read(barrelLoc.getBlock()).count();
    }

    /** Returns a count safe for legacy APIs that only accept an int. */
    public int getStoringItemCountSafely(Location barrelLoc) {
        return (int) Math.min(getStoringItemCount(barrelLoc), Integer.MAX_VALUE - 1L);
    }

    /**
     * Removes exactly {@code count} items, returning null if insufficient stock exists.
     */
    public @Nullable ItemStack takeItem(Location barrelLoc, int count) {
        if (count < 1) {
            return null;
        }

        Block block = barrelLoc.getBlock();
        synchronized (lock(barrelLoc)) {
            DrawerData data = DrawerStorage.read(block);
            ItemStack stored = data.item();
            if (stored == null || data.count() < count) {
                return null;
            }

            ItemStack result = MutableItemStacks.copy(stored);
            result.setAmount(count);
            DrawerData updated = new DrawerData(stored, data.count() - count);
            DrawerStorage.write(block, updated);
            DrawerDisplayManager.update(block, updated);
            return result;
        }
    }

    private static ItemStack one(ItemStack item) {
        return MutableItemStacks.copyWithAmount(item, 1);
    }

    private static Object lock(Location location) {
        return LOCKS.computeIfAbsent(locationKey(location), ignored -> new Object());
    }

    private static String locationKey(Location location) {
        return (location.getWorld() == null ? "unknown" : location.getWorld().getUID())
                + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
