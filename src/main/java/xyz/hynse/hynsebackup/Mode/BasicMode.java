package xyz.hynse.hynsebackup.Mode;

import com.github.luben.zstd.ZstdOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.bukkit.command.CommandSender;
import xyz.hynse.hynsebackup.BackupManager;
import xyz.hynse.hynsebackup.Util.DisplayUtil;
import xyz.hynse.hynsebackup.Util.SchedulerUtil;
import xyz.hynse.hynsebackup.Util.TimerUtil;

import java.io.*;

public class BasicMode {

    private final BackupManager backupManager;
    private final DisplayUtil displayUtil;
    TimerUtil timer = new TimerUtil();
    public BasicMode(BackupManager backupManager, DisplayUtil displayUtil) {
        this.backupManager = backupManager;
        this.displayUtil = displayUtil;
    }

    public void compressWorld(File source, File destination, CommandSender sender) throws IOException {
        long totalSize = backupManager.getMiscUtil().getFolderSize(source.toPath());
        long[] currentSize = {0};

        SchedulerUtil.runAsyncNowScheduler(backupManager.plugin, () -> {
            try (FileOutputStream fos = new FileOutputStream(destination);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 ZstdOutputStream zos = new ZstdOutputStream(bos, backupManager.backupConfig.getCompressionLevel())) {

                sender.sendMessage("Starting compression of world [" + source.getName() + "] with " + backupManager.backupConfig.getCompressionMode() +" Mode");
                timer.start();

                try (TarArchiveOutputStream taos = new TarArchiveOutputStream(zos)) {
                    taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
                    taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

                    TarArchiveEntry worldEntry = new TarArchiveEntry(source, source.getName() + "/");
                    taos.putArchiveEntry(worldEntry);
                    taos.closeArchiveEntry();

                    compressDirectoryToTar(source, source.getName() + File.separator, taos, totalSize, currentSize);
                }
                timer.stop();
                sender.sendMessage("Compression of world [" + source.getName() + "] with " + backupManager.backupConfig.getCompressionMode() +" Mode completed in " + timer.getElapsedTime());
                displayUtil.finishBossBarProgress();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                displayUtil.removeBossBar();
            }
        });
    }

    private void compressDirectoryToTar(File source, String entryPath, TarArchiveOutputStream taos, long totalSize, long[] currentSize) throws IOException {
        for (File file : source.listFiles()) {
            String filePath = entryPath + file.getName();
            if (file.isDirectory()) {
                TarArchiveEntry dirEntry = new TarArchiveEntry(file, filePath + "/");
                taos.putArchiveEntry(dirEntry);
                taos.closeArchiveEntry();
                compressDirectoryToTar(file, filePath + File.separator, taos, totalSize, currentSize);
            } else {
                addFileToTar(file, filePath, taos, totalSize, currentSize);
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
                currentSize[0] += bytesRead;
                displayUtil.updateBossBarProgress((double) currentSize[0] / totalSize);
            }
        }
        taos.closeArchiveEntry();
    }

}
