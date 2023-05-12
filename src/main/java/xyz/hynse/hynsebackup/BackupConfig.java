package xyz.hynse.hynsebackup;

import org.bukkit.configuration.file.FileConfiguration;

public class BackupConfig {

    private boolean autoEnabled;
    private int autoInterval;
    private int autoDelayInterval;
    private boolean maxBackupEnabled;
    private int maxBackupCount;
    private int parallelism;
    private boolean bossBarEnabled;
    private String compressionMode;
    private int zstdLevel;
    private int zipLevel;

    public BackupConfig(FileConfiguration config) {
        loadSettings(config);
    }

    private void loadSettings(FileConfiguration config) {
        autoEnabled = config.getBoolean("auto.enabled");
        if (autoEnabled) {
            autoInterval = config.getInt("auto.interval");
            autoDelayInterval = config.getInt("auto.delay");
        }
        maxBackupEnabled = config.getBoolean("max_backup.enabled");
        maxBackupCount = config.getInt("max_backup.count");
        parallelism = config.getInt("compression.zstd.parallelism", Runtime.getRuntime().availableProcessors());
        bossBarEnabled = config.getBoolean("compression.bossbar");
        compressionMode = config.getString("compression.mode");
        zstdLevel = config.getInt("compression.zstd.level");
        zipLevel = config.getInt("compression.zip.level");
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

    public int getParallelism() {
        return parallelism;
    }

    public String getCompressionMode() {
        return compressionMode;
    }

    public boolean isBossBarEnabled() {
        return bossBarEnabled;
    }

    public int getZstdLevel() {
        return zstdLevel;
    }

    public int getZipLevel() {
        return zipLevel;
    }
}
