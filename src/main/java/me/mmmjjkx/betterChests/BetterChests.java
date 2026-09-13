package me.mmmjjkx.betterChests;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import me.mmmjjkx.betterChests.diagnostics.LegacyDoctorBridge;
import me.mmmjjkx.betterChests.integrations.SlimeHudIntegration;
import me.mmmjjkx.betterChests.items.BCItems;
import me.mmmjjkx.betterChests.listeners.DrawerFixListener;
import me.mmmjjkx.betterChests.listeners.LegacyItemTextListener;
import me.mmmjjkx.betterChests.utils.LanguageManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/** Main plugin entry point. */
public final class BetterChests extends JavaPlugin implements SlimefunAddon {

    public static BetterChests INSTANCE;

    private LanguageManager languageManager;

    @Override
    public void onEnable() {
        INSTANCE = this;
        saveDefaultConfig();

        languageManager = new LanguageManager(this);
        BCItems.registerItems();
        getServer().getPluginManager().registerEvents(new DrawerFixListener(), this);
        getServer().getPluginManager().registerEvents(new LegacyItemTextListener(), this);
        LegacyDoctorBridge.register(this);

        if (getServer().getPluginManager().isPluginEnabled("SlimeHUD")) {
            try {
                SlimeHudIntegration.register();
                getLogger().info("SlimeHUD integration enabled.");
            } catch (LinkageError | RuntimeException ex) {
                getLogger().warning("SlimeHUD was found, but its API was incompatible. BetterChests will continue without HUD integration.");
                getLogger().log(Level.WARNING, "SlimeHUD integration failure", ex);
            }
        }

        getLogger().info("BetterChests Albion fork enabled.");
    }

    @Override
    public void onDisable() {
        LegacyDoctorBridge.unregister(this);
        getLogger().info("BetterChests Albion fork disabled.");
    }

    @Override
    public @NotNull JavaPlugin getJavaPlugin() {
        return this;
    }

    @Override
    public String getBugTrackerURL() {
        return "https://github.com/lijinhong11/SfBetterChests/issues";
    }

    public LanguageManager getLang() {
        return languageManager;
    }
}
