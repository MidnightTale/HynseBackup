package xyz.hynse.hynsebackup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedList;
import java.util.Queue;

public class HynseBackup extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Queue<String> worldsToBackup = new LinkedList<>(getConfig().getStringList("whitelist-worlds"));

        Manager manager = new Manager(this); // Pass the plugin instance to the Manager
        manager.startNextBackup(worldsToBackup);
    }
}
