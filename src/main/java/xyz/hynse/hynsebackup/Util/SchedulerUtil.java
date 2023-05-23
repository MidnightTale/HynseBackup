package xyz.hynse.hynsebackup.Util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public class SchedulerUtil {

    private static Boolean IS_FOLIA = null;

    private static boolean tryFolia() {
        try {
            Bukkit.getAsyncScheduler();
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static Boolean isFolia() {
        if (IS_FOLIA == null) IS_FOLIA = tryFolia();
        return IS_FOLIA;
    }

    public static void runAsyncFixRateScheduler(Plugin plugin, Runnable runnable, int initialDelayTicks, int periodTicks) {
        if (isFolia()) {
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, (task) -> runnable.run(), initialDelayTicks, periodTicks, TimeUnit.SECONDS);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, initialDelayTicks * 20L, periodTicks * 20L);
        }
    }
    public static void runAsyncnow(Plugin plugin, Runnable runnable) {
        if (isFolia()) {
            Bukkit.getAsyncScheduler().runNow(plugin, (task) -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }
    public static void runAsyncDelay(Plugin plugin, Runnable runnable, int periodTicks) {
        if (isFolia()) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, (task) -> runnable.run(), periodTicks, TimeUnit.SECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, periodTicks * 20L);
        }
    }

}