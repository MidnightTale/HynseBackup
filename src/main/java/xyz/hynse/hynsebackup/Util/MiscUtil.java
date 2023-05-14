package xyz.hynse.hynsebackup.Util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class MiscUtil {
    private JavaPlugin plugin;

    public MiscUtil(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public long calculateTotalSize(File worldFolder) {
        try (Stream<Path> pathStream = Files.walk(worldFolder.toPath())) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            plugin.getLogger().severe("Failed to get file size: " + path);
                            e.printStackTrace();
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to calculate world size");
            e.printStackTrace();
            return 0;
        }
    }
}