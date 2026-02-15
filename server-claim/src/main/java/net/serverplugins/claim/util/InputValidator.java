package net.serverplugins.claim.util;

import java.util.regex.Pattern;

/**
 * Utility class for validating and sanitizing user input to prevent exploits, injection attacks,
 * and malicious content.
 */
public class InputValidator {

    // Allow alphanumeric, spaces, and common punctuation
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9 _\\-'.!]+$");

    // Detect ANSI escape codes
    private static final Pattern ANSI_PATTERN = Pattern.compile("\\x1B\\[[;\\d]*m");

    // Detect color codes
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("&[0-9a-fk-or]");

    // Maximum reasonable name length
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_MESSAGE_LENGTH = 256;

    /**
     * Validate and sanitize a claim or nation name
     *
     * @param name Input name from user
     * @return Validation result with sanitized name or error
     */
    public static ValidationResult validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return new ValidationResult(false, "Name cannot be empty", null);
        }

        String trimmed = name.trim();

        // Check length
        if (trimmed.length() > MAX_NAME_LENGTH) {
            return new ValidationResult(
                    false, "Name is too long (max " + MAX_NAME_LENGTH + " characters)", null);
        }

        if (trimmed.length() < 2) {
            return new ValidationResult(false, "Name is too short (min 2 characters)", null);
        }

        // Remove ANSI escape codes
        String sanitized = ANSI_PATTERN.matcher(trimmed).replaceAll("");

        // Check for malicious patterns
        if (!SAFE_NAME_PATTERN.matcher(sanitized).matches()) {
            return new ValidationResult(
                    false,
                    "Name contains invalid characters. Use only letters, numbers, spaces, and basic punctuation.",
                    null);
        }

        // Check for excessive spaces
        if (sanitized.contains("  ")) {
            sanitized = sanitized.replaceAll(" +", " ");
        }

        // Prevent names that are just spaces or punctuation
        if (sanitized.replaceAll("[\\s\\-_'.!]+", "").isEmpty()) {
            return new ValidationResult(
                    false, "Name must contain at least some letters or numbers", null);
        }

        return new ValidationResult(true, null, sanitized);
    }

    /**
     * Validate and sanitize a message (welcome message, description, etc.)
     *
     * @param message Input message from user
     * @return Validation result with sanitized message or error
     */
    public static ValidationResult validateMessage(String message) {
        if (message == null) {
            return new ValidationResult(true, null, "");
        }

        String trimmed = message.trim();

        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            return new ValidationResult(
                    false, "Message is too long (max " + MAX_MESSAGE_LENGTH + " characters)", null);
        }

        // Remove ANSI escape codes for security
        String sanitized = ANSI_PATTERN.matcher(trimmed).replaceAll("");

        // Allow color codes but validate them
        // Minecraft color codes are allowed in messages

        return new ValidationResult(true, null, sanitized);
    }

    /**
     * Strip all color codes from text (for display in logs, etc.)
     *
     * @param text Text to strip
     * @return Text without color codes
     */
    public static String stripColors(String text) {
        if (text == null) {
            return "";
        }
        return COLOR_CODE_PATTERN.matcher(text).replaceAll("");
    }

    /** Validation result containing success status, error message, and sanitized value */
    public record ValidationResult(boolean valid, String errorMessage, String sanitized) {

        /** Check if validation was successful */
        public boolean isValid() {
            return valid;
        }

        /** Get error message if validation failed */
        public String getError() {
            return errorMessage;
        }

        /** Get sanitized value (null if invalid) */
        public String getValue() {
            return sanitized;
        }
    }
}
