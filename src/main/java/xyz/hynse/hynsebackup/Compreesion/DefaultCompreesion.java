package xyz.hynse.hynsebackup.Compreesion;

import com.github.luben.zstd.ZstdOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.hynse.hynsebackup.Util.MiscUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DefaultCompreesion {
    private static final int BUFFER_SIZE = 8192;

    private MiscUtil miscUtil;
    private JavaPlugin plugin;

    public DefaultCompreesion(MiscUtil miscUtil, JavaPlugin plugin) {
        this.miscUtil = miscUtil;
        this.plugin = plugin;
    }

    public void start(World world) throws IOException {
        String formattedDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
        File backupDirectory = new File(plugin.getDataFolder(), "backup/" + world.getName());
        Path backupPath = Paths.get(backupDirectory.getPath(), world.getName() + "-" + formattedDate + ".tar.zst");
        int compressionLevel = plugin.getConfig().getInt("compression.level", 3);

        File worldFolder = world.getWorldFolder();
        long startTime = System.currentTimeMillis();
        long totalSize = miscUtil.calculateTotalSize(worldFolder);
        long[] totalBytesWritten = new long[1];
        try (OutputStream out = Files.newOutputStream(backupPath);
             BufferedOutputStream bufferedOut = new BufferedOutputStream(out, BUFFER_SIZE);
             ZstdOutputStream zstdOut = new ZstdOutputStream(bufferedOut, compressionLevel);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(zstdOut)) {

            tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            addFileToTar(tarOut, worldFolder.toPath(), worldFolder.toPath(), totalSize, startTime, totalBytesWritten, miscUtil, world);
        }
    }

    private void addFileToTar(TarArchiveOutputStream tarOut, Path rootPath, Path filePath, long totalSize, long startTime, long[] totalBytesWritten, MiscUtil miscUtil, World world) throws IOException {
        File file = filePath.toFile();
        String entryName = rootPath.relativize(filePath).toString();
        int lastPrintedProgress = 0;

        if (entryName.length() > TarConstants.NAMELEN) {
            // Entry name is too long, use long filename mode
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
                    addFileToTar(tarOut, rootPath, childFile.toPath(), totalSize, startTime, totalBytesWritten, miscUtil, world);
                }
            }
        } else {
            // Handle file
            TarArchiveEntry entry = new TarArchiveEntry(file, entryName);
            entry.setModTime(startTime);
            entry.setSize(file.length());
            tarOut.putArchiveEntry(entry);

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    tarOut.write(buffer, 0, bytesRead);
                    totalBytesWritten[0] += bytesRead;
                    lastPrintedProgress = miscUtil.printProgress(world, totalSize, totalBytesWritten[0], startTime, lastPrintedProgress);
                }
            }

            tarOut.closeArchiveEntry();
        }
    }

}
