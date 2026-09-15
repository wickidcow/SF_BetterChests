package me.mmmjjkx.betterChests.items.chests.ie;

import io.github.thebusybiscuit.slimefun4.libraries.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;
import me.mmmjjkx.betterChests.BetterChests;
import me.mmmjjkx.betterChests.utils.ItemStackBuilder;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static me.mmmjjkx.betterChests.items.chests.ie.IEStorageUnit.*;

/**
 * Represents a single storage unit with cached data.
 *
 * <p>Malformed or internally inconsistent persisted state is frozen instead of
 * normalized. This preserves the original BlockStorage/menu evidence for Doctor
 * or manual recovery and prevents a routine ticker pass from overwriting it.</p>
 *
 * @author Mooy1, mmmjjkx
 */
@SuppressWarnings("deprecation")
public final class IEStorageCache {

    /* Menu strings */
    private static final String EMPTY_DISPLAY_NAME = ChatColor.WHITE + "Empty";
    private static final String VOID_EXCESS_TRUE = ChatColors.color("&7Void Excess:&e true");
    private static final String VOID_EXCESS_FALSE = ChatColors.color("&7Void Excess:&e false");

    /* BlockStorage keys */
    private static final String STORED_AMOUNT = "stored"; // amount key in block data
    private static final String VOID_EXCESS = "void_excess"; // void excess true or null key

    /* Menu Items */
    private static final ItemStack EMPTY_ITEM = new ItemStackBuilder(Material.BARRIER, meta -> {
        meta.setDisplayName(ChatColor.WHITE + "Empty");
        meta.getPersistentDataContainer().set(EMPTY_KEY, PersistentDataType.BYTE, (byte) 1);
    }).getItemStack();

    /* Space Pattern for Sign Display Names */
    private static final Pattern SPACE = Pattern.compile(" ");

    /* Instance Constants */
    private final IEStorageUnit storageUnit;
    private final BlockMenu menu;

    /* Instance Variables */
    private final String[] signDisplay = new String[2];
    private String displayName;
    private Material material;
    private ItemMeta meta;
    private boolean voidExcess;
    private boolean persistentStateSafe = true;
    private String persistentStateProblem;

    private int amount;

