package com.cavetale.overboard.world;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.file.YamlConfiguration;
import static com.cavetale.overboard.OverboardPlugin.plugin;

/**
 * Utility class for the copying and loading of temporary worlds.
 */
public final class Worlds {
    /**
     * Load the copy of a world.
     * @return the world
     */
    public static World loadWorldCopy(String worldName) {
        plugin().getLogger().info("[Worlds] loadWorld " + worldName);
        File folder = copyWorld(worldName);
        if (folder == null) return null;
        File configFile = new File(folder, "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        WorldCreator wc = new WorldCreator(folder.getName());
        wc.environment(World.Environment.valueOf(config.getString("world.Environment", "NORMAL")));
        wc.generateStructures(config.getBoolean("world.GenerateStructures", false));
        wc.generator(config.getString("world.Generator", "VoidGenerator"));
        wc.type(WorldType.valueOf(config.getString("world.WorldType", "NORMAL")));
        World result = Bukkit.createWorld(wc);
        result.setAutoSave(false);
        return result;
    }

    /**
     * Unload a world and delete its files.
     * The world is hopefully temporary.
     */
    public static void deleteWorld(World world) {
        plugin().getLogger().info("[Worlds] deleteWorld " + world.getName());
        File folder = world.getWorldFolder();
        if (!Bukkit.unloadWorld(world, false)) {
            throw new IllegalStateException("Unloading world " + world.getName());
        }
        deleteFileStructure(folder);
    }

    /**
     * Copy files recursively.  This will use the helper function
     * before.
     */
    private static void copyFileStructure(File source, File target) {
        copyFileStructure(source, target, 0);
    }

    /**
     * Copy world files recursively.  This is a helper function for
     * the function above.  The difference being that this maintains a
     * depth.
     */
    private static void copyFileStructure(File source, File target, int depth) {
        plugin().getLogger().info("[Worlds] copyFileStructure " + source + " => " + target);
        if (source.isDirectory()) {
            if (!target.exists()) {
                if (!target.mkdirs()) {
                    throw new IllegalStateException("Couldn't create world directory: " + target);
                }
            }
            String[] files = source.list();
            for (String file : files) {
                File srcFile = new File(source, file);
                File destFile = new File(target, file);
                copyFileStructure(srcFile, destFile, depth + 1);
            }
        } else {
            if (depth == 1) {
                // Don't copy uid and session.lock!
                switch (source.getName()) {
                case "uid.dat":
                case "session.lock":
                    return;
                default:
                    break;
                }
            }
            try {
                Files.copy(source.toPath(), target.toPath());
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }
    }

    /**
     * Copy a world which is not necessarily loaded.  The destination
     * is a generated unique folder starting with "tmp_" in the worlds
     * folder.  The resulting folder can be used to load a world.
     * @return the destination file
     */
    private static File copyWorld(String worldName) {
        plugin().getLogger().info("[Worlds] copyWorld " + worldName);
        File source = new File("/home/cavetale/creative/worlds/" + worldName);
        if (!source.exists()) return null;
        int suffix = 0;
        File dest;
        do {
            String fileName = "tmp_" + suffix++;
            dest = new File(Bukkit.getWorldContainer(), fileName);
        } while (dest.exists());
        copyFileStructure(source, dest);
        return dest;
    }

    /**
     * Delete files recursively.
     */
    private static void deleteFileStructure(File file) {
        plugin().getLogger().info("[Worlds] deleteFileStructure " + file);
        if (!file.exists()) return;
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteFileStructure(child);
            }
        }
        file.delete();
    }

    private Worlds() { }
}
