package xyz.hynse.hynsebackup;

import org.bukkit.configuration.file.FileConfiguration;

public class BackupConfig {

    private boolean autoEnabled;
    private int autoInterval;
    private int autoDelayInterval;
    private boolean maxBackupEnabled;
    private int maxBackupCount;
    private String compressionMode;
    private int parallelism;
    private boolean bossBarEnabled; // New field

    public BackupConfig(FileConfiguration config) {
        loadSettings(config);
    }

    private void loadSettings(FileConfiguration config) {
        autoEnabled = config.getBoolean("auto.enabled");
        if (autoEnabled) {
            autoInterval = config.getInt("auto.interval");
            autoDelayInterval = config.getInt("auto.delay");
            compressionMode = config.getString("compression.mode");
        }
        maxBackupEnabled = config.getBoolean("max_backup.enabled");
        maxBackupCount = config.getInt("max_backup.count");
        parallelism = config.getInt("compression.parallelism", Runtime.getRuntime().availableProcessors());
        bossBarEnabled = config.getBoolean("compression.bossbar");
    }

    public boolean isAutoEnabled() {
        return autoEnabled;
    }

    public int getAutoInterval() {
        return autoInterval;
    }

    public int getAutoDelayInterval() {
        return autoDelayInterval;
    }

    public boolean isMaxBackupEnabled() {
        return maxBackupEnabled;
    }

    public int getMaxBackupCount() {
        return maxBackupCount;
    }

    public String getCompressionMode() {
        return compressionMode;
    }

    public int getParallelism() {
        return parallelism;
    }

    public boolean isBossBarEnabled() {
        return bossBarEnabled;
    }
}
