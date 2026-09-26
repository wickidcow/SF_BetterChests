package me.mmmjjkx.betterChests.utils;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class LanguageManager {
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final List<String> BUNDLED_LANGUAGES = List.of("en-US", "pt_BR");

    private final Plugin plugin;
    private YamlConfiguration configuration;

    public LanguageManager(Plugin plugin) {
        this.plugin = plugin;
        loadLanguages();
    }

    private void loadLanguages() {
        for (String language : BUNDLED_LANGUAGES) {
            String resource = "language/" + language + ".yml";
            File destination = new File(plugin.getDataFolder(), resource);
            if (!destination.exists()) {
                plugin.saveResource(resource, false);
            }
            completeLangFile(resource);
        }

        String language = plugin.getConfig().getString("options.language", "en-US");
        if (language == null || language.isBlank()) {
            language = "en-US";
        }

        File languageFile = new File(plugin.getDataFolder(), "language/" + language + ".yml");
        if (!languageFile.isFile()) {
            plugin.getLogger().warning("Unknown BetterChests language '" + language + "'; using en-US.");
            language = "en-US";
            languageFile = new File(plugin.getDataFolder(), "language/en-US.yml");
        }

        plugin.getConfig().set("options.language", language);
        plugin.saveConfig();
        configuration = YamlConfiguration.loadConfiguration(languageFile);
    }

    public String getMsg(String key, MessageReplacement... arguments) {
        String message = configuration.getString(key);
        if (message == null) {
            return key;
        }

        for (MessageReplacement argument : arguments) {
            message = argument.parse(message);
        }
        return color(message);
    }

    public List<String> getMsgList(String key, MessageReplacement... arguments) {
        List<String> messages = new ArrayList<>(configuration.getStringList(key));
        messages.replaceAll(message -> {
            String parsed = message;
            for (MessageReplacement argument : arguments) {
                parsed = argument.parse(parsed);
            }
            return color(parsed);
        });
        return messages;
    }

    private String color(String value) {
        return LEGACY_SECTION.serialize(LEGACY_AMPERSAND.deserialize(value));
    }

    private void completeLangFile(String resourceFile) {
        File file = new File(plugin.getDataFolder(), resourceFile);
        try (InputStream stream = plugin.getResource(resourceFile)) {
            if (stream == null) {
                plugin.getLogger().warning("Bundled language resource is missing: " + resourceFile);
                return;
            }

            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                YamlConfiguration current = YamlConfiguration.loadConfiguration(file);

                for (String key : defaults.getKeys(true)) {
                    if (!current.contains(key)) {
                        current.set(key, defaults.get(key));
                    }
                    if (!defaults.getComments(key).equals(current.getComments(key))) {
                        current.setComments(key, defaults.getComments(key));
                    }
                }

                Set<String> currentKeys = Set.copyOf(current.getKeys(true));
                for (String key : currentKeys) {
                    if (!defaults.contains(key)) {
                        current.set(key, null);
                    }
                }
                current.save(file);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Could not update language file '" + resourceFile + "'.", exception);
        }
    }
}
