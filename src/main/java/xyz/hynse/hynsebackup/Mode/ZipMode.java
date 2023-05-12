package xyz.hynse.hynsebackup.Mode;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.bukkit.command.CommandSender;
import xyz.hynse.hynsebackup.BackupManager;
import xyz.hynse.hynsebackup.Util.DisplayUtil;
import xyz.hynse.hynsebackup.Util.MiscUtil;
import xyz.hynse.hynsebackup.Util.SchedulerUtil;
import xyz.hynse.hynsebackup.Util.TimerUtil;

import java.io.*;

public class ZipMode {

    private final BackupManager backupManager;
    private final DisplayUtil displayUtil;
    TimerUtil timer = new TimerUtil();

    public ZipMode(BackupManager backupManager, DisplayUtil displayUtil) {
        this.backupManager = backupManager;
        this.displayUtil = displayUtil;
    }

    public void compressWorld(File source, File destination, CommandSender sender) throws IOException {
        long totalSize = backupManager.getMiscUtil().getFolderSize(source.toPath());
        long[] currentSize = {0};

        SchedulerUtil.runAsyncNowScheduler(backupManager.plugin, () -> {
            try (FileOutputStream fos = new FileOutputStream(destination);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 ZipArchiveOutputStream zos = new ZipArchiveOutputStream(bos)) {
                zos.setMethod(ZipArchiveOutputStream.DEFLATED);
                zos.setLevel(backupManager.backupConfig.getZipLevel());


                timer.start();
                String startMessage = "Starting compression of world [" + source.getName() + "] with Zip Mode";
                if (sender != null) {
                    sender.sendMessage(startMessage);
                } else {
                    backupManager.plugin.getLogger().info(startMessage);
                }

                compressDirectoryToZip(source, source.getName() + File.separator, zos, totalSize, currentSize);

                timer.stop();

                long compressedSize = destination.length();
                String endMessage = "Compression of world [" + source.getName() + "] with Zip Mode completed in " + timer.getElapsedTime();
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
            }
        });
    }


    private void compressDirectoryToZip(File source, String entryPath, ZipArchiveOutputStream zos, long totalSize, long[] currentSize) throws IOException {
        for (File file : source.listFiles()) {
            String filePath = entryPath + file.getName();
            if (file.isDirectory()) {
                ZipArchiveEntry dirEntry = new ZipArchiveEntry(filePath + "/");
                zos.putArchiveEntry(dirEntry);
                zos.closeArchiveEntry();
                compressDirectoryToZip(file, filePath + File.separator, zos, totalSize, currentSize);
            } else {
                addFileToZip(file, filePath, zos, totalSize, currentSize);
            }
        }
    }

    private void addFileToZip(File file, String entryPath, ZipArchiveOutputStream zos, long totalSize, long[] currentSize) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(entryPath);
        zos.putArchiveEntry(entry);

        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead;
            byte[] buffer = new byte[4096];
            while ((bytesRead = fis.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
                currentSize[0] += bytesRead;
                displayUtil.updateBossBarProgress((double) currentSize[0] / totalSize);
            }
        }
        zos.closeArchiveEntry();
    }
}

