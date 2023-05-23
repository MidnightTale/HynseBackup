package xyz.hynse.hynsebackup;

import com.github.luben.zstd.ZstdOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.hynse.hynsebackup.Util.FormatUtil;
import xyz.hynse.hynsebackup.Util.SchedulerUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

public class HynseBackup extends JavaPlugin {
    public static HynseBackup instance;
    public static final int BUFFER_SIZE = 8192;
    public int lastPrintedProgress = 0;
    public long totalBytesWritten = 0;
    public long startTime = 0;

    private int compressionLevel;
    private boolean printProgress;
    private boolean maxBackupEnabled;
    private int maxBackupCount;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        compressionLevel = getConfig().getInt("compression.level", 3);
        printProgress = getConfig().getBoolean("print-progress", true);
        boolean autoInterventionEnabled = getConfig().getBoolean("auto-intervention.enabled", true);
        int autoInterventionInterval = getConfig().getInt("auto-intervention.interval", 3600);
        maxBackupEnabled = getConfig().getBoolean("max-backup.enabled", true);
        maxBackupCount = getConfig().getInt("max-backup.count", 1);

        if (autoInterventionEnabled) {
            SchedulerUtil.runAsyncFixRateScheduler(this, this::performAutoIntervention, autoInterventionInterval, autoInterventionInterval);
        }

    }

    private void startNextBackup(Queue<String> worldsToBackup) {
        String worldName = worldsToBackup.poll();
        lastPrintedProgress = 0;
        totalBytesWritten = 0;
        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                SchedulerUtil.runAsyncDelay(this, () -> backupWorld(world, worldsToBackup), 10);
            } else {
                getLogger().warning(worldName + " does not exist, skipping backup.");
                startNextBackup(worldsToBackup);
            }
        }
    }

    private void backupWorld(World world, Queue<String> worldsToBackup) {
        startTime = System.currentTimeMillis();

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
             ZstdOutputStream zstdOutputStream = new ZstdOutputStream(bufferedOutputStream, compressionLevel)) {
            TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(zstdOutputStream);
            tarOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            long totalSize = calculateTotalSize(worldFolder);
            addFolderToTar(tarOutputStream, worldFolder, worldFolder.getPath(), totalSize, world);
            tarOutputStream.finish();
            getLogger().info("Backup " + worldName + " created successfully.");

            if (maxBackupEnabled) {
                cleanupOldBackups(backupFolder, worldName);
            }
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
                            if (printProgress) {
                                printProgress(world, totalBytesWritten, totalSize);
                            }
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
        if (!printProgress) {
            return;
        }

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
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
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

    private void performAutoIntervention() {
        getLogger().info("Performing auto backup intervention...");
        Queue<String> worldsToBackup = new LinkedList<>(getConfig().getStringList("whitelist-worlds"));
        startNextBackup(worldsToBackup);
    }

    private void cleanupOldBackups(File backupFolder, String worldName) {
        File[] backupFiles = backupFolder.listFiles();
        if (backupFiles != null) {
            int totalBackups = 0;
            for (File backupFile : backupFiles) {
                if (backupFile.getName().startsWith(worldName + "_")) {
                    totalBackups++;
                }
            }

            int excessBackups = totalBackups - maxBackupCount;
            if (excessBackups > 0) {
                for (File backupFile : backupFiles) {
                    if (backupFile.getName().startsWith(worldName + "_") && excessBackups > 0) {
                        if (backupFile.delete()) {
                            getLogger().info("Deleted old backup file: " + backupFile.getName());
                            excessBackups--;
                        } else {
                            getLogger().warning("Failed to delete old backup file: " + backupFile.getName());
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("backup")) {
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("reload")) {
                    if (sender.hasPermission("hynsebackup.reload")) {
                        reloadConfig(); // Reload the configuration

                        compressionLevel = getConfig().getInt("compression.level", 3);
                        printProgress = getConfig().getBoolean("print-progress", true);
                        maxBackupEnabled = getConfig().getBoolean("max-backup.enabled", true);
                        maxBackupCount = getConfig().getInt("max-backup.count", 1);

                        sender.sendMessage("Backup configuration reloaded.");
                    } else {
                        sender.sendMessage("You do not have permission to use this command.");
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("start")) {
                    if (args.length > 1) {
                        if (sender.hasPermission("hynsebackup.start")) {
                            String worldName = args[1];
                            World world = Bukkit.getWorld(worldName);
                            if (world != null) {
                                Queue<String> worldsToBackup = new LinkedList<>();
                                worldsToBackup.add(worldName);
                                startNextBackup(worldsToBackup);
                                sender.sendMessage("Starting backup for world: " + worldName);
                            } else {
                                sender.sendMessage("World " + worldName + " does not exist.");
                            }
                        } else {
                            sender.sendMessage("You do not have permission to use this command.");
                        }
                    } else {
                        sender.sendMessage("Usage: /backup start <world>");
                    }
                    return true;
                }
            }
        }
        return false;
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("backup")) {
            if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
                if (sender.hasPermission("hynsebackup.start")) {
                    String worldPrefix = args[1].toLowerCase();
                    List<String> worldNames = new ArrayList<>();
                    for (World world : Bukkit.getWorlds()) {
                        if (world.getName().toLowerCase().startsWith(worldPrefix)) {
                            worldNames.add(world.getName());
                        }
                    }
                    return worldNames;
                }
            }
        }
        return super.onTabComplete(sender, command, alias, args);
    }

}
