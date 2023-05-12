package xyz.hynse.hynsebackup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.plugin.java.JavaPlugin;

public class HynseBackup extends JavaPlugin {

    private BossBar backupProgressBossBar;
    private BackupConfig backupConfig;
    @Override
    public void onEnable() {
        saveDefaultConfig();
        backupConfig = new BackupConfig(getConfig());
        backupProgressBossBar = Bukkit.createBossBar("Backup in progress...", BarColor.BLUE, BarStyle.SEGMENTED_10);
        BackupManager backupManager = new BackupManager(this, backupConfig, backupProgressBossBar);
        BackupCommandExecutor backupCommandExecutor = new BackupCommandExecutor(backupManager, getConfig());
        getCommand("backup").setExecutor(backupCommandExecutor);
        getCommand("backup").setTabCompleter(backupCommandExecutor);
    }
}
