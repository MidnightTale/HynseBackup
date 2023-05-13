package xyz.hynse.hynsebackup;

import com.github.luben.zstd.ZstdOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.utils.IOUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.hynse.hynsebackup.Util.FormatUtil;
import xyz.hynse.hynsebackup.Util.SchedulerUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

public class HynseBackup extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        List<String> worldsToBackup = getConfig().getStringList("whitelist-worlds");

        SchedulerUtil.runGlobalScheduler(this, () -> {
            for (String worldName : worldsToBackup) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    backupWorld(world);
                } else {
                    getLogger().warning("World " + worldName + " does not exist, skipping backup.");
                }
            }
        });
    }

    private void backupWorld(World world) {
        getLogger().info("Starting backup for world " + world.getName() + " ...");
        limitBackups(world);
        File worldFolder = world.getWorldFolder();
        File backupDirectory = new File(getDataFolder(), "backup/" + world.getName());
        if (!backupDirectory.exists() && !backupDirectory.mkdirs()) {
            getLogger().severe("Failed to create backup directory: " + backupDirectory.getPath());
            return;
        }

        String formattedDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
        Path backupPath = Paths.get(backupDirectory.getPath(), world.getName() + "-" + formattedDate + ".tar.zst");

        long totalSize = calculateTotalSize(worldFolder); // Calculate total size here

        int compressionLevel = getConfig().getInt("compression.level"); // Add a config for compression level with a default of 3

        long startTime = System.currentTimeMillis();

        try (OutputStream out = Files.newOutputStream(backupPath);
             ZstdOutputStream zstdOut = new ZstdOutputStream(new BufferedOutputStream(out), compressionLevel);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(zstdOut)) {

            addFileToTar(tarOut, worldFolder.toPath(), worldFolder.toPath(), totalSize, startTime, world);

            getLogger().info("Backup for " + world.getName() + " created successfully.");
        } catch (IOException e) {
            getLogger().severe("Failed to create backup for " + world.getName());
            e.printStackTrace();
        }
    }

    private long calculateTotalSize(File worldFolder) {
        try (Stream<Path> pathStream = Files.walk(worldFolder.toPath())) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            getLogger().severe("Failed to get file size: " + path);
                            e.printStackTrace();
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            getLogger().severe("Failed to calculate world size");
            e.printStackTrace();
            return 0;
        }
    }


    private long addFileToTar(TarArchiveOutputStream tarOut, Path rootPath, Path filePath, long totalSize, long startTime, World world) throws IOException {
        long bytesWritten = 0;

        File file = filePath.toFile();
        String entryName = rootPath.relativize(filePath).toString();

        if (entryName.length() > TarConstants.NAMELEN) {
            // Entry name is too long, use long filename mode
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            TarArchiveEntry longNameEntry = new TarArchiveEntry(TarConstants.GNU_LONGLINK, TarConstants.LF_GNUTYPE_LONGNAME);
            longNameEntry.setSize(entryName.length() + 1); // Add 1 for null terminator
            tarOut.putArchiveEntry(longNameEntry);
            tarOut.write(entryName.getBytes());
            tarOut.write(0); // Null terminator
            tarOut.closeArchiveEntry();

            entryName = "././@LongLink";
        }

        if (file.isDirectory()) {
            // Handle directory
            File[] childFiles = file.listFiles();
            if (childFiles != null) {
                for (File childFile : childFiles) {
                    bytesWritten += addFileToTar(tarOut, rootPath, childFile.toPath(), totalSize, startTime, world);
                }
            }
        } else {
            // Handle file
            TarArchiveEntry entry = new TarArchiveEntry(file, entryName);
            try (FileInputStream fis = new FileInputStream(file)) {
                tarOut.putArchiveEntry(entry);

                long fileBytesWritten = IOUtils.copy(fis, tarOut);
                bytesWritten += fileBytesWritten;

                // Call printProgress here
                printProgress(world, totalSize, bytesWritten, startTime);

                tarOut.closeArchiveEntry();
            }
        }
        return bytesWritten;
    }


    private void printProgress(World world, long totalSize, long bytesWritten, long startTime) {
        String worldName = world.getName();
        float percentDone = (bytesWritten / (float) totalSize) * 100;
        long elapsedTime = System.currentTimeMillis() - startTime;
        long estimatedTotalTime = (long) (elapsedTime / (percentDone / 100));
        long estimatedTimeRemaining = estimatedTotalTime - elapsedTime;

        if (percentDone % 5 == 0) {
            getLogger().info(String.format("Backup progress [%s]: %.2f%%, (%s) ETA: %s",
                    worldName, percentDone, FormatUtil.humanReadableByteCountBin(bytesWritten), FormatUtil.formatTime(estimatedTimeRemaining)));
        }
    }



    private void limitBackups(World world) {
        if (!getConfig().getBoolean("max_backup.enabled")) {
            return;
        }

        File backupDirectory = new File(getDataFolder(), "backup/" + world.getName());
        File[] backupFiles = backupDirectory.listFiles();

        if (backupFiles != null && backupFiles.length > getConfig().getInt("max_backup.count")) {
            Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < backupFiles.length - getConfig().getInt("max_backup.count"); i++) {
                if (!backupFiles[i].delete()) {
                    getLogger().severe("Failed to delete old backup: " + backupFiles[i].getPath());
                }
            }
        }
    }
}