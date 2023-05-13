package xyz.hynse.hynsebackup.Util;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

public class MiscUtil {
    private final JavaPlugin plugin;

    public MiscUtil(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public long calculateTotalSize(File worldFolder) {
        try (Stream<Path> pathStream = Files.walk(worldFolder.toPath())) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            plugin.getLogger().severe("Failed to get file size: " + path);
                            e.printStackTrace();
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to calculate world size");
            e.printStackTrace();
            return 0;
        }
    }

    public int printProgress(World world, long totalSize, long bytesWritten, long startTime, int lastPrintedProgress) {
        String worldName = world.getName();
        int percentDone = (int) ((bytesWritten / (float) totalSize) * 100);

        if (percentDone >= lastPrintedProgress + 1 || percentDone == 100) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            long estimatedTotalTime = (long) (elapsedTime / (percentDone / 100.0));
            long estimatedTimeRemaining = estimatedTotalTime - elapsedTime;

            plugin.getLogger().info(String.format("Backup progress [%s]: %d%%, (%s) ETA: %s",
                    worldName, percentDone, FormatUtil.humanReadableByteCountBin(bytesWritten), FormatUtil.formatTime(estimatedTimeRemaining)));

            return percentDone;
        }

        return lastPrintedProgress;
    }
    public String getFormattedTotalSize(World world) {
        File worldFolder = world.getWorldFolder();
        long totalSize = calculateTotalSize(worldFolder);
        return FormatUtil.humanReadableByteCountBin(totalSize);
    }
    public void limitBackups(World world) {
        if (!plugin.getConfig().getBoolean("max_backup.enabled")) {
            return;
        }

        File backupDirectory = new File(plugin.getDataFolder(), "backup/" + world.getName());
        File[] backupFiles = backupDirectory.listFiles();

        if (backupFiles != null && backupFiles.length > plugin.getConfig().getInt("max_backup.count")) {
            Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < backupFiles.length - plugin.getConfig().getInt("max_backup.count"); i++) {
                if (!backupFiles[i].delete()) {
                    plugin.getLogger().severe("Failed to delete old backup: " + backupFiles[i].getPath());
                }
            }
        }
    }
}
