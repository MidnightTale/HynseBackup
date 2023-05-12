package xyz.hynse.hynsebackup;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.plugin.java.JavaPlugin;

public class HynseBackup extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        BackupConfig backupConfig = new BackupConfig(getConfig());
        BossBar backupProgressBossBar = Bukkit.createBossBar("Backup in progress...", BarColor.BLUE, BarStyle.SEGMENTED_10);
        BackupManager backupManager = new BackupManager(this, backupConfig, backupProgressBossBar);
        BackupCommandExecutor backupCommandExecutor = new BackupCommandExecutor(backupManager, getConfig());
        getCommand("backup").setExecutor(backupCommandExecutor);
        getCommand("backup").setTabCompleter(backupCommandExecutor);
    }
}
