package xyz.hynse.hynsebackup;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;

public class BackupManager {

    public final JavaPlugin plugin;
    private final BackupConfig backupConfig;
    private final BossBar backupProgressBossBar;
    private World currentWorld;
    public BackupManager(JavaPlugin plugin, BackupConfig backupConfig, BossBar backupProgressBossBar) {
        this.plugin = plugin;
        this.backupConfig = backupConfig;
        this.backupProgressBossBar = backupProgressBossBar;

        if (backupConfig.isAutoEnabled()) {
            scheduleAutoBackup();
        }

        if (backupConfig.getCompressionMode().equalsIgnoreCase("parallel")) {
            plugin.getLogger().warning("Parallel compression mode is experimental and may cause performance issues.");
        }
    }
    private void updateBossBarColor(World.Environment environment) {
        switch (environment) {
            case NORMAL -> backupProgressBossBar.setColor(BarColor.GREEN);
            case NETHER -> backupProgressBossBar.setColor(BarColor.RED);
            case THE_END -> backupProgressBossBar.setColor(BarColor.WHITE);
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

    void backupWorld(World world) {
        File worldFolder = world.getWorldFolder();
        updateBossBarColor(world.getEnvironment());
        this.currentWorld = world;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String backupFileName = world.getName() + "_" + LocalDateTime.now().format(formatter) + ".tar.zst";

        File backupWorldFolder = new File(plugin.getDataFolder(), "backup" + File.separator + world.getName());
        backupWorldFolder.mkdirs();
        File backupFile = new File(backupWorldFolder, backupFileName);

        try {
            if (backupConfig.getCompressionMode().equalsIgnoreCase("parallel")) {
                compressWorldParallel(worldFolder, backupFile);
            } else {
                compressWorld(worldFolder, backupFile);
            }
            plugin.getLogger().info("World backup successfully created: " + backupFile.getAbsolutePath());
            deleteOldBackups(backupWorldFolder);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create world backup: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void compressWorldParallel(File source, File destination) throws IOException {
        long totalSize = getFolderSize(source.toPath());
        AtomicLong currentSize = new AtomicLong(0);

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    CommandSender sender = Bukkit.getConsoleSender(); // Use the console sender as the default sender

                    sender.sendMessage("Starting compression of world [" + source.getName() + "] with Parallel mode - thread: " + backupConfig.getParallelism());
                    ForkJoinPool forkJoinPool = new ForkJoinPool(backupConfig.getParallelism());
                    Map<String, byte[]> compressedFiles = new ConcurrentHashMap<>();
                    forkJoinPool.submit(() -> {
                        try {
                            compressDirectoryToMap(source, source.getName() + File.separator, totalSize, currentSize, compressedFiles);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).join();

                    try (FileOutputStream fos = new FileOutputStream(destination);
                         BufferedOutputStream bos = new BufferedOutputStream(fos);
                         ZstdOutputStream zos = new ZstdOutputStream(bos, Zstd.maxCompressionLevel())) {

                        try (TarArchiveOutputStream taos = new TarArchiveOutputStream(zos)) {
                            taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
                            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

                            TarArchiveEntry worldEntry = new TarArchiveEntry(source.getName() + "/");
                            taos.putArchiveEntry(worldEntry);
                            taos.closeArchiveEntry();

                            for (Map.Entry<String, byte[]> entry : compressedFiles.entrySet()) {
                                TarArchiveEntry fileEntry = new TarArchiveEntry(entry.getKey());
                                fileEntry.setSize(entry.getValue().length);
                                taos.putArchiveEntry(fileEntry);
                                taos.write(entry.getValue());
                                taos.closeArchiveEntry();
                            }
                        }
                    }
                    sender.sendMessage("Compression of world [" + source.getName() + "] with Parallel mode completed - thread: " + backupConfig.getParallelism());
                    FinishBossBarProgress();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    removeBossBar();
                }
            }
        }.runTaskAsynchronously(plugin);
    }


    private void compressWorld(File source, File destination) throws IOException {
        long totalSize = getFolderSize(source.toPath());
        long[] currentSize = {0};

        new BukkitRunnable() {
            @Override
            public void run() {
                try (FileOutputStream fos = new FileOutputStream(destination);
                     BufferedOutputStream bos = new BufferedOutputStream(fos);
                     ZstdOutputStream zos = new ZstdOutputStream(bos, Zstd.maxCompressionLevel())) {

                    CommandSender sender = Bukkit.getConsoleSender(); // Use the console sender as the default sender

                    sender.sendMessage("Starting compression of world [" + source.getName() + "] with Basic mode - thread: " + backupConfig.getParallelism());

                    ExecutorService executorService = Executors.newFixedThreadPool(backupConfig.getParallelism());
                    List<Future<Void>> futures = new ArrayList<>();
                    compressDirectoryToTar(source, source.getName() + File.separator, zos, totalSize, currentSize, executorService, futures);

                    for (Future<Void> future : futures) {
                        future.get(); // Wait for all compression tasks to complete
                    }

                    sender.sendMessage("Compression of world [" + source.getName() + "] with Basic mode completed - thread: " + backupConfig.getParallelism());
                    FinishBossBarProgress();
                } catch (IOException | InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                } finally {
                    removeBossBar();
                }
            }
        }.runTaskAsynchronously(plugin);
    }



    private void compressDirectoryToMap(File source, String entryPath, long totalSize, AtomicLong currentSize, Map<String, byte[]> compressedFiles) throws IOException {
        if (source.listFiles() != null) {
            for (File file : source.listFiles()) {
                String filePath = entryPath + file.getName();
                if (file.isDirectory()) {
                    compressDirectoryToMap(file, filePath + File.separator, totalSize, currentSize, compressedFiles);
                } else {
                    compressFileToMap(file, filePath, totalSize, currentSize, compressedFiles);
                }
            }
        }
    }

    private void compressFileToMap(File file, String entryPath, long totalSize, AtomicLong currentSize, Map<String, byte[]> compressedFiles) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] fileData = new byte[(int) file.length()];
            fis.read(fileData);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
            deflater.setInput(fileData);
            deflater.finish();

            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                baos.write(buffer, 0, count);
            }
            deflater.end();
            byte[] compressedData = baos.toByteArray();

            compressedFiles.put(entryPath, compressedData);
            currentSize.addAndGet(file.length());
            updateBossBarProgress((double) currentSize.get() / totalSize);
        }
    }
    private void compressDirectoryToTar(File source, String entryPath, OutputStream outputStream, long totalSize, long[] currentSize, ExecutorService executorService, List<Future<Void>> futures) throws IOException {
        try (TarArchiveOutputStream taos = new TarArchiveOutputStream(new BufferedOutputStream(outputStream))) {
            taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            for (File file : source.listFiles()) {
                String filePath = entryPath + file.getName();
                if (file.isDirectory()) {
                    TarArchiveEntry dirEntry = new TarArchiveEntry(file, filePath + "/");
                    taos.putArchiveEntry(dirEntry);
                    taos.closeArchiveEntry();
                    compressDirectoryToTar(file, filePath + File.separator, outputStream, totalSize, currentSize, executorService, futures);
                } else {
                    futures.add(executorService.submit(() -> {
                        addFileToTar(file, filePath, taos, totalSize, currentSize);
                        return null;
                    }));
                }
            }
        }
        outputStream.close();
    }

    private void addFileToTar(File file, String entryPath, TarArchiveOutputStream taos, long totalSize, long[] currentSize) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(file, entryPath);
        taos.putArchiveEntry(entry);

        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead;
            byte[] buffer = new byte[4096];
            while ((bytesRead = fis.read(buffer)) != -1) {
                taos.write(buffer, 0, bytesRead);
                updateBossBarProgress((double) currentSize[0] / totalSize);
            }
        }

        taos.closeArchiveEntry();
    }
    private void updateBossBarProgress(double progress) {
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
    private void FinishBossBarProgress() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            backupProgressBossBar.setTitle("Backup world: " + currentWorld.getName() + "success.");
            backupProgressBossBar.addPlayer(player);
        }
    }
    private void removeBossBar() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            backupProgressBossBar.removePlayer(player);
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