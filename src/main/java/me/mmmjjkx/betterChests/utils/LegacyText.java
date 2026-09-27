package me.mmmjjkx.betterChests.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Converts legacy BetterChests text into real Adventure components.
 *
 * <p>The upstream project stored strings such as {@code &aName} directly in
 * ItemMeta. Paper packet translation can hide that mistake for held items,
 * but GUIs and dropped item entities display the literal ampersands. Keeping
 * the conversion here gives every BetterChests item the same behaviour.</p>
 */
public final class LegacyText {

    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER =
            PlainTextComponentSerializer.plainText();
    private static final Pattern LEGACY_CODE = Pattern.compile(
            "(?i)(?:[&\\u00A7][0-9A-FK-ORX]|[&\\u00A7]#[0-9A-F]{6})"
    );

    private LegacyText() {
    }

    public static Component component(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty().decoration(TextDecoration.ITALIC, false);
        }

        // LanguageManager and Slimefun helpers may already have converted '&'
        // codes to the section-sign form. Normalize both forms before parsing.
        String normalized = text.replace('\u00A7', '&');
        return AMPERSAND_SERIALIZER.deserialize(normalized)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> components(Collection<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream().map(LegacyText::component).toList();
    }

    /**
     * Repairs an old component only when its visible text still contains raw
     * legacy color codes. Already-correct components are returned unchanged.
     */
    public static String plain(Component component) {
        return component == null ? "" : PLAIN_SERIALIZER.serialize(component);
    }

    public static Component repair(Component original) {
        if (original == null) {
            return null;
        }

        String plain = PLAIN_SERIALIZER.serialize(original);
        return containsCodes(plain) ? component(plain) : original;
    }

    public static boolean containsCodes(String text) {
        return text != null && LEGACY_CODE.matcher(text).find();
    }
}
