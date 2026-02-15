package net.serverplugins.api.messages;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaceholderTest {

    @Test
    @DisplayName("Should create placeholder with string value")
    void shouldCreatePlaceholderWithString() {
        Placeholder placeholder = Placeholder.of("player", "Steve");

        assertThat(placeholder.getKey()).isEqualTo("player");
        assertThat(placeholder.getValue()).isEqualTo("Steve");
    }

    @Test
    @DisplayName("Should convert null string value to empty string")
    void shouldConvertNullStringToEmpty() {
        Placeholder placeholder = Placeholder.of("player", (String) null);

        assertThat(placeholder.getKey()).isEqualTo("player");
        assertThat(placeholder.getValue()).isEmpty();
    }

    @Test
    @DisplayName("Should throw NPE when key is null")
    void shouldThrowNPEForNullKey() {
        assertThatThrownBy(() -> Placeholder.of(null, "value"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("key cannot be null");
    }

    @Test
    @DisplayName("Should format integer with thousand separators")
    void shouldFormatIntegerWithThousandSeparators() {
        Placeholder placeholder = Placeholder.of("amount", 1000);
        assertThat(placeholder.getValue()).isEqualTo("1,000");

        placeholder = Placeholder.of("amount", 1234567);
        assertThat(placeholder.getValue()).isEqualTo("1,234,567");

        placeholder = Placeholder.of("amount", 100);
        assertThat(placeholder.getValue()).isEqualTo("100");
    }

    @Test
    @DisplayName("Should format long with thousand separators")
    void shouldFormatLongWithThousandSeparators() {
        Placeholder placeholder = Placeholder.of("amount", 1000L);
        assertThat(placeholder.getValue()).isEqualTo("1,000");

        placeholder = Placeholder.of("amount", 999999999L);
        assertThat(placeholder.getValue()).isEqualTo("999,999,999");
    }

    @Test
    @DisplayName("Should format double with 2 decimal places and thousand separators")
    void shouldFormatDoubleWithTwoDecimals() {
        Placeholder placeholder = Placeholder.of("balance", 1234.56);
        assertThat(placeholder.getValue()).isEqualTo("1,234.56");

        placeholder = Placeholder.of("balance", 1000.0);
        assertThat(placeholder.getValue()).isEqualTo("1,000.00");

        placeholder = Placeholder.of("balance", 99.99);
        assertThat(placeholder.getValue()).isEqualTo("99.99");
    }

    @Test
    @DisplayName("Should format float with 2 decimal places and thousand separators")
    void shouldFormatFloatWithTwoDecimals() {
        Placeholder placeholder = Placeholder.of("percentage", 123.45f);
        assertThat(placeholder.getValue()).isEqualTo("123.45");

        placeholder = Placeholder.of("percentage", 1000.5f);
        assertThat(placeholder.getValue()).isEqualTo("1,000.50");
    }

    @Test
    @DisplayName("Should format boolean as string")
    void shouldFormatBooleanAsString() {
        Placeholder placeholder = Placeholder.of("enabled", true);
        assertThat(placeholder.getValue()).isEqualTo("true");

        placeholder = Placeholder.of("enabled", false);
        assertThat(placeholder.getValue()).isEqualTo("false");
    }

    @Test
    @DisplayName("Should handle object value with toString")
    void shouldHandleObjectValueWithToString() {
        Object obj =
                new Object() {
                    @Override
                    public String toString() {
                        return "custom-object";
                    }
                };

        Placeholder placeholder = Placeholder.of("object", obj);
        assertThat(placeholder.getValue()).isEqualTo("custom-object");
    }

    @Test
    @DisplayName("Should convert null object to empty string")
    void shouldConvertNullObjectToEmpty() {
        Placeholder placeholder = Placeholder.of("object", (Object) null);
        assertThat(placeholder.getValue()).isEmpty();
    }

    @Test
    @DisplayName("Should replace placeholder in curly braces format")
    void shouldReplaceCurlyBracesFormat() {
        Placeholder placeholder = Placeholder.of("player", "Steve");
        String text = "Welcome {player}!";

        String result = placeholder.replace(text);
        assertThat(result).isEqualTo("Welcome Steve!");
    }

    @Test
    @DisplayName("Should replace placeholder in percent format")
    void shouldReplacePercentFormat() {
        Placeholder placeholder = Placeholder.of("player", "Steve");
        String text = "Welcome %player%!";

        String result = placeholder.replace(text);
        assertThat(result).isEqualTo("Welcome Steve!");
    }

    @Test
    @DisplayName("Should replace both placeholder formats in same text")
    void shouldReplaceBothFormatsInSameText() {
        Placeholder placeholder = Placeholder.of("player", "Steve");
        String text = "Hello {player}, your name is %player%";

        String result = placeholder.replace(text);
        assertThat(result).isEqualTo("Hello Steve, your name is Steve");
    }

    @Test
    @DisplayName("Should replace multiple occurrences")
    void shouldReplaceMultipleOccurrences() {
        Placeholder placeholder = Placeholder.of("item", "diamond");
        String text = "You have {item}, {item}, and {item}";

        String result = placeholder.replace(text);
        assertThat(result).isEqualTo("You have diamond, diamond, and diamond");
    }

    @Test
    @DisplayName("Should handle null text in replace")
    void shouldHandleNullTextInReplace() {
        Placeholder placeholder = Placeholder.of("key", "value");
        assertThat(placeholder.replace(null)).isNull();
    }

    @Test
    @DisplayName("Should handle empty text in replace")
    void shouldHandleEmptyTextInReplace() {
        Placeholder placeholder = Placeholder.of("key", "value");
        assertThat(placeholder.replace("")).isEmpty();
    }

    @Test
    @DisplayName("Should replace all placeholders with replaceAll")
    void shouldReplaceAllPlaceholders() {
        String text = "Player {player} has {amount} coins";
        String result =
                Placeholder.replaceAll(
                        text, Placeholder.of("player", "Steve"), Placeholder.of("amount", 1000));

        assertThat(result).isEqualTo("Player Steve has 1,000 coins");
    }

    @Test
    @DisplayName("Should handle null placeholders array in replaceAll")
    void shouldHandleNullPlaceholdersArrayInReplaceAll() {
        String text = "Hello {player}";
        String result = Placeholder.replaceAll(text, (Placeholder[]) null);

        assertThat(result).isEqualTo(text);
    }

    @Test
    @DisplayName("Should handle null placeholder in array")
    void shouldHandleNullPlaceholderInArray() {
        String text = "Player {player} has {amount} coins";
        String result =
                Placeholder.replaceAll(
                        text,
                        Placeholder.of("player", "Steve"),
                        null,
                        Placeholder.of("amount", 500));

        assertThat(result).isEqualTo("Player Steve has 500 coins");
    }

    @Test
    @DisplayName("Should handle null text in replaceAll")
    void shouldHandleNullTextInReplaceAll() {
        String result = Placeholder.replaceAll(null, Placeholder.of("key", "value"));
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle empty text in replaceAll")
    void shouldHandleEmptyTextInReplaceAll() {
        String result = Placeholder.replaceAll("", Placeholder.of("key", "value"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should implement toString correctly")
    void shouldImplementToString() {
        Placeholder placeholder = Placeholder.of("balance", 1234.56);
        assertThat(placeholder.toString()).isEqualTo("{balance=1,234.56}");
    }

    @Test
    @DisplayName("Should implement equals correctly")
    void shouldImplementEquals() {
        Placeholder p1 = Placeholder.of("player", "Steve");
        Placeholder p2 = Placeholder.of("player", "Steve");
        Placeholder p3 = Placeholder.of("player", "Alex");
        Placeholder p4 = Placeholder.of("name", "Steve");

        assertThat(p1).isEqualTo(p2);
        assertThat(p1).isNotEqualTo(p3);
        assertThat(p1).isNotEqualTo(p4);
        assertThat(p1).isNotEqualTo(null);
    }

    @Test
    @DisplayName("Should implement hashCode correctly")
    void shouldImplementHashCode() {
        Placeholder p1 = Placeholder.of("player", "Steve");
        Placeholder p2 = Placeholder.of("player", "Steve");

        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }

    @Test
    @DisplayName("Should maintain equals and hashCode contract")
    void shouldMaintainEqualsHashCodeContract() {
        Placeholder p1 = Placeholder.of("balance", 100.50);
        Placeholder p2 = Placeholder.of("balance", 100.50);

        if (p1.equals(p2)) {
            assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
        }
    }
}
