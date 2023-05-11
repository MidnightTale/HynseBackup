package xyz.hynse.hynsebackup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class BackupCommandExecutor implements CommandExecutor {
    private final BackupManager backupManager;

    public BackupCommandExecutor(BackupManager backupManager) {
        this.backupManager = backupManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
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

        return true;
    }
}
