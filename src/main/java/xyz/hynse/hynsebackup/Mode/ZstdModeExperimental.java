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
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

public class ZstdModeExperimental {

    private final BackupManager backupManager;
    private final DisplayUtil displayUtil;
    private final ForkJoinPool forkJoinPool;
    private final TimerUtil timer;

    public ZstdModeExperimental(BackupManager backupManager, DisplayUtil displayUtil) {
        this.backupManager = backupManager;
        this.displayUtil = displayUtil;
        int parallelism = backupManager.backupConfig.getParallelism();
        this.forkJoinPool = new ForkJoinPool(parallelism);
        this.timer = new TimerUtil();
    }

    public void compressWorld(File source, File destination, CommandSender sender) throws IOException {
        long totalSize = backupManager.getMiscUtil().getFolderSize(source.toPath());
        long[] currentSize = {0};

        SchedulerUtil.runAsyncNowScheduler(backupManager.plugin, () -> {
            try (FileOutputStream fos = new FileOutputStream(destination);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 ZstdOutputStream zos = new ZstdOutputStream(bos, backupManager.backupConfig.getZstdLevel())) {
                timer.start();
                String startMessage = "Starting compression of world [" + source.getName() + "] with Zstd Experimental Mode";
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

                    forkJoinPool.invoke(new CompressDirectoryTask(source, source.getName() + File.separator, taos, totalSize, currentSize));
                }
                timer.stop();

                long compressedSize = destination.length();
                String endMessage = "Compression of world [" + source.getName() + "] with Zstd Experimental Mode completed in " + timer.getElapsedTime();
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
                forkJoinPool.shutdown();
                try {
                    forkJoinPool.awaitTermination(1, TimeUnit.MICROSECONDS);
                } catch (InterruptedException e) {
                    backupManager.plugin.getLogger().severe("Failed to wait for the termination of the ForkJoinPool: " + e.getMessage());
                }
                forkJoinPool.getQueuedTaskCount();
            }
        });
    }

    private class CompressDirectoryTask extends RecursiveAction {
        private final File source;
        private final String entryPath;
        private final TarArchiveOutputStream taos;
        private final long totalSize;
        private final long[] currentSize;

        public CompressDirectoryTask(File source, String entryPath, TarArchiveOutputStream taos, long totalSize, long[] currentSize) {
            this.source = source;
            this.entryPath = entryPath;
            this.taos = taos;
            this.totalSize = totalSize;
            this.currentSize = currentSize;
        }

        @Override
        protected void compute() {
            try {
                for (File file : source.listFiles()) {
                    String filePath = entryPath + file.getName();
                    if (file.isDirectory()) {
                        TarArchiveEntry dirEntry = new TarArchiveEntry(file, filePath + "/");
                        taos.putArchiveEntry(dirEntry);
                        taos.closeArchiveEntry();
                        forkJoinPool.invoke(new CompressDirectoryTask(file, filePath + File.separator, taos, totalSize, currentSize));
                    } else {
                        compressFile(file, filePath, taos, totalSize, currentSize);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void compressFile(File file, String entryPath, TarArchiveOutputStream taos, long totalSize, long[] currentSize) throws IOException {
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
            }
            taos.closeArchiveEntry();
        }
    }
}
