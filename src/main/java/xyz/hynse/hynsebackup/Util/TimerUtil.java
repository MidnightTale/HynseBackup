package xyz.hynse.hynsebackup.Util;

import java.time.Duration;
import java.time.Instant;

public class TimerUtil {

    private static Instant start;
    private static Instant end;

    public static void start() {
        start = Instant.now();
    }

    public static void stop() {
        end = Instant.now();
    }

    public String getElapsedTime() {
        if (start == null || end == null) {
            throw new IllegalStateException("Must call start() and stop() before calling getElapsedTime()");
        }

        Duration duration = Duration.between(start, end);
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        long seconds = duration.minusHours(hours).minusMinutes(minutes).getSeconds();
        long millis = duration.minusHours(hours).minusMinutes(minutes).minusSeconds(seconds).toMillis();

        return String.format("%d hours, %d minutes, %d seconds, %d milliseconds", hours, minutes, seconds, millis);
    }
}