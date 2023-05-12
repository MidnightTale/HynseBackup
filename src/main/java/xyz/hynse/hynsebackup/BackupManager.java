package xyz.hynse.hynsebackup;

import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.hynse.hynsebackup.Mode.ZipMode;
import xyz.hynse.hynsebackup.Mode.ZstdMode;
import xyz.hynse.hynsebackup.Mode.ZstdModeExperimental;
import xyz.hynse.hynsebackup.Util.DisplayUtil;
import xyz.hynse.hynsebackup.Util.MiscUtil;
import xyz.hynse.hynsebackup.Util.SchedulerUtil;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BackupManager {

    public final JavaPlugin plugin;
    public final BackupConfig backupConfig;
    private final ZstdMode zstdMode;
    private final ZstdModeExperimental zstdModeExperimental;
    private final ZipMode zip;
    private final DisplayUtil displayUtil;
    private final MiscUtil miscUtil;
    public MiscUtil getMiscUtil() {
        return this.miscUtil;
    }

    public BackupManager(JavaPlugin plugin, BackupConfig backupConfig, BossBar backupProgressBossBar) {
        this.plugin = plugin;
        this.backupConfig = backupConfig;
        this.displayUtil = new DisplayUtil(backupProgressBossBar, backupConfig);
        this.zstdMode = new ZstdMode(this, displayUtil);
        this.zstdModeExperimental = new ZstdModeExperimental(this, displayUtil);
        this.zip = new ZipMode(this, displayUtil);
        this.miscUtil = new MiscUtil(this, backupConfig, plugin);

        if (backupConfig.isAutoEnabled()) {
            scheduleAutoBackup();
        }

        if (backupConfig.getCompressionMode().equalsIgnoreCase("zstd_experimental")) {
            plugin.getLogger().warning("⚠ WARNING: ZstdExperimental compression mode is experimental and may cause severe performance issues. Use with caution!");
        }
    }
    private void scheduleAutoBackup() {
        SchedulerUtil.runAsyncFixRateScheduler(plugin,
                miscUtil::backupWhitelistedWorlds,
                backupConfig.getAutoDelayInterval(),
                backupConfig.getAutoInterval());
    }
    public void backupWorld(World world, CommandSender sender) {
        File worldFolder = world.getWorldFolder();
        displayUtil.setCurrentWorld(world);
        displayUtil.updateBossBarColor(world.getEnvironment());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String backupFileName = world.getName() + "_" + LocalDateTime.now().format(formatter) + ".tar.zst";

        File backupWorldFolder = new File(plugin.getDataFolder(), "backup" + File.separator + world.getName());
        backupWorldFolder.mkdirs();
        File backupFile = new File(backupWorldFolder, backupFileName);

        try {
            if (backupConfig.getCompressionMode().equalsIgnoreCase("zstd")) {
                zstdMode.compressWorld(worldFolder, backupFile, sender);
            } else if (backupConfig.getCompressionMode().equalsIgnoreCase("zstd_experimental")) {
                zstdModeExperimental.compressWorld(worldFolder, backupFile, sender);
            } else if (backupConfig.getCompressionMode().equalsIgnoreCase("zip")) {
                zip.compressWorld(worldFolder, backupFile, sender);
            }
            plugin.getLogger().info("World backup successfully created: " + backupFile.getAbsolutePath());
            miscUtil.deleteOldBackups(backupWorldFolder);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create world backup: " + e.getMessage());
            e.printStackTrace();
        }
    }
}