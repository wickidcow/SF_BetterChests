package me.mmmjjkx.betterChests.diagnostics;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mmmjjkx.betterChests.BetterChests;
import me.mmmjjkx.betterChests.items.chests.ie.IEStorageUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** State-preserving English presentation migration for portable BetterChests IE Storage Units. */
final class LegacyIEStorageUnitSchemaMigration {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    static final String CANDIDATE_TYPE = "betterchests-ie-storage-english-presentation";
    static final Set<String> ITEM_IDS = createItemIds();

    private LegacyIEStorageUnitSchemaMigration() {
    }

    static @Nullable Result inspect(@NotNull ItemStack stack, @NotNull String slimefunId) {
        if (!ITEM_IDS.contains(slimefunId)) {
            return null;
        }

        ItemMeta meta = stack.getItemMeta();
        if (!containsCjk(meta)) {
            return null;
        }

        PortableState state = readState(meta);
        if (state.malformed()) {
            return new Result(
                "MANUAL_ONLY",
                "Translated IE Storage Unit presentation found, but its portable stored-item state is incomplete or unreadable.",
                null);
        }

        return new Result(
            "READY",
            state.storedItem() == null
                ? "Translated empty IE Storage Unit presentation can be regenerated from its registered English template."
                : "Translated IE Storage Unit presentation can be regenerated from its existing stored item and count.",
            claim(slimefunId, state));
    }

    static boolean migrate(
        @NotNull ItemStack stack,
        @NotNull String slimefunId,
        @NotNull String expectedClaim
    ) {
        if (!ITEM_IDS.contains(slimefunId)) {
            return false;
        }

        ItemMeta meta = stack.getItemMeta();
        PortableState state = readState(meta);
        if (state.malformed()) {
            return false;
        }

        String liveClaim = claim(slimefunId, state);
        if (!MessageDigest.isEqual(
            liveClaim.getBytes(StandardCharsets.US_ASCII),
            expectedClaim.getBytes(StandardCharsets.US_ASCII))) {
            return false;
        }

        SlimefunItem registered = SlimefunItem.getById(slimefunId);
        if (registered == null) {
            return false;
        }

        ItemMeta canonical = registered.getItem().getItemMeta();
        Component canonicalName = canonical.hasDisplayName() ? canonical.displayName() : null;
        Component name = canonicalName != null && !containsCjk(canonicalName)
            ? canonicalName
            : Component.text(humanize(slimefunId.replace("BC_", "")), NamedTextColor.YELLOW);

        List<Component> canonicalLore = canonical.hasLore() ? canonical.lore() : null;
        List<Component> lore = canonicalLore != null
            ? new ArrayList<>(canonicalLore)
            : new ArrayList<>();
        lore.removeIf(LegacyIEStorageUnitSchemaMigration::looksLikeStoredLine);
        if (state.storedItem() != null) {
            lore.add(Component.text("Stored: ", NamedTextColor.GOLD)
                .append(Component.text(englishItemName(state.storedItem())))
                .append(Component.text(" x " + state.amount(), NamedTextColor.YELLOW)));
        }

        boolean changed = !Objects.equals(name, meta.displayName()) || !Objects.equals(lore, meta.lore());
        if (!changed) {
            return false;
        }

        meta.displayName(name);
        meta.lore(lore);
        stack.setItemMeta(meta);
        return true;
    }

    private static @NotNull PortableState readState(@NotNull ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey itemKey = new NamespacedKey(BetterChests.INSTANCE, "item");
        NamespacedKey amountKey = new NamespacedKey(BetterChests.INSTANCE, "stored");

        boolean hasItem = pdc.has(itemKey);
        boolean hasAmount = pdc.has(amountKey);
        if (!hasItem && !hasAmount) {
            return new PortableState(null, 0, false);
        }
        if (hasItem != hasAmount) {
            return new PortableState(null, 0, true);
        }

        try {
            ItemStack storedItem = pdc.get(itemKey, IEStorageUnit.ITEM_STACK);
            Integer amount = pdc.get(amountKey, PersistentDataType.INTEGER);
            if (storedItem == null || storedItem.getType().isAir() || amount == null || amount < 0) {
                return new PortableState(null, 0, true);
            }
            return new PortableState(storedItem.clone(), amount, false);
        } catch (RuntimeException exception) {
            return new PortableState(null, 0, true);
        }
    }

    private static @NotNull String claim(@NotNull String slimefunId, @NotNull PortableState state) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(slimefunId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(state.amount()).array());
            if (state.storedItem() != null) {
                digest.update(state.storedItem().serializeAsBytes());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static @NotNull String englishItemName(@NotNull ItemStack item) {
        SlimefunItem sfItem = SlimefunItem.getByItem(item);
        if (sfItem != null) {
            ItemMeta canonical = sfItem.getItem().getItemMeta();
            Component displayName = canonical.hasDisplayName() ? canonical.displayName() : null;
            if (displayName != null && !containsCjk(displayName)) {
                return PLAIN.serialize(displayName);
            }
            return humanize(sfItem.getId());
        }

        ItemMeta meta = item.getItemMeta();
        Component displayName = meta.hasDisplayName() ? meta.displayName() : null;
        if (displayName != null && !containsCjk(displayName)) {
            return PLAIN.serialize(displayName);
        }
        return humanize(item.getType().name());
    }

    private static boolean looksLikeStoredLine(@Nullable Component line) {
        if (line == null) {
            return false;
        }
        String plain = PLAIN.serialize(line).trim().toLowerCase(Locale.ROOT);
        return plain.startsWith("stored:")
            || plain.startsWith("储存:")
            || plain.startsWith("儲存:")
            || plain.startsWith("存储:")
            || plain.startsWith("存放:");
    }

    private static boolean containsCjk(@NotNull ItemMeta meta) {
        Component displayName = meta.hasDisplayName() ? meta.displayName() : null;
        if (displayName != null && containsCjk(displayName)) {
            return true;
        }
        List<Component> lore = meta.hasLore() ? meta.lore() : null;
        if (lore != null) {
            for (Component line : lore) {
                if (containsCjk(line)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsCjk(@Nullable Component component) {
        return component != null && containsCjk(PLAIN.serialize(component));
    }

    private static boolean containsCjk(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static @NotNull String humanize(@NotNull String id) {
        String[] words = id.toLowerCase(Locale.ROOT).split("[_:-]+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }
        return result.toString();
    }

    private static Set<String> createItemIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (int tier = 1; tier <= 8; tier++) {
            ids.add("BC_IE_STORAGE_UNIT_" + tier);
        }
        return Set.copyOf(ids);
    }

    record Result(@NotNull String readiness, @NotNull String detail, @Nullable String validationClaim) {
    }

    private record PortableState(@Nullable ItemStack storedItem, int amount, boolean malformed) {
    }
}
