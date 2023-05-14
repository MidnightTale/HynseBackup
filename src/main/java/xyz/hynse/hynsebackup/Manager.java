package xyz.hynse.hynsebackup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.hynse.hynsebackup.Compreesion.DefaultCompreesion;
import xyz.hynse.hynsebackup.Util.MiscUtil;
import xyz.hynse.hynsebackup.Util.SchedulerUtil;
import xyz.hynse.hynsebackup.Util.TimerUtil;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Queue;

public class Manager {
    private final JavaPlugin plugin;

    public Manager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void startNextBackup(Queue<String> worldsToBackup) {
        String worldName = worldsToBackup.poll();
        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                SchedulerUtil.runAsyncnow(plugin, () -> backupWorld(world, worldsToBackup));
            } else {
                plugin.getLogger().warning("World " + worldName + " does not exist, skipping backup.");
                startNextBackup(worldsToBackup);  // Start next backup if this world does not exist
            }
        }
    }
    private void backupWorld(World world, Queue<String> worldsToBackup) {
        plugin.getLogger().info("Starting backup for world " + world.getName() + " ...");
        File backupDirectory = new File(plugin.getDataFolder(), "backup/" + world.getName());
        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            plugin.getLogger().severe("Failed to create backup directory: " + backupDirectory.getPath());
            return;
        }

        // Initiate the compression utility
        MiscUtil miscUtil = new MiscUtil(plugin);
        DefaultCompreesion compression = new DefaultCompreesion(miscUtil, plugin);

        // Call the start method of the compression utility
        try {
            TimerUtil timer = new TimerUtil();
            TimerUtil.start();
            if (Objects.requireNonNull(plugin.getConfig().getString("compression.mode")).equalsIgnoreCase("zstd")) {
                compression.start(world);
            } else if (Objects.requireNonNull(plugin.getConfig().getString("compression.mode")).equalsIgnoreCase("todo1")) {
                compression.start(world);
            }
            TimerUtil.stop();

            plugin.getLogger().info(String.format("Backup for world %s completed. Size: %s, Time: %s",
                    world.getName(), miscUtil.getFormattedTotalSize(world), timer.getElapsedTime()));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to compress the world: " + e.getMessage());
            return;
        }
        miscUtil.limitBackups(world);
        startNextBackup(worldsToBackup);
    }
}
