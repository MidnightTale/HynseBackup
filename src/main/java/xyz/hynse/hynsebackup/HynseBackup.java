package xyz.hynse.hynsebackup;

import com.github.luben.zstd.ZstdOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.hynse.hynsebackup.Util.FormatUtil;
import xyz.hynse.hynsebackup.Util.SchedulerUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;
import java.util.stream.Stream;

public class HynseBackup extends JavaPlugin {
    private static final int BUFFER_SIZE = 8192;
    private int lastPrintedProgress = 0;
    private long totalBytesWritten = 0;
    private long startTime = 0;
    @Override
    public void onEnable() {
        saveDefaultConfig();
        Queue<String> worldsToBackup = new LinkedList<>(getConfig().getStringList("whitelist-worlds"));

        startNextBackup(worldsToBackup);
    }

    private void startNextBackup(Queue<String> worldsToBackup) {
        String worldName = worldsToBackup.poll();
        lastPrintedProgress = 0;  // Reset the progress for each world
        totalBytesWritten = 0;  // Reset total bytes written for each world
        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                SchedulerUtil.runAsyncDelay(this, () -> backupWorld(world, worldsToBackup), 10);
            } else {
                getLogger().warning("" + worldName + " does not exist, skipping backup.");
                startNextBackup(worldsToBackup);  // Start next backup if this world does not exist
            }
        }
    }
    private void backupWorld(World world, Queue<String> worldsToBackup) {
        startTime = System.currentTimeMillis(); // Initialize startTime at the beginning of your backup process

        String worldName = world.getName();
        File worldFolder = world.getWorldFolder();
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String backupFileName = worldName + "_" + timeStamp + ".tar.zst";
        File backupFolder = new File(getDataFolder(), "backups");
        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            getLogger().warning("Failed to create backup directory!");
            return;
        }
        File backupFile = new File(backupFolder, backupFileName);
        getLogger().info("Starting Backup " + worldName + "...");
        try (FileOutputStream fileOutputStream = new FileOutputStream(backupFile);
             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
             ZstdOutputStream zstdOutputStream = new ZstdOutputStream(bufferedOutputStream);
             TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(zstdOutputStream)) {
            tarOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU); // replaced TarConstants.LONGFILE_GNU
            long totalSize = calculateTotalSize(worldFolder);
            addFolderToTar(tarOutputStream, worldFolder, worldFolder.getPath(), totalSize, world); // added world argument
            tarOutputStream.finish();
            getLogger().info("Backup " + worldName + " created successfully.");
        } catch (IOException e) {
            getLogger().warning("Failed to create backup " + worldName);
            e.printStackTrace();
        }
        SchedulerUtil.runAsyncDelay(this, () -> startNextBackup(worldsToBackup), 10);
    }

    private void addFolderToTar(TarArchiveOutputStream tarOutputStream, File folder, String basePrefix, long totalSize, World world) throws IOException {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                String entryName = file.getPath().replace("\\", "/").substring(basePrefix.length() + 1);
                if (entryName.equals("session.lock")) {
                    continue;
                }

                if (file.isDirectory()) {
                    addFolderToTar(tarOutputStream, file, basePrefix, totalSize, world);
                } else {
                    try (FileInputStream fileInputStream = new FileInputStream(file);
                         BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {
                        TarArchiveEntry entry = new TarArchiveEntry(file, entryName);
                        tarOutputStream.putArchiveEntry(entry);
                        int count;
                        byte[] data = new byte[BUFFER_SIZE];
                        while ((count = bufferedInputStream.read(data)) != -1) {
                            tarOutputStream.write(data, 0, count);
                            totalBytesWritten += count;
                            printProgress(world, totalBytesWritten, totalSize);
                        }
                        tarOutputStream.closeArchiveEntry();
                    } catch (IOException e) {
                        getLogger().warning("Failed to backup file: " + file.getPath());
                        // Log the exception and continue with the next file
                        e.printStackTrace();
                    }
                }
            }
        }
    }



    private void printProgress(World world, long bytes, long total) {
        // Pass world as a parameter to the printProgress method
        int progress = (int) (100 * bytes / total);
        if (progress >= lastPrintedProgress + 5) {
            getLogger().info(String.format("Backup progress [%s]: %d%%, (%s) Remaining %s",
                    world.getName(),
                    progress,
                    FormatUtil.humanReadableByteCountBin(totalBytesWritten),
                    estimateTimeRemaining(totalBytesWritten, total)));
            lastPrintedProgress = progress;
        }
    }

    private String estimateTimeRemaining(long bytesWritten, long totalBytes) {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        double bytesPerMillisecond = (double) bytesWritten / elapsedTime;
        long remainingBytes = totalBytes - bytesWritten;
        long remainingTime = (long) (remainingBytes / (bytesPerMillisecond * 1000));

        return FormatUtil.formatTime(remainingTime);
    }


    private long calculateTotalSize(File worldFolder) {
        try (Stream<Path> pathStream = Files.walk(worldFolder.toPath())) {
            return pathStream
                    .filter(Files::isRegularFile)                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            getLogger().warning("Failed to get file size: " + path);
                            e.printStackTrace();
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            getLogger().warning("Failed to calculate world size");
            e.printStackTrace();
            return 0;
        }
    }
}

