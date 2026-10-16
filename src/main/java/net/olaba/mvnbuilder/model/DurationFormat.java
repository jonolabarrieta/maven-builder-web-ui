package net.olaba.mvnbuilder.model;

/**
 * Formats millisecond durations for display in the build history screen.
 */
public final class DurationFormat {

    private DurationFormat() {
    }

    /**
     * Formats a duration in milliseconds as a short human-readable string.
     *
     * @param durationMs duration in milliseconds
     * @return e.g. "850 ms", "1.5 s", "2m 3s", "1h 4m"
     */
    public static String human(final long durationMs) {
        if (durationMs < 1000) {
            return Math.max(0, durationMs) + " ms";
        }
        final long totalSeconds = durationMs / 1000;
        final long hours = totalSeconds / 3600;
        final long minutes = (totalSeconds % 3600) / 60;
        final long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return String.format("%.1f s", durationMs / 1000.0);
    }
}
