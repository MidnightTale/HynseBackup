package xyz.hynse.hynsebackup;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import xyz.hynse.hynsebackup.Mode.AdvancedMode;
import xyz.hynse.hynsebackup.Mode.BasicMode;
import xyz.hynse.hynsebackup.Mode.ParallelMode;
import xyz.hynse.hynsebackup.Mode.ParallelOptimizeMode;
import xyz.hynse.hynsebackup.Util.DisplayUtil;
import xyz.hynse.hynsebackup.Util.MiscUtil;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BackupManager {

    public final JavaPlugin plugin;
    public final BackupConfig backupConfig;
    private final BasicMode basic;
    private final ParallelMode parallel;
    private final ParallelOptimizeMode parallelOptimize;
    private final AdvancedMode advanced;
    private final DisplayUtil displayUtil;
    private final MiscUtil miscUtil;
    public MiscUtil getMiscUtil() {
        return this.miscUtil;
    }

    public BackupManager(JavaPlugin plugin, BackupConfig backupConfig, BossBar backupProgressBossBar) {
        this.plugin = plugin;
        this.backupConfig = backupConfig;
        this.displayUtil = new DisplayUtil(backupProgressBossBar, backupConfig);
        this.basic = new BasicMode(this, displayUtil);
        this.parallel = new ParallelMode(this, displayUtil);
        this.parallelOptimize = new ParallelOptimizeMode(this, displayUtil);
        this.advanced = new AdvancedMode(this, displayUtil);
        this.miscUtil = new MiscUtil(this, backupConfig, plugin);

        if (backupConfig.isAutoEnabled()) {
            scheduleAutoBackup();
        }

        if (backupConfig.getCompressionMode().equalsIgnoreCase("parallel")) {
            plugin.getLogger().warning("⚠ WARNING: parallel compression mode is experimental and may cause severe performance issues. Use with caution!");
        }
        if (backupConfig.getCompressionMode().equalsIgnoreCase("experimental1")) {
            plugin.getLogger().warning("⚠ WARNING: advanced compression mode is experimental and may cause severe performance issues. Use with caution!");
        }
        if (backupConfig.getCompressionMode().equalsIgnoreCase("experimental2")) {
            plugin.getLogger().warning("⚠ WARNING: parallelOptimize compression mode is experimental and may cause severe performance issues. Use with caution!");
        }
    }
    private void scheduleAutoBackup() {
        new BukkitRunnable() {
            @Override
            public void run() {
                miscUtil.backupWhitelistedWorlds();
            }
        }.runTaskTimerAsynchronously(plugin, backupConfig.getAutoDelayInterval(), backupConfig.getAutoInterval());
    }
    public void backupWorld(World world) {
        File worldFolder = world.getWorldFolder();
        displayUtil.setCurrentWorld(world);
        displayUtil.updateBossBarColor(world.getEnvironment());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String backupFileName = world.getName() + "_" + LocalDateTime.now().format(formatter) + ".tar.zst";

        File backupWorldFolder = new File(plugin.getDataFolder(), "backup" + File.separator + world.getName());
        backupWorldFolder.mkdirs();
        File backupFile = new File(backupWorldFolder, backupFileName);

        try {
            if (backupConfig.getCompressionMode().equalsIgnoreCase("parallel")) {
                parallel.compressWorldParallel(worldFolder, backupFile);
            } else if (backupConfig.getCompressionMode().equalsIgnoreCase("basic")) {
                basic.compressWorld(worldFolder, backupFile);
            } else if (backupConfig.getCompressionMode().equalsIgnoreCase("experimental1")) {
                advanced.compressWorld(worldFolder, backupFile);
            } else if (backupConfig.getCompressionMode().equalsIgnoreCase("experimental2")) {
                parallelOptimize.compressWorldParallel(worldFolder, backupFile);
            }
            plugin.getLogger().info("World backup successfully created: " + backupFile.getAbsolutePath());
            miscUtil.deleteOldBackups(backupWorldFolder);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create world backup: " + e.getMessage());
            e.printStackTrace();
        }
    }
}