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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class ParallelMode {

    private final BackupManager backupManager;
    private final DisplayUtil displayUtil;
    private final ForkJoinPool forkJoinPool;
    TimerUtil timer = new TimerUtil();

    public ParallelMode(BackupManager backupManager, DisplayUtil displayUtil, int parallelism) {
        this.backupManager = backupManager;
        this.displayUtil = displayUtil;
        this.forkJoinPool = new ForkJoinPool(parallelism);
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
                forkJoinPool.shutdown();
                try {
                    forkJoinPool.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
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
                forkJoinPool.shutdown();
            }
        });
    }

    private void compressDirectoryToTar(File source, String entryPath, TarArchiveOutputStream taos, long totalSize, long[] currentSize) throws IOException {
        List<File> files = List.of(source.listFiles());
        forkJoinPool.invoke(new CompressDirectoryAction(files, entryPath, taos, totalSize, currentSize));
    }

    private class CompressDirectoryAction extends RecursiveAction {
        private final List<File> files;
        private final String entryPath;
        private final TarArchiveOutputStream taos;
        private final long totalSize;
        private final long[] currentSize;

        public CompressDirectoryAction(List<File> files, String entryPath, TarArchiveOutputStream taos, long totalSize, long[] currentSize) {
            this.files = files;
            this.entryPath = entryPath;
            this.taos = taos;
            this.totalSize = totalSize;
            this.currentSize = currentSize;
        }

        @Override
        protected void compute() {
            if (files.size() <= 1) {
                if (!files.isEmpty()) {
                    File file = files.get(0);
                    String filePath = entryPath + file.getName();
                    if (file.isDirectory()) {
                        try {
                            TarArchiveEntry dirEntry = new TarArchiveEntry(file, filePath + "/");
                            taos.putArchiveEntry(dirEntry);
                            taos.closeArchiveEntry();
                            compressDirectoryToTar(file, filePath + File.separator, taos, totalSize, currentSize);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        addFileToTar(file, filePath, taos, totalSize, currentSize);
                    }
                }
            } else {
                int split = files.size() / 2;
                List<File> leftFiles = files.subList(0, split);
                List<File> rightFiles = files.subList(split, files.size());

                CompressDirectoryAction leftAction = new CompressDirectoryAction(leftFiles, entryPath, taos, totalSize, currentSize);
                CompressDirectoryAction rightAction = new CompressDirectoryAction(rightFiles, entryPath, taos, totalSize, currentSize);

                invokeAll(leftAction, rightAction);
            }
        }
    }

    private void addFileToTar(File file, String entryPath, TarArchiveOutputStream taos, long totalSize, long[] currentSize) {
        forkJoinPool.execute(() -> {
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

