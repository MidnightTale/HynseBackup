package xyz.hynse.hynsebackup;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class BackupManager {

    private final JavaPlugin plugin;
    private final BackupConfig backupConfig;
    private final BossBar backupProgressBossBar;

    public BackupManager(JavaPlugin plugin, BackupConfig backupConfig, BossBar backupProgressBossBar) {
        this.plugin = plugin;
        this.backupConfig = backupConfig;
        this.backupProgressBossBar = backupProgressBossBar;

        if (backupConfig.isAutoEnabled()) {
            scheduleAutoBackup();
        }
    }

    private void scheduleAutoBackup() {
        new BukkitRunnable() {
            @Override
            public void run() {
                backupWhitelistedWorlds();
            }
        }.runTaskTimerAsynchronously(plugin, backupConfig.getAutoDelayInterval(), backupConfig.getAutoInterval());
    }
    public void backupWhitelistedWorlds() {
        FileConfiguration config = plugin.getConfig();
        List<String> whitelistWorlds = config.getStringList("whitelist_world");

        for (String worldName : whitelistWorlds) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                backupWorld(world);
            } else {
                plugin.getLogger().warning("World not found: " + worldName);
            }
        }
    }

    public void backupWorld(World world) {
        File worldFolder = world.getWorldFolder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String backupFileName = world.getName() + "_" + LocalDateTime.now().format(formatter) + ".tar.zst";

        File backupWorldFolder = new File(plugin.getDataFolder(), "backup" + File.separator + world.getName());
        backupWorldFolder.mkdirs();
        File backupFile = new File(backupWorldFolder, backupFileName);

        try {
            compressWorld(worldFolder, backupFile);
            plugin.getLogger().info("World backup successfully created: " + backupFile.getAbsolutePath());
            deleteOldBackups(backupWorldFolder);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create world backup: " + e.getMessage());
            e.printStackTrace();
        }
    }

private void compressWorld(File source, File destination) throws IOException {
        long totalSize = getFolderSize(source.toPath());
        long[] currentSize = {0};

        try (FileOutputStream fos = new FileOutputStream(destination);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZstdOutputStream zos = new ZstdOutputStream(bos, Zstd.maxCompressionLevel())) {

            try (TarArchiveOutputStream taos = new TarArchiveOutputStream(zos)) {
                taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
                taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

                TarArchiveEntry worldEntry = new TarArchiveEntry(source.getName() + "/");
                taos.putArchiveEntry(worldEntry);
                taos.closeArchiveEntry();

                compressDirectoryToTar(source, source.getName() + File.separator, taos, totalSize, currentSize);
            }
        }
    }

    private void compressDirectoryToTar(File source, String entryPath, TarArchiveOutputStream taos, long totalSize, long[] currentSize) throws IOException {
        for (File file : source.listFiles()) {
            String filePath = entryPath + file.getName();
            if (file.isDirectory()) {
                TarArchiveEntry dirEntry = new TarArchiveEntry(file, filePath + "/");
                taos.putArchiveEntry(dirEntry);
                taos.closeArchiveEntry();
                compressDirectoryToTar(file, filePath + File.separator, taos, totalSize, currentSize);
            } else {
                addFileToTar(file, filePath, taos, totalSize, currentSize);
            }
        }
    }

    private void addFileToTar(File file, String entryPath, TarArchiveOutputStream taos, long totalSize, long[] currentSize) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(file, entryPath);
        taos.putArchiveEntry(entry);

        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead;
            byte[] buffer = new byte[4096];
            while ((bytesRead = fis.read(buffer)) != -1) {
                taos.write(buffer, 0, bytesRead);
                currentSize[0] += bytesRead;
                updateBossBarProgress((double) currentSize[0] / totalSize);
            }
        }

        taos.closeArchiveEntry();
    }
    private void updateBossBarProgress(double progress) {
        backupProgressBossBar.setProgress(progress);
        for (Player player : Bukkit.getOnlinePlayers()) {
            backupProgressBossBar.addPlayer(player);
        }
    }

    private long getFolderSize(Path folder) throws IOException {
        return Files.walk(folder)
                .filter(p -> p.toFile().isFile())
                .mapToLong(p -> p.toFile().length())
                .sum();
    }

    private void deleteOldBackups(File backupWorldFolder) {
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