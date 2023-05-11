package xyz.hynse.hynsebackup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.Arrays;

public class BackupCommandExecutor implements CommandExecutor {
    private final BackupManager backupManager;

    public BackupCommandExecutor(BackupManager backupManager) {
        this.backupManager = backupManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                if (args.length == 2) {
                    String worldName = args[1];
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        backupManager.backupWorld(world);
                        sender.sendMessage("Started backup for world: " + worldName);
                    } else {
                        sender.sendMessage("World not found: " + worldName);
                    }
                } else {
                    sender.sendMessage("Usage: /backup start <world>");
                }
                break;
            case "list":
                if (!sender.hasPermission("hynsebackup.list")) {
                    sender.sendMessage("You do not have permission to use this command.");
                    return true;
                }

                File backupFolder = new File(backupManager.plugin.getDataFolder(), "backup");
                File[] worldFolders = backupFolder.listFiles(File::isDirectory);

                if (worldFolders != null) {
                    sender.sendMessage("Hynse Backup -----------");
                    for (File worldFolder : worldFolders) {
                        sender.sendMessage("  " + worldFolder.getName());
                        File[] backupFiles = worldFolder.listFiles(file -> file.getName().endsWith(".tar.zst"));
                        if (backupFiles != null) {
                            Arrays.sort(backupFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                            for (File backupFile : backupFiles) {
                                sender.sendMessage("           -  " + backupFile.getName());
                            }
                        }
                    }
                    sender.sendMessage("--------------------------------");
                } else {
                    sender.sendMessage("No backups found.");
                }
                break;
            default:
                return false;
        }

        return true;
    }
}
