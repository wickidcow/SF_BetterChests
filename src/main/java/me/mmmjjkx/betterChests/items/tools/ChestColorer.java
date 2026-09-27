package me.mmmjjkx.betterChests.items.tools;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotPlaceable;
import io.github.thebusybiscuit.slimefun4.core.attributes.Rechargeable;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import me.mmmjjkx.betterChests.BCGroups;
import me.mmmjjkx.betterChests.BetterChests;
import me.mmmjjkx.betterChests.items.chests.SimpleChest;
import me.mmmjjkx.betterChests.compat.SlimefunBlockCompat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChestColorer extends SimpleSlimefunItem<ItemUseHandler> implements NotPlaceable, Rechargeable {
    private static final ColorMaterials DEFAULT_COLOR = ColorMaterials.NoColor;
    private static final NamespacedKey COLOR_KEY = new NamespacedKey(BetterChests.INSTANCE, "color");

    public ChestColorer(SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(BCGroups.TOOLS, item, recipeType, recipe);
    }

    @Override
    public float getMaxItemCharge(ItemStack itemStack) {
        return 200;
    }

    @Override
    public @NotNull ItemUseHandler getItemHandler() {
        return e -> {
            e.cancel();

            Player p = e.getPlayer();
            boolean cycle = p.isSneaking();
            ItemStack item = e.getItem();

            Optional<Block> block = e.getClickedBlock();
            if (block.isPresent()) {
                if (!Slimefun.getProtectionManager().hasPermission(p, block.get(), Interaction.INTERACT_BLOCK)) {
                    p.sendMessage("§cYou don't have permission to interact with this block.");
                    return;
                }
            }

            if (cycle) {
                int index = normalizeIndex(PersistentDataAPI.getOptionalInt(item.getItemMeta(), COLOR_KEY)
                        .orElse(DEFAULT_COLOR.ordinal()));

                int nextIndex = index + 1;
                if (nextIndex >= ColorMaterials.values().length) {
                    nextIndex = DEFAULT_COLOR.ordinal();
                }

                ColorMaterials color = ColorMaterials.values()[nextIndex];

                ItemMeta meta = item.getItemMeta();

                String currentColor = BetterChests.INSTANCE.getLang().getMsg("items.chest_color_changer.current_color");
                String colorName = BetterChests.INSTANCE.getLang().getMsg(color.getTranslationKey());

                Component lore = LegacyComponentSerializer.legacyAmpersand().deserialize(currentColor + colorName)
                        .decoration(TextDecoration.ITALIC, false);
                List<Component> currentLore = meta.lore();
                List<Component> loreList = currentLore == null
                        ? new ArrayList<>()
                        : new ArrayList<>(currentLore);
                while (loreList.size() <= 3) {
                    loreList.add(Component.empty());
                }
                loreList.set(3, lore);

                meta.lore(loreList);

                PersistentDataAPI.setInt(meta, COLOR_KEY, nextIndex);
                item.setItemMeta(meta);

                p.sendActionBar(lore);
            } else {
                if (block.isPresent()) {
                    Block b = block.get();
                    SlimefunItem sfItem = SlimefunBlockCompat.getSlimefunItem(b);
                    if (sfItem instanceof SimpleChest) {
                        if (getItemCharge(item) >= 10) {
                            removeItemCharge(item, 10);
                            int index = normalizeIndex(PersistentDataAPI.getOptionalInt(item.getItemMeta(), COLOR_KEY)
                                    .orElse(DEFAULT_COLOR.ordinal()));
                            ColorMaterials color = ColorMaterials.values()[index];
                            b.setType(color.getMaterial());
                        } else {
                            p.sendMessage(BetterChests.INSTANCE.getLang().getMsg("items.chest_color_changer.no_energy"));
                        }
                    }
                } else {
                    p.sendMessage("§cYou need to right-click at a block to use this item.");
                }
            }
        };
    }

    private static int normalizeIndex(int index) {
        return index >= 0 && index < ColorMaterials.values().length
                ? index
                : DEFAULT_COLOR.ordinal();
    }

    private enum ColorMaterials {
        NoColor(Material.GLASS, "no_color"),
        White(Material.WHITE_STAINED_GLASS, "white"),
        Yellow(Material.YELLOW_STAINED_GLASS, "yellow"),
        Orange(Material.ORANGE_STAINED_GLASS, "orange"),
        Red(Material.RED_STAINED_GLASS, "red"),
        Blue(Material.BLUE_STAINED_GLASS, "blue"),
        Green(Material.GREEN_STAINED_GLASS, "green"),
        Lime(Material.LIME_STAINED_GLASS, "lime"),
        Pink(Material.PINK_STAINED_GLASS, "pink"),
        Purple(Material.PURPLE_STAINED_GLASS, "purple"),
        Brown(Material.BROWN_STAINED_GLASS, "brown"),
        Black(Material.BLACK_STAINED_GLASS, "black"),
        Gray(Material.GRAY_STAINED_GLASS, "gray"),
        LightGray(Material.LIGHT_GRAY_STAINED_GLASS, "light_gray"),
        Cyan(Material.CYAN_STAINED_GLASS, "cyan"),
        Magenta(Material.MAGENTA_STAINED_GLASS, "magenta"),
        LightBlue(Material.LIGHT_BLUE_STAINED_GLASS, "light_blue");

        private final Material material;
        private final String key;

        ColorMaterials(Material material, String key) {
            this.material = material;
            this.key = key;
        }

        public Material getMaterial() {
            return material;
        }

        public String getTranslationKey() {
            return "items.chest_color_changer.colors." + key;
        }
    }
}
