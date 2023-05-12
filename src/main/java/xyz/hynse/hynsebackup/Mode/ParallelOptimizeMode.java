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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class ParallelOptimizeMode {

    private final BackupManager backupManager;
    private final DisplayUtil displayUtil;
    private static final int BUFFER_SIZE = 8192;

    public ParallelOptimizeMode(BackupManager backupManager, DisplayUtil displayUtil) {
        this.backupManager = backupManager;
        this.displayUtil = displayUtil;
    }

    public void compressWorldParallel(File source, File destination) throws IOException {
        long totalSize = backupManager.getMiscUtil().getFolderSize(source.toPath());
        AtomicLong currentSize = new AtomicLong(0);

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    CommandSender sender = Bukkit.getConsoleSender(); // Use the console sender as the default sender

                    sender.sendMessage("Starting compression of world [" + source.getName() + "] with "+ backupManager.backupConfig.getCompressionMode() +" Mode - thread: " + backupManager.backupConfig.getParallelism());
                    ForkJoinPool forkJoinPool = new ForkJoinPool(backupManager.backupConfig.getParallelism());
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
                    sender.sendMessage("Compression of world [" + source.getName() + "] with "+ backupManager.backupConfig.getCompressionMode() +" Mode - thread: " + backupManager.backupConfig.getParallelism());
                    displayUtil.finishBossBarProgress();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    displayUtil.removeBossBar();
                }
            }
        }.runTaskAsynchronously(backupManager.plugin);
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
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(Deflater.BEST_COMPRESSION))) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
            }

            dos.finish();
            byte[] compressedData = baos.toByteArray();

            compressedFiles.put(entryPath, compressedData);
            currentSize.addAndGet(file.length());
            displayUtil.updateBossBarProgress((double) currentSize.get() / totalSize);
        }
    }


}

