package xyz.hynse.hynsebackup;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class HynseBackup extends JavaPlugin {

    private BossBar backupProgressBossBar;
    private BackupConfig backupConfig;
    private BackupManager backupManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        backupConfig = new BackupConfig(getConfig());
        backupProgressBossBar = Bukkit.createBossBar("Backup in progress...", BarColor.BLUE, BarStyle.SEGMENTED_10);
        backupManager = new BackupManager(this, backupConfig, backupProgressBossBar);

        new BukkitRunnable() {
            @Override
            public void run() {
                backupManager.backupWhitelistedWorlds();
            }
        }.runTaskAsynchronously(this);
    }
}
