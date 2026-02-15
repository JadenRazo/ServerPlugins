package net.serverplugins.admin.reset;

public class ResetResult {
    private final boolean success;
    private final String details;
    private final String error;

    public ResetResult(boolean success, String details, String error) {
        this.success = success;
        this.details = details;
        this.error = error;
    }

    public static ResetResult success(String details) {
        return new ResetResult(true, details, null);
    }

    public static ResetResult failure(String error) {
        return new ResetResult(false, null, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getDetails() {
        return details;
    }

    public String getError() {
        return error;
    }
}
