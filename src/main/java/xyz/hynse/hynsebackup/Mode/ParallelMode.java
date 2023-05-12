package xyz.hynse.hynsebackup.Mode;

import com.github.luben.zstd.ZstdOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.bukkit.command.CommandSender;
import xyz.hynse.hynsebackup.BackupManager;
import xyz.hynse.hynsebackup.Util.DisplayUtil;
import xyz.hynse.hynsebackup.Util.MiscUtil;
import xyz.hynse.hynsebackup.Util.SchedulerUtil;
import xyz.hynse.hynsebackup.Util.TimerUtil;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ParallelMode {

    private final BackupManager backupManager;
    private final DisplayUtil displayUtil;
    private final ExecutorService executorService;
    TimerUtil timer = new TimerUtil();

    public ParallelMode(BackupManager backupManager, DisplayUtil displayUtil, int parallelism) {
        this.backupManager = backupManager;
        this.displayUtil = displayUtil;
        this.executorService = Executors.newFixedThreadPool(parallelism);
    }

    public void compressWorld(File source, File destination, CommandSender sender) throws IOException {
        long totalSize = backupManager.getMiscUtil().getFolderSize(source.toPath());
        long[] currentSize = {0};

        SchedulerUtil.runAsyncNowScheduler(backupManager.plugin, () -> {
            try (FileOutputStream fos = new FileOutputStream(destination);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 ZstdOutputStream zos = new ZstdOutputStream(bos, backupManager.backupConfig.getCompressionLevel())) {
                timer.start();
                String startMessage = "Starting compression of world [" + source.getName() + "] with " + backupManager.backupConfig.getCompressionMode() +" Mode";
                if (sender != null) {
                    sender.sendMessage(startMessage);
                } else {
                    backupManager.plugin.getLogger().info(startMessage);
                }

                try (TarArchiveOutputStream taos = new TarArchiveOutputStream(zos)) {
                    taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
                    taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

                    TarArchiveEntry worldEntry = new TarArchiveEntry(source, source.getName() + "/");
                    taos.putArchiveEntry(worldEntry);
                    taos.closeArchiveEntry();

                    compressDirectoryToTar(source, source.getName() + File.separator, taos, totalSize, currentSize);
                }
                executorService.shutdown();
                try {
                    while (!executorService.isTerminated()) {
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                timer.stop();

                long compressedSize = destination.length();
                String endMessage = "Compression of world [" + source.getName() + "] with " + backupManager.backupConfig.getCompressionMode() +" Mode completed in " + timer.getElapsedTime();
                String sizeMessage = "Size of compressed world: " + MiscUtil.humanReadableByteCountBin(compressedSize);
                if (sender != null) {
                    sender.sendMessage(endMessage);
                    sender.sendMessage(sizeMessage);
                } else {
                    backupManager.plugin.getLogger().info(endMessage);
                    backupManager.plugin.getLogger().info(sizeMessage);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                displayUtil.removeBossBar();
                executorService.shutdown();
            }
        });
    }

    private void compressDirectoryToTar(File source, String entryPath,TarArchiveOutputStream taos, long totalSize, long[] currentSize) throws IOException {
        for (File file : source.listFiles()) {
            String filePath = entryPath + file.getName();
            if (file.isDirectory()) {
                TarArchiveEntry dirEntry = new TarArchiveEntry(file, filePath + "/");
                taos.putArchiveEntry(dirEntry);
                taos.closeArchiveEntry();
                compressDirectoryToTar(file, filePath + File.separator, taos, totalSize, currentSize);
            } else {
                addFileToTarParallel(file, filePath, taos, totalSize, currentSize);
            }
        }
    }

    private void addFileToTarParallel(File file, String entryPath, TarArchiveOutputStream taos, long totalSize, long[] currentSize) {
        executorService.execute(() -> {
            try {
                TarArchiveEntry entry = new TarArchiveEntry(file, entryPath);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (FileInputStream fis = new FileInputStream(file)) {
                    int bytesRead;
                    byte[] buffer = new byte[4096];
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                        synchronized (currentSize) {
                            currentSize[0] += bytesRead;
                            displayUtil.updateBossBarProgress((double) currentSize[0] / totalSize);
                        }
                    }
                }
                synchronized (taos) {
                    taos.putArchiveEntry(entry);
                    taos.write(baos.toByteArray());
                    taos.closeArchiveEntry();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

}