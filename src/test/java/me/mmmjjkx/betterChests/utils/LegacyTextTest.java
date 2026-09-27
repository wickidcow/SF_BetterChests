package me.mmmjjkx.betterChests.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class LegacyTextTest {

    @Test
    void parsesAmpersandCodesUsedByGuiItems() {
        var component = LegacyText.component("&bLeft Click: &7Withdraw 1 item");
        assertEquals("Left Click: Withdraw 1 item",
                PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void alsoParsesAlreadyTranslatedSectionCodes() {
        var component = LegacyText.component("\u00A7aQuick Actions");
        assertEquals("Quick Actions",
                PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    void convertsEveryLoreLineAndRemovesLiteralColorCodes() {
        var lore = LegacyText.components(List.of(
                "&bRight Click: &7Withdraw 1 stack",
                "&bShift Right Click: &7Withdraw inventory"
        ));

        String plain = lore.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce("", String::concat);
        assertFalse(plain.contains("&b"));
        assertFalse(plain.contains("&7"));
    }

    @Test
    void serializesAdventureTextForLegacyDisplayRecovery() {
        assertEquals("12,345", LegacyText.plain(Component.text("12,345")));
    }

    @Test
    void leavesAlreadyFormattedComponentsUntouched() {
        Component correct = Component.text("Already formatted");
        assertSame(correct, LegacyText.repair(correct));
    }

    @Test
    void repairsLegacyComponentsFromExistingDroppedItems() {
        Component old = Component.text("&aDrawer &6Lvl 1");
        Component repaired = LegacyText.repair(old);
        assertEquals("Drawer Lvl 1",
                PlainTextComponentSerializer.plainText().serialize(repaired));
    }
}
