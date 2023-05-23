package xyz.hynse.hynsebackup.Util;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.concurrent.TimeUnit;

public class FormatUtil {
    public static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %cB", value / 1024.0, ci.current());
    }
    public static String formatTime(long seconds) {
        long hours = TimeUnit.SECONDS.toHours(seconds);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) % TimeUnit.HOURS.toMinutes(1);
        seconds = seconds % TimeUnit.MINUTES.toSeconds(1);

        if (hours > 0) {
            return String.format("%d hour", hours);
        } else if (minutes > 0) {
            return String.format("%d minute", minutes);
        } else {
            return String.format("%d second", seconds);
        }
    }
}
