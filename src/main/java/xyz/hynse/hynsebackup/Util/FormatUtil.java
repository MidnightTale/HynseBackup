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
    public static String formatTime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1);
        long milliseconds = millis % TimeUnit.SECONDS.toMillis(1);

        if (hours > 0) {
            return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds);
        } else if (minutes > 0) {
            return String.format("%d:%02d.%03d", minutes, seconds, milliseconds);
        } else {
            return String.format("%d.%03d", seconds, milliseconds);
        }
    }
}
