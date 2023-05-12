package xyz.hynse.hynsebackup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import xyz.hynse.hynsebackup.Util.MiscUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BackupCommandExecutor implements CommandExecutor, TabCompleter {
    private final BackupManager backupManager;
    private final List<String> whitelistedWorlds;

    public BackupCommandExecutor(BackupManager backupManager, FileConfiguration config) {
        this.backupManager = backupManager;
        this.whitelistedWorlds = config.getStringList("whitelist_world");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                if (args.length == 2) {
                    if (!sender.hasPermission("hynsebackup.start")) {
                        sender.sendMessage("You do not have permission to use this command.");
                        return true;
                    }

                    String worldName = args[1];
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        backupManager.backupWorld(world, sender);
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
                    sender.sendMessage("Hynse Backup -------------------");
                    long totalSize = 0;
                    for (File worldFolder : worldFolders) {
                        sender.sendMessage("  " + worldFolder.getName());
                        File[] backupFiles = worldFolder.listFiles(file -> file.getName().endsWith(".tar.zst") || file.getName().endsWith(".zip"));
                        if (backupFiles != null) {
                            Arrays.sort(backupFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                            for (File backupFile : backupFiles) {
                                long fileSize = backupFile.length();
                                totalSize += fileSize;
                                String humanReadableSize = MiscUtil.humanReadableByteCountBin(fileSize);
                                sender.sendMessage("     -  " + backupFile.getName() + " (" + humanReadableSize + ")");
                            }
                        }
                    }
                    String totalHumanReadableSize = MiscUtil.humanReadableByteCountBin(totalSize);
                    sender.sendMessage("Total size: " + totalHumanReadableSize);
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("start", "list");
            return subcommands.stream().filter(subcommand -> subcommand.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            if (!sender.hasPermission("hynsebackup.start")) {
                return new ArrayList<>();
            }
            return whitelistedWorlds.stream()
                    .filter(world -> world.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            if (!sender.hasPermission("hynsebackup.list")) {
                return new ArrayList<>();
            }
            File backupFolder = new File(backupManager.plugin.getDataFolder(), "backup");
            File[] worldFolders = backupFolder.listFiles(File::isDirectory);

            if (worldFolders != null) {
                List<String> worldNames = Arrays.stream(worldFolders)
                        .map(File::getName)
                        .collect(Collectors.toList());
                return worldNames.stream()
                        .filter(world -> world.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }
}