    IEStorageCache(IEStorageUnit storageUnit, BlockMenu menu) {
        this.storageUnit = storageUnit;
        this.menu = menu;

        // Load only the historical stable BlockStorage fields. Invalid raw values
        // are preserved and freeze this cache instead of being normalized to zero.
        reloadData();

        ItemStack display = menu.getItemInSlot(DISPLAY_SLOT);
        if (persistentStateSafe && this.amount == 0) {
            if (isRealDisplayItem(display)) {
                markPersistentStateUnsafe("stored count is zero but the display slot still identifies an item");
            } else {
                setEmptyDisplayName();
                menu.replaceExistingItem(DISPLAY_SLOT, EMPTY_ITEM);
            }
        } else if (persistentStateSafe) {
            if (isRealDisplayItem(display)) {
                load(display, display.getItemMeta());
            } else {
                // A non-empty unit without its display identity is inconsistent.
                // The output slot is strong item-identity evidence, but it contains
                // real withdrawn items and must never be consumed during recovery.
                ItemStack output = menu.getItemInSlot(OUTPUT_SLOT);
                if (isRealDisplayItem(output)) {
                    ItemStack recoveredIdentity = output.clone();
                    recoveredIdentity.setAmount(1);
                    setStored(recoveredIdentity);
                    BetterChests.INSTANCE.getLogger().warning(
                            "Recovered IE storage display identity from the output slot at "
                                    + this.menu.getLocation() + "; the output stack was left untouched.");
                } else {
                    markPersistentStateUnsafe("positive stored count has no display-item identity");
                }
            }
        }

        // void excess handler
        menu.addMenuClickHandler(STATUS_SLOT, (p, slot, item, action) -> {
            if (!persistentStateSafe) {
                p.sendMessage(ChatColor.RED + "This storage unit is frozen because its persisted state needs recovery.");
                p.sendMessage(ChatColor.YELLOW + "Run /sf doctor addons scan and restore from backup if needed.");
                return false;
            }

            this.voidExcess = !this.voidExcess;
            BlockStorage.addBlockInfo(this.menu.getLocation(), VOID_EXCESS, this.voidExcess ? "true" : null);
            if (item != null) {
                ItemMeta itemMeta = item.getItemMeta();
                List<String> lore = itemMeta.getLore() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(itemMeta.getLore());
                while (lore.size() < 2) {
                    lore.add("");
                }
                lore.set(1, this.voidExcess ? VOID_EXCESS_TRUE : VOID_EXCESS_FALSE);
                itemMeta.setLore(lore);
                item.setItemMeta(itemMeta);
            }
            return false;
        });

        // interact handler
        menu.addMenuClickHandler(INTERACT_SLOT, (p, slot, item, action) -> {
            if (!persistentStateSafe) {
                p.sendMessage(ChatColor.RED + "This storage unit is frozen because its persisted state needs recovery.");
                return false;
            }

            if (this.amount == 1) {
                if (action.isShiftClicked() && !action.isRightClicked()) {
                    depositAll(p);
                } else {
                    withdrawLast(p);
                }
            } else if (!isEmpty()) {
                if (action.isRightClicked()) {
                    if (action.isShiftClicked()) {
                        withdraw(p, this.amount - 1);
                    } else {
                        withdraw(p, Math.min(this.material.getMaxStackSize(), this.amount - 1));
                    }
                } else {
                    if (action.isShiftClicked()) {
                        depositAll(p);
                    } else {
                        withdraw(p, 1);
                    }
                }
            }
            return false;
        });

        if (persistentStateSafe) {
            updateStatus();
        }
    }

    public long getStored() {
        return this.amount;
    }

    public boolean isPersistentStateSafe() {
        return persistentStateSafe;
    }

    public String getPersistentStateProblem() {
        return persistentStateProblem;
    }

    private static boolean checkWallSign(Block sign, Block block) {
        return SlimefunTag.WALL_SIGNS.isTagged(sign.getType())
                && sign.getRelative(((WallSign) sign.getBlockData()).getFacing().getOppositeFace()).equals(block);
    }

