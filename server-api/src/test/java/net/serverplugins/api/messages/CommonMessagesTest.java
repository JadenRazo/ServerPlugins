package net.serverplugins.api.messages;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommonMessagesTest {

    @Test
    @DisplayName("All enum values should have non-null default messages")
    void allEnumValuesShouldHaveNonNullDefaults() {
        for (CommonMessages message : CommonMessages.values()) {
            assertThat(message.getDefault())
                    .as("Message %s should have non-null default", message.name())
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("Should access all enum values without NPE")
    void shouldAccessAllEnumValuesWithoutNPE() {
        assertThatCode(
                        () -> {
                            for (CommonMessages message : CommonMessages.values()) {
                                message.getDefault();
                                message.toString();
                            }
                        })
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should format message without placeholders")
    void shouldFormatMessageWithoutPlaceholders() {
        String result = CommonMessages.NO_PERMISSION.format();
        assertThat(result).isEqualTo(CommonMessages.NO_PERMISSION.getDefault());
    }

    @Test
    @DisplayName("Should format message with single placeholder")
    void shouldFormatMessageWithSinglePlaceholder() {
        String result = CommonMessages.INVALID_USAGE.format(Placeholder.of("usage", "/test <arg>"));
        assertThat(result).contains("/test <arg>");
        assertThat(result).doesNotContain("{usage}");
    }

    @Test
    @DisplayName("Should format message with multiple placeholders")
    void shouldFormatMessageWithMultiplePlaceholders() {
        String result =
                CommonMessages.SUCCESS.format(Placeholder.of("message", "Operation completed"));
        assertThat(result).contains("Operation completed");
        assertThat(result).doesNotContain("{message}");
    }

    @Test
    @DisplayName("NO_PERMISSION should contain expected text")
    void noPermissionShouldContainExpectedText() {
        assertThat(CommonMessages.NO_PERMISSION.getDefault()).containsIgnoringCase("permission");
    }

    @Test
    @DisplayName("PLAYERS_ONLY should contain expected text")
    void playersOnlyShouldContainExpectedText() {
        assertThat(CommonMessages.PLAYERS_ONLY.getDefault()).containsIgnoringCase("player");
    }

    @Test
    @DisplayName("CONSOLE_ONLY should contain expected text")
    void consoleOnlyShouldContainExpectedText() {
        assertThat(CommonMessages.CONSOLE_ONLY.getDefault()).containsIgnoringCase("console");
    }

    @Test
    @DisplayName("PLAYER_NOT_FOUND should contain expected text")
    void playerNotFoundShouldContainExpectedText() {
        assertThat(CommonMessages.PLAYER_NOT_FOUND.getDefault()).containsIgnoringCase("not found");
    }

    @Test
    @DisplayName("INVALID_USAGE should contain usage placeholder")
    void invalidUsageShouldContainUsagePlaceholder() {
        assertThat(CommonMessages.INVALID_USAGE.getDefault()).contains("{usage}");
    }

    @Test
    @DisplayName("INVALID_NUMBER should contain input placeholder")
    void invalidNumberShouldContainInputPlaceholder() {
        assertThat(CommonMessages.INVALID_NUMBER.getDefault()).contains("{input}");
    }

    @Test
    @DisplayName("SUCCESS should contain message placeholder")
    void successShouldContainMessagePlaceholder() {
        assertThat(CommonMessages.SUCCESS.getDefault()).contains("{message}");
    }

    @Test
    @DisplayName("ERROR should contain message placeholder")
    void errorShouldContainMessagePlaceholder() {
        assertThat(CommonMessages.ERROR.getDefault()).contains("{message}");
    }

    @Test
    @DisplayName("WARNING should contain message placeholder")
    void warningShouldContainMessagePlaceholder() {
        assertThat(CommonMessages.WARNING.getDefault()).contains("{message}");
    }

    @Test
    @DisplayName("INFO should contain message placeholder")
    void infoShouldContainMessagePlaceholder() {
        assertThat(CommonMessages.INFO.getDefault()).contains("{message}");
    }

    @Test
    @DisplayName("ON_COOLDOWN should contain time placeholder")
    void onCooldownShouldContainTimePlaceholder() {
        assertThat(CommonMessages.ON_COOLDOWN.getDefault()).contains("{time}");
    }

    @Test
    @DisplayName("INSUFFICIENT_FUNDS should contain amount placeholder")
    void insufficientFundsShouldContainAmountPlaceholder() {
        assertThat(CommonMessages.INSUFFICIENT_FUNDS.getDefault()).contains("{amount}");
    }

    @Test
    @DisplayName("SUCCESS should contain checkmark icon")
    void successShouldContainCheckmarkIcon() {
        assertThat(CommonMessages.SUCCESS.getDefault()).contains(ColorScheme.CHECKMARK);
    }

    @Test
    @DisplayName("ERROR should contain cross icon")
    void errorShouldContainCrossIcon() {
        assertThat(CommonMessages.ERROR.getDefault()).contains(ColorScheme.CROSS);
    }

    @Test
    @DisplayName("WARNING should contain warning icon")
    void warningShouldContainWarningIcon() {
        assertThat(CommonMessages.WARNING.getDefault()).contains(ColorScheme.WARNING_ICON);
    }

    @Test
    @DisplayName("INFO should contain arrow icon")
    void infoShouldContainArrowIcon() {
        assertThat(CommonMessages.INFO.getDefault()).contains(ColorScheme.ARROW);
    }

    @Test
    @DisplayName("Should format with numeric placeholders correctly")
    void shouldFormatWithNumericPlaceholders() {
        String result = CommonMessages.ON_COOLDOWN.format(Placeholder.of("time", "30 seconds"));
        assertThat(result).contains("30 seconds");
    }

    @Test
    @DisplayName("Should verify all expected enum values exist")
    void shouldVerifyAllExpectedEnumValuesExist() {
        assertThat(CommonMessages.values())
                .extracting(Enum::name)
                .contains(
                        "NO_PERMISSION",
                        "CONSOLE_ONLY",
                        "PLAYERS_ONLY",
                        "PLAYER_NOT_FOUND",
                        "PLAYER_OFFLINE",
                        "CANNOT_TARGET_SELF",
                        "INVALID_USAGE",
                        "INVALID_NUMBER",
                        "INVALID_PLAYER",
                        "INVALID_ARGUMENTS",
                        "TOO_FEW_ARGUMENTS",
                        "TOO_MANY_ARGUMENTS",
                        "SUCCESS",
                        "ERROR",
                        "WARNING",
                        "INFO",
                        "ON_COOLDOWN",
                        "PROCESSING",
                        "INSUFFICIENT_FUNDS",
                        "PURCHASE_SUCCESS",
                        "MONEY_ADDED",
                        "MONEY_REMOVED",
                        "WORLD_DISABLED",
                        "REGION_DISABLED",
                        "DATABASE_ERROR",
                        "DATA_LOADING",
                        "DATA_LOAD_FAILED",
                        "SOMETHING_WENT_WRONG",
                        "NOT_IMPLEMENTED",
                        "ACTION_CANCELLED");
    }

    @Test
    @DisplayName("Should handle null placeholders gracefully")
    void shouldHandleNullPlaceholdersGracefully() {
        String result = CommonMessages.SUCCESS.format((Placeholder[]) null);
        assertThat(result).isEqualTo(CommonMessages.SUCCESS.getDefault());
    }

    @Test
    @DisplayName("Should handle empty placeholder array")
    void shouldHandleEmptyPlaceholderArray() {
        String result = CommonMessages.SUCCESS.format();
        assertThat(result).isEqualTo(CommonMessages.SUCCESS.getDefault());
    }

    @Test
    @DisplayName("Database error should contain expected text")
    void databaseErrorShouldContainExpectedText() {
        assertThat(CommonMessages.DATABASE_ERROR.getDefault()).containsIgnoringCase("database");
    }

    @Test
    @DisplayName("Something went wrong should contain expected text")
    void somethingWentWrongShouldContainExpectedText() {
        assertThat(CommonMessages.SOMETHING_WENT_WRONG.getDefault())
                .containsIgnoringCase("something")
                .containsIgnoringCase("wrong");
    }
}
