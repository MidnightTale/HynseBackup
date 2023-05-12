package xyz.hynse.hynsebackup.Util;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import xyz.hynse.hynsebackup.BackupConfig;

public class DisplayUtil {
    private final BossBar backupProgressBossBar;
    private final BackupConfig backupConfig;
    private World currentWorld;

    public DisplayUtil(BossBar backupProgressBossBar, BackupConfig backupConfig) {
        this.backupProgressBossBar = backupProgressBossBar;
        this.backupConfig = backupConfig;
    }

    public void setCurrentWorld(World currentWorld) {
        this.currentWorld = currentWorld;
    }

    public void updateBossBarProgress(double progress) {
        String progressPercent = String.format("%.1f", progress * 100);
        backupProgressBossBar.setTitle("Backing up world: " + currentWorld.getName() + " (" + progressPercent + "%)");
        backupProgressBossBar.setProgress(progress);
        boolean bossBarEnabled = backupConfig.isBossBarEnabled();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (bossBarEnabled) {
                backupProgressBossBar.addPlayer(player);
            }
        }
    }

    public void finishBossBarProgress() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            backupProgressBossBar.setTitle("Backup world: " + currentWorld.getName() + " success.");
            backupProgressBossBar.addPlayer(player);
        }
    }

    public void removeBossBar() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            backupProgressBossBar.removePlayer(player);
        }
    }

    public void updateBossBarColor(World.Environment environment) {
        switch (environment) {
            case NORMAL -> backupProgressBossBar.setColor(BarColor.GREEN);
            case NETHER -> backupProgressBossBar.setColor(BarColor.RED);
            case THE_END -> backupProgressBossBar.setColor(BarColor.WHITE);
        }
    }
}