    private static boolean isRealDisplayItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return !item.hasItemMeta()
                || !item.getItemMeta().getPersistentDataContainer().has(EMPTY_KEY, PersistentDataType.BYTE);
    }

    private void markPersistentStateUnsafe(String problem) {
        this.persistentStateSafe = false;
        this.persistentStateProblem = problem;
        BetterChests.INSTANCE.getLogger().warning(
                "Freezing IE storage at " + this.menu.getLocation() + ": " + problem
                        + ". Raw BlockStorage/menu state will not be normalized automatically.");
    }

    private void setDisplayName(String name) {
        this.displayName = name;

        int len = name.length();
        if (len == 0) {
            this.signDisplay[0] = "";
            this.signDisplay[1] = "";
            return;
        }

        String color;
        if (len >= 2 && name.charAt(0) == ChatColor.COLOR_CHAR) {
            char second = name.charAt(1);
            if (len >= 14 && second == 'x') {
                color = name.substring(0, 14);
            } else {
                color = new String(new char[]{
                        ChatColor.COLOR_CHAR, second
                });
            }
        } else {
            color = null;
        }

        if (name.length() <= 15) {
            this.signDisplay[0] = color != null ? name : ChatColor.WHITE + name;
            this.signDisplay[1] = "";
            return;
        }

        String[] words = SPACE.split(name);
        int i = 1;
        StringBuilder firstLine = new StringBuilder();
        if (color == null) {
            firstLine.append(ChatColor.WHITE);
        }
        firstLine.append(words[0]);
        while (i < words.length && words[i].length() + firstLine.length() < 15) {
            firstLine.append(' ').append(words[i++]);
        }
        this.signDisplay[0] = firstLine.toString();

        if (i < words.length) {
            StringBuilder secondLine = new StringBuilder();
            String first = words[i++];
            if (first.length() <= 1 || first.charAt(0) != ChatColor.COLOR_CHAR) {
                if (color == null) {
                    secondLine.append(ChatColor.WHITE);
                } else {
                    secondLine.append(color);
                }
            }
            secondLine.append(first);
            while (i < words.length) {
                secondLine.append(' ').append(words[i++]);
            }
            this.signDisplay[1] = secondLine.toString();
        } else {
            this.signDisplay[1] = "";
        }
    }

    private void setEmptyDisplayName() {
        this.displayName = EMPTY_DISPLAY_NAME;
        this.signDisplay[0] = EMPTY_DISPLAY_NAME;
        this.signDisplay[1] = "";
    }

    void destroy(BlockBreakEvent e, List<ItemStack> drops) {
        if (!persistentStateSafe) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED
                    + "This storage unit has unsafe persisted state and cannot be broken until it is recovered.");
            return;
        }

        // add output slot
        ItemStack output = this.menu.getItemInSlot(OUTPUT_SLOT);
        if (output != null && matches(output)) {
            int add = Math.min(this.storageUnit.max - this.amount, output.getAmount());
            if (add != 0) {
                this.amount += add;
                output.setAmount(output.getAmount() - add);
            }
        }

        Block b = e.getBlock();
        ItemStack drop = this.storageUnit.getItem().clone();
        drop.setItemMeta(IEStorageUnit.saveToStack(
                drop.getItemMeta(), this.storageUnit.getDisplayingItem(b), this.displayName, this.amount));
        e.getPlayer().sendMessage(ChatColor.GREEN + "Stored items transferred to dropped item");
        drops.add(drop);
    }

    void reloadData() {
        Config config = BlockStorage.getLocationInfo(this.menu.getLocation());
        String stored = config == null ? null : config.getString(STORED_AMOUNT);
        this.voidExcess = config != null && "true".equals(config.getString(VOID_EXCESS));
        this.persistentStateSafe = true;
        this.persistentStateProblem = null;

        if (stored == null || stored.isBlank()) {
            this.amount = 0;
            return;
        }

        final long parsed;
        try {
            parsed = Long.parseLong(stored.trim());
        } catch (NumberFormatException exception) {
            this.amount = 0;
            markPersistentStateUnsafe("stored count is malformed: " + stored);
            return;
        }

        if (parsed < 0L) {
            this.amount = 0;
            markPersistentStateUnsafe("stored count is negative: " + parsed);
            return;
        }
        if (parsed > this.storageUnit.max) {
            this.amount = 0;
            markPersistentStateUnsafe(
                    "stored count " + parsed + " exceeds capacity " + this.storageUnit.max);
            return;
        }

        this.amount = (int) parsed;
    }

    void load(ItemStack stored, ItemMeta copy) {
        if (stored == null || stored.getType().isAir()) {
            setEmpty();
            return;
        }

        ItemStack display = stored.clone();
        display.setAmount(1);
        this.menu.replaceExistingItem(DISPLAY_SLOT, display);

        if (copy == null) {
            copy = display.getItemMeta();
        } else {
            copy = copy.clone();
        }

        // remove the display key from the stored comparison metadata
        copy.getPersistentDataContainer().remove(DISPLAY_KEY);

        // check if the copy has anything besides the display key
        if (copy.equals(Bukkit.getItemFactory().getItemMeta(display.getType()))) {
            this.meta = null;
        } else {
            this.meta = copy;
        }
        setDisplayName(ItemUtils.getItemName(display));
        this.material = display.getType();
    }

    void input() {
        if (!persistentStateSafe) {
            return;
        }

        ItemStack input = this.menu.getItemInSlot(INPUT_SLOT);
        if (input == null) {
            return;
        }
        if (isEmpty()) {
            // set the stored item to input
            this.amount = input.getAmount();
            setStored(input);
            this.menu.replaceExistingItem(INPUT_SLOT, null, false);
        } else if (matches(input)) {
            if (this.voidExcess) {
                // input and void excess
                if (this.amount < this.storageUnit.max) {
                    this.amount = Math.min(this.amount + input.getAmount(), this.storageUnit.max);
                }
                input.setAmount(0);
            } else if (this.amount < this.storageUnit.max) {
                // input as much as possible
                if (input.getAmount() + this.amount >= this.storageUnit.max) {
                    // last item
                    input.setAmount(input.getAmount() - (this.storageUnit.max - this.amount));
                    this.amount = this.storageUnit.max;
                } else {
                    this.amount += input.getAmount();
                    input.setAmount(0);
                }
            }
        }
    }

    private void output() {
        if (!persistentStateSafe || this.amount == 0) {
            return;
        }
        ItemStack outputSlot = this.menu.getItemInSlot(OUTPUT_SLOT);
        if (outputSlot == null) {
            if (this.amount == 1) {
                this.menu.replaceExistingItem(OUTPUT_SLOT, createItem(1), false);
                setEmpty();
            } else {
                int amt = Math.min(this.material.getMaxStackSize(), this.amount - 1);
                this.menu.replaceExistingItem(OUTPUT_SLOT, createItem(amt), false);
                this.amount -= amt;
            }
        } else if (this.amount > 1) {
            int amt = Math.min(this.material.getMaxStackSize() - outputSlot.getAmount(), this.amount - 1);
            if (amt != 0 && matches(outputSlot)) {
                outputSlot.setAmount(outputSlot.getAmount() + amt);
                this.amount -= amt;
            }
        }
    }

    void tick(Block block) {
        if (!persistentStateSafe) {
            return;
        }

        // input output
        input();
        output();

        // store amount
        BlockStorage.addBlockInfo(this.menu.getLocation(), STORED_AMOUNT, String.valueOf(this.amount));

        // status
        if (this.menu.hasViewer()) {
            updateStatus();
        }

        // signs
        Block check = block.getRelative(0, 1, 0);
        if (SlimefunTag.SIGNS.isTagged(check.getType())
                || checkWallSign(check = block.getRelative(1, 0, 0), block)
                || checkWallSign(check = block.getRelative(-1, 0, 0), block)
                || checkWallSign(check = block.getRelative(0, 0, 1), block)
                || checkWallSign(check = block.getRelative(0, 0, -1), block)
        ) {
            Sign sign = (Sign) check.getState();
            sign.setLine(0, this.signDisplay[0]);
            sign.setLine(1, this.signDisplay[1]);
            sign.setLine(2, ChatColor.GRAY + "------------");
            sign.setLine(3, ChatColor.YELLOW.toString() + this.amount);
            sign.update();
        }
    }

    private void updateStatus() {
        this.menu.replaceExistingItem(STATUS_SLOT, new ItemStackBuilder(Material.CYAN_STAINED_GLASS_PANE, meta -> {
            meta.setDisplayName(ChatColor.AQUA + "Status");
            List<String> lore = new ArrayList<>();
            if (this.amount == 0) {
                lore.add(ChatColors.color("&6Stored: &e0 / " + format(this.storageUnit.max) + " &7(0%)"));
            } else {
                lore.add(ChatColors.color("&6Stored: &e" + format(this.amount)
                        + " / " + format(this.storageUnit.max)
                        + " &7(" + format((double) this.amount * 100.D / this.storageUnit.max) + "%)"
                ));
            }
            lore.add(this.voidExcess ? VOID_EXCESS_TRUE : VOID_EXCESS_FALSE);
            lore.add(ChatColor.GRAY + "(Click to toggle)");
            meta.setLore(lore);
        }).getItemStack(), false);
    }

    private void setStored(ItemStack input) {
        this.meta = input.hasItemMeta() ? input.getItemMeta() : null;
        setDisplayName(ItemUtils.getItemName(input));
        this.material = input.getType();

        // add the display key to the display input and set amount 1
        ItemMeta itemMeta = input.getItemMeta();
        itemMeta.getPersistentDataContainer().set(DISPLAY_KEY, PersistentDataType.BYTE, (byte) 1);
        input.setItemMeta(itemMeta);
        input.setAmount(1);

        this.menu.replaceExistingItem(DISPLAY_SLOT, input);
    }

    private void setEmpty() {
        setEmptyDisplayName();
        this.meta = null;
        this.material = null;
        this.menu.replaceExistingItem(DISPLAY_SLOT, EMPTY_ITEM);
        this.amount = 0;
    }

    boolean matches(ItemStack item) {
        return persistentStateSafe
                && item != null
                && this.material != null
                && !item.getType().isAir()
                && item.getType() == this.material
                && item.hasItemMeta() == (this.meta != null)
                && (this.meta == null || this.meta.equals(item.getItemMeta()));
    }

    private ItemStack createItem(int amount) {
        ItemStack item = new ItemStack(this.material, amount);
        if (this.meta != null) {
            item.setItemMeta(this.meta);
        }
        return item;
    }

    boolean isEmpty() {
        return persistentStateSafe && this.amount == 0;
    }

    private void withdraw(Player player, int requested) {
        if (!persistentStateSafe || requested <= 0 || this.amount <= 0) {
            return;
        }

        int remainingToGive = Math.min(requested, this.amount);
        int acceptedTotal = 0;
        Inventory inventory = player.getInventory();

        while (remainingToGive > 0) {
            int stackSize = Math.min(this.material.getMaxStackSize(), remainingToGive);
            ItemStack offered = createItem(stackSize);
            int leftover = inventory.addItem(offered).values().stream()
                    .mapToInt(ItemStack::getAmount)
                    .sum();
            int accepted = stackSize - leftover;
            acceptedTotal += accepted;
            remainingToGive -= accepted;

            if (accepted < stackSize) {
                break;
            }
        }

        if (acceptedTotal > 0) {
            this.amount -= acceptedTotal;
            if (this.amount == 0) {
                setEmpty();
            }
        }
    }

    private void withdrawLast(Player player) {
        if (!persistentStateSafe || this.amount != 1) {
            return;
        }

        int leftover = player.getInventory().addItem(createItem(1)).values().stream()
                .mapToInt(ItemStack::getAmount)
                .sum();
        if (leftover == 0) {
            setEmpty();
        }
    }

    private void depositAll(Player p) {
        depositAll(p.getInventory().getStorageContents());
    }

    public void depositAll(ItemStack[] itemStacks) {
        depositAll(itemStacks, false);
    }

    public void depositAll(ItemStack[] itemStacks, boolean observeVoiding) {
        if (!persistentStateSafe) {
            return;
        }

        if (this.amount < this.storageUnit.max) {
            for (ItemStack item : itemStacks) {
                if (item != null && matches(item)) {
                    if (item.getAmount() + this.amount >= this.storageUnit.max) {
                        // last item
                        item.setAmount(item.getAmount() - (this.storageUnit.max - this.amount));
                        this.amount = this.storageUnit.max;
                    } else {
                        this.amount += item.getAmount();
                        item.setAmount(0);
                    }
                }
            }
        }
        if (observeVoiding && this.voidExcess) {
            for (ItemStack item : itemStacks) {
                if (item != null && matches(item)) {
                    item.setAmount(0);
                }
            }
        }
    }

    private static final DecimalFormat FORMAT = new DecimalFormat("###,###,###,###,###,###.#");

    private String format(double num) {
        return FORMAT.format(num);
    }

    public void amount(int amount) {
        if (!persistentStateSafe) {
            return;
        }

        this.amount = Math.max(0, Math.min(this.storageUnit.max, amount));
        if (this.amount == 0) {
            setEmpty();
        }
    }
}
