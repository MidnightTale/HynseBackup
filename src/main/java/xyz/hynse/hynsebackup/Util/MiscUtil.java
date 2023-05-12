    package xyz.hynse.hynsebackup.Util;

    import org.bukkit.Bukkit;
    import org.bukkit.World;
    import org.bukkit.configuration.file.FileConfiguration;
    import org.bukkit.plugin.java.JavaPlugin;
    import xyz.hynse.hynsebackup.BackupConfig;
    import xyz.hynse.hynsebackup.BackupManager;

    import java.io.File;
    import java.io.IOException;
    import java.nio.file.Files;
    import java.nio.file.Path;
    import java.util.Arrays;
    import java.util.Comparator;
    import java.util.List;

    public class MiscUtil {
        private final BackupManager backupManager;
        private final BackupConfig backupConfig;
        private final JavaPlugin plugin;

        public MiscUtil(BackupManager backupManager, BackupConfig backupConfig, JavaPlugin plugin) {
            this.backupManager = backupManager;
            this.backupConfig = backupConfig;
            this.plugin = plugin;
        }

        public long getFolderSize(Path folder) throws IOException {
            return Files.walk(folder)
                    .filter(p -> p.toFile().isFile())
                    .mapToLong(p -> p.toFile().length())
                    .sum();
        }
        public void backupWhitelistedWorlds() {
            FileConfiguration config = plugin.getConfig();
            List<String> whitelistWorlds = config.getStringList("whitelist_world");

            for (String worldName : whitelistWorlds) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    backupManager.backupWorld(world);
                } else {
                    plugin.getLogger().warning("World not found: " + worldName);
                }
            }
        }

        public void deleteOldBackups(File backupWorldFolder) {
            boolean maxBackupEnabled = backupConfig.isMaxBackupEnabled();
            int maxBackupCount = backupConfig.getMaxBackupCount();

            if (maxBackupEnabled) {
                File[] backupFiles = backupWorldFolder.listFiles(file -> file.getName().endsWith(".tar.zst"));
                if (backupFiles != null && backupFiles.length > maxBackupCount) {
                    Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified));
                    for (int i = 0; i < backupFiles.length - maxBackupCount; i++) {
                        if (backupFiles[i].delete()) {
                            plugin.getLogger().info("Deleted old backup: " + backupFiles[i].getAbsolutePath());
                        } else {
                            plugin.getLogger().warning("Failed to delete old backup: " + backupFiles[i].getAbsolutePath());
                        }
                    }
                }
            }
        }
    }
