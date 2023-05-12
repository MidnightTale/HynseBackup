package xyz.hynse.hynsebackup.Mode;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import xyz.hynse.hynsebackup.BackupManager;
import xyz.hynse.hynsebackup.Util.DisplayUtil;

import java.io.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdvancedMode {

    private final BackupManager backupManager;
    private final DisplayUtil displayUtil;

    public AdvancedMode(BackupManager backupManager, DisplayUtil displayUtil) {
        this.backupManager = backupManager;
        this.displayUtil = displayUtil;
    }

    public void compressWorld(File source, File destination) throws IOException {
        long totalSize = backupManager.getMiscUtil().getFolderSize(source.toPath());
        long[] currentSize = {0};

        ExecutorService executor = Executors.newFixedThreadPool(backupManager.backupConfig.getParallelism());
        CountDownLatch latch = new CountDownLatch((int) totalSize);

        new BukkitRunnable() {
            @Override
            public void run() {
                try (FileOutputStream fos = new FileOutputStream(destination);
                     BufferedOutputStream bos = new BufferedOutputStream(fos);
                     ZstdOutputStream zos = new ZstdOutputStream(bos, Zstd.maxCompressionLevel());
                     TarArchiveOutputStream taos = new TarArchiveOutputStream(zos)) {

                    CommandSender sender = Bukkit.getConsoleSender(); // Use the console sender as the default sender

                    sender.sendMessage("Starting compression of world [" + source.getName() + "] with Advanced mode - thread: " + backupManager.backupConfig.getParallelism());

                    taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
                    taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

                    TarArchiveEntry worldEntry = new TarArchiveEntry(source.getName() + "/");
                    taos.putArchiveEntry(worldEntry);
                    taos.closeArchiveEntry();

                    compressDirectoryToTar(executor, latch, source, source.getName() + File.separator, taos, totalSize, currentSize);
                    latch.await(); // wait for all tasks to finish

                    sender.sendMessage("Compression of world [" + source.getName() + "] with Advanced mode completed - thread: " + backupManager.backupConfig.getParallelism());
                    displayUtil.finishBossBarProgress();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    displayUtil.removeBossBar();
                    executor.shutdown(); // shut down the executor
                }
            }
        }.runTaskAsynchronously(backupManager.plugin);
    }

    private void compressDirectoryToTar(ExecutorService executor, CountDownLatch latch, File source, String entryPath, TarArchiveOutputStream taos, long totalSize, long[] currentSize) {
        for (File file : source.listFiles()) {
            String filePath = entryPath + file.getName();
            if (file.isDirectory()) {
                TarArchiveEntry dirEntry = new TarArchiveEntry(file, filePath + "/");
                try {
                    taos.putArchiveEntry(dirEntry);
                    taos.closeArchiveEntry();
                    compressDirectoryToTar(executor, latch, file, filePath + File.separator, taos, totalSize, currentSize);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                executor.submit(() -> {
                    try {
                        addFileToTar(file, filePath, taos, totalSize, currentSize);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();  // signal that this task is finished
                    }
                });
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
                synchronized (currentSize) {
                    currentSize[0] += bytesRead;
                    displayUtil.updateBossBarProgress((double) currentSize[0] / totalSize);
                }
            }
        } finally {
            taos.flush(); // Flush the TarArchiveOutputStream
            taos.closeArchiveEntry();
        }
    }
}

