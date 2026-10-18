package com.github.drakescraft_labs.galactifun;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPluginLoader;

import com.github.drakescraft_labs.galactifun.api.worlds.AlienWorld;
import com.github.drakescraft_labs.galactifun.api.worlds.PlanetaryWorld;
import com.github.drakescraft_labs.galactifun.base.BaseAlien;
import com.github.drakescraft_labs.galactifun.base.BaseItems;
import com.github.drakescraft_labs.galactifun.base.BaseMats;
import com.github.drakescraft_labs.galactifun.base.BaseUniverse;
import com.github.drakescraft_labs.galactifun.core.CoreItemGroup;
import com.github.drakescraft_labs.galactifun.core.commands.AlienRemoveCommand;
import com.github.drakescraft_labs.galactifun.core.commands.AlienSpawnCommand;
import com.github.drakescraft_labs.galactifun.core.commands.EffectsCommand;
import com.github.drakescraft_labs.galactifun.core.commands.GalactiportCommand;
import com.github.drakescraft_labs.galactifun.core.commands.SealedCommand;
import com.github.drakescraft_labs.galactifun.core.commands.StructureCommand;
import com.github.drakescraft_labs.galactifun.core.managers.AlienManager;
import com.github.drakescraft_labs.galactifun.core.managers.ProtectionManager;
import com.github.drakescraft_labs.galactifun.core.managers.WorldManager;
import dev.drake.infinitylib.common.Scheduler;
import dev.drake.infinitylib.core.AbstractAddon;

import io.github.thebusybiscuit.slimefun4.api.MinecraftVersion;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;


public final class Galactifun extends AbstractAddon {

    private static final String LEGACY_PLUGIN_NAMESPACE = "galactifun-drake";

    @Getter
    private static Galactifun instance;

    private boolean isTest = false;

    private AlienManager alienManager;
    private WorldManager worldManager;
    private ProtectionManager protectionManager;

    private boolean shouldDisable = false;

    public Galactifun() {
        super("Slimefun-Addon-Community", "Galactifun", "master", "auto-update");
    }

    public Galactifun(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
        super(loader, description, dataFolder, file, "Slimefun-Addon-Community", "Galactifun", "master", "auto-update");
        isTest = true;
    }

    public static AlienManager alienManager() {
        return instance.alienManager;
    }

    public static WorldManager worldManager() {
        return instance.worldManager;
    }

    public static ProtectionManager protectionManager() {
        return instance.protectionManager;
    }

    /**
     * Keeps existing entity, item and chunk data readable after the addon was renamed to Galaxyfun.
     * New Galaxyfun-only features should use their own namespace instead of extending this legacy surface.
     */
    public static NamespacedKey legacyKey(String key) {
        return new NamespacedKey(LEGACY_PLUGIN_NAMESPACE, key);
    }

    public File getPersistentDataFile(String name) {
        return new File(getDataFolder(), name);
    }

    @Override
    protected void enable() {
        instance = this;

        migrateLegacyData();

        if (!isTest) {
            if (Slimefun.getMinecraftVersion().isBefore(MinecraftVersion.MINECRAFT_1_17)) {
                log(Level.SEVERE, "Galactifun only supports Minecraft 1.17 and above");
                log(Level.SEVERE, "Please use Minecraft 1.17 or above");
                shouldDisable = true;
            }
            if (Bukkit.getPluginManager().isPluginEnabled("ClayTech")) {
                log(Level.SEVERE, "Galactifun will not work properly with ClayTech");
                log(Level.SEVERE, "Please disable ClayTech");
                shouldDisable = true;
            }

            if (Bukkit.getPluginManager().isPluginEnabled("ChatColor2")) {
                log(Level.SEVERE, "Galactifun will not work properly with ChatColor2");
                log(Level.SEVERE, "Please disable ChatColor2");
                shouldDisable = true;
            }

            if (shouldDisable) {
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
        }



        this.alienManager = new AlienManager(this);
        this.worldManager = new WorldManager(this);
        this.protectionManager = new ProtectionManager();

        BaseAlien.setup(this.alienManager);
        if (!isTest) {
            BaseUniverse.setup(this);
        }
        CoreItemGroup.setup(this);
        BaseMats.setup();
        BaseItems.setup(this);

        // log after startup
        Scheduler.run(() -> log(Level.INFO,
                "################# Galaxyfun " + getPluginVersion() + " #################",
                "",
                "Galactifun DrakesCraft Edition is maintained by DrakesCraft Labs.",
                "Repository: https://github.com/DrakesCraft-Labs/Galaxyfun-drake",
                "Issues: https://github.com/DrakesCraft-Labs/Galaxyfun-drake/issues",
                "",
                "###################################################"
        ));

        getAddonCommand()
                .addSub(new GalactiportCommand())
                .addSub(new AlienSpawnCommand())
                .addSub(new AlienRemoveCommand())
                .addSub(new StructureCommand(this))
                .addSub(new SealedCommand())
                .addSub(new EffectsCommand());
    }

    private void migrateLegacyData() {
        copyLegacyData(new File("plugins/Galactifun-drake", "config.yml"), getPersistentDataFile("config.yml"));
        copyLegacyData(new File("plugins/Galactifun", "worlds.yml"), getPersistentDataFile("worlds.yml"));
        copyLegacyData(new File("plugins/Galactifun", "uuids.yml"), getPersistentDataFile("uuids.yml"));
    }

    private void copyLegacyData(File source, File target) {
        if (!source.isFile() || target.isFile()) {
            return;
        }

        try {
            Files.createDirectories(target.toPath().getParent());
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            log(Level.INFO, "Migrated legacy data file: " + source.getPath() + " -> " + target.getPath());
        } catch (IOException ex) {
            log(Level.WARNING, "Could not migrate legacy data file " + source.getPath() + ": " + ex.getMessage());
        }
    }

    @Override
    protected void disable() {
        if (shouldDisable) return;

        this.alienManager.onDisable();

        // Do this last
        instance = null;
    }

    @Override
    public void load() {
        if (!isTest) {
            // Default to not logging world settings
            Bukkit.spigot().getConfig().set("world-settings.default.verbose", false);
        }
    }

    @Nullable
    @Override
    public ChunkGenerator getDefaultWorldGenerator(@Nonnull String worldName, @Nullable String id) {
        if (this.worldManager == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        PlanetaryWorld planetaryWorld = this.worldManager.getWorld(world);
        if (planetaryWorld instanceof AlienWorld) {
            return planetaryWorld.world().getGenerator();
        }

        return null;
    }

}
