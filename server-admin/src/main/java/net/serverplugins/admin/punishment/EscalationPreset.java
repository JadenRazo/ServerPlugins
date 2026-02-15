package net.serverplugins.admin.punishment;

public class EscalationPreset {
    private final PunishmentType type;
    private final Long durationMs;
    private final String reason;

    public EscalationPreset(PunishmentType type, Long durationMs, String reason) {
        this.type = type;
        this.durationMs = durationMs;
        this.reason = reason;
    }

    public PunishmentType getType() {
        return type;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getReason() {
        return reason;
    }

    public boolean isPermanent() {
        return type.hasDuration() && durationMs == null;
    }

    public String getFormattedDuration() {
        if (durationMs == null) {
            return "Permanent";
        }
        return formatDuration(durationMs);
    }

    public static String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";

        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d" + (hours % 24 > 0 ? " " + (hours % 24) + "h" : "");
        } else if (hours > 0) {
            return hours + "h" + (minutes % 60 > 0 ? " " + (minutes % 60) + "m" : "");
        } else if (minutes > 0) {
            return minutes + "m" + (seconds % 60 > 0 ? " " + (seconds % 60) + "s" : "");
        } else {
            return seconds + "s";
        }
    }

    public static Long parseDuration(String duration) {
        if (duration == null || duration.isEmpty() || duration.equalsIgnoreCase("permanent")) {
            return null;
        }

        duration = duration.toLowerCase().trim();
        long totalMs = 0;
        StringBuilder number = new StringBuilder();

        for (char c : duration.toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else if (number.length() > 0) {
                long value = Long.parseLong(number.toString());
                totalMs +=
                        switch (c) {
                            case 's' -> value * 1000;
                            case 'm' -> value * 60 * 1000;
                            case 'h' -> value * 60 * 60 * 1000;
                            case 'd' -> value * 24 * 60 * 60 * 1000;
                            case 'w' -> value * 7 * 24 * 60 * 60 * 1000;
                            default -> 0;
                        };
                number = new StringBuilder();
            }
        }

        if (number.length() > 0) {
            totalMs += Long.parseLong(number.toString()) * 60 * 1000;
        }

        return totalMs > 0 ? totalMs : null;
    }
}
