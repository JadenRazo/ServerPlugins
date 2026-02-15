package net.serverplugins.api.messages;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ColorSchemeTest {

    @Test
    @DisplayName("Should have correct color constants")
    void shouldHaveCorrectColorConstants() {
        assertThat(ColorScheme.ERROR).isEqualTo("<red>");
        assertThat(ColorScheme.SUCCESS).isEqualTo("<green>");
        assertThat(ColorScheme.WARNING).isEqualTo("<yellow>");
        assertThat(ColorScheme.INFO).isEqualTo("<gray>");
        assertThat(ColorScheme.EMPHASIS).isEqualTo("<gold>");
        assertThat(ColorScheme.HIGHLIGHT).isEqualTo("<white>");
        assertThat(ColorScheme.SECONDARY).isEqualTo("<dark_gray>");
        assertThat(ColorScheme.COMMAND).isEqualTo("<aqua>");
    }

    @Test
    @DisplayName("All color constants should be non-null")
    void allColorConstantsShouldBeNonNull() {
        assertThat(ColorScheme.ERROR).isNotNull();
        assertThat(ColorScheme.SUCCESS).isNotNull();
        assertThat(ColorScheme.WARNING).isNotNull();
        assertThat(ColorScheme.INFO).isNotNull();
        assertThat(ColorScheme.EMPHASIS).isNotNull();
        assertThat(ColorScheme.HIGHLIGHT).isNotNull();
        assertThat(ColorScheme.SECONDARY).isNotNull();
        assertThat(ColorScheme.COMMAND).isNotNull();
    }

    @Test
    @DisplayName("Should have correct icon constants")
    void shouldHaveCorrectIconConstants() {
        assertThat(ColorScheme.CHECKMARK).isEqualTo("✓");
        assertThat(ColorScheme.CROSS).isEqualTo("✗");
        assertThat(ColorScheme.WARNING_ICON).isEqualTo("⚠");
        assertThat(ColorScheme.ARROW).isEqualTo("→");
        assertThat(ColorScheme.BULLET).isEqualTo("•");
        assertThat(ColorScheme.STAR).isEqualTo("★");
    }

    @Test
    @DisplayName("All icon constants should be non-null")
    void allIconConstantsShouldBeNonNull() {
        assertThat(ColorScheme.CHECKMARK).isNotNull();
        assertThat(ColorScheme.CROSS).isNotNull();
        assertThat(ColorScheme.WARNING_ICON).isNotNull();
        assertThat(ColorScheme.ARROW).isNotNull();
        assertThat(ColorScheme.BULLET).isNotNull();
        assertThat(ColorScheme.STAR).isNotNull();
    }

    @Test
    @DisplayName("Should wrap text with simple color tag")
    void shouldWrapTextWithSimpleColorTag() {
        String result = ColorScheme.wrap("Hello", "<red>");
        assertThat(result).isEqualTo("<red>Hello</red>");
    }

    @Test
    @DisplayName("Should wrap text with multi-word color tag")
    void shouldWrapTextWithMultiWordColorTag() {
        String result = ColorScheme.wrap("World", "<dark_gray>");
        assertThat(result).isEqualTo("<dark_gray>World</dark_gray>");
    }

    @Test
    @DisplayName("Should return empty string for null text")
    void shouldReturnEmptyStringForNullText() {
        String result = ColorScheme.wrap(null, "<red>");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty string for empty text")
    void shouldReturnEmptyStringForEmptyText() {
        String result = ColorScheme.wrap("", "<red>");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return text unchanged for null color")
    void shouldReturnTextUnchangedForNullColor() {
        String result = ColorScheme.wrap("Hello", null);
        assertThat(result).isEqualTo("Hello");
    }

    @Test
    @DisplayName("Should return text unchanged for empty color")
    void shouldReturnTextUnchangedForEmptyColor() {
        String result = ColorScheme.wrap("Hello", "");
        assertThat(result).isEqualTo("Hello");
    }

    @Test
    @DisplayName("Should wrap text with all predefined colors")
    void shouldWrapTextWithAllPredefinedColors() {
        assertThat(ColorScheme.wrap("text", ColorScheme.ERROR)).isEqualTo("<red>text</red>");
        assertThat(ColorScheme.wrap("text", ColorScheme.SUCCESS)).isEqualTo("<green>text</green>");
        assertThat(ColorScheme.wrap("text", ColorScheme.WARNING))
                .isEqualTo("<yellow>text</yellow>");
        assertThat(ColorScheme.wrap("text", ColorScheme.INFO)).isEqualTo("<gray>text</gray>");
        assertThat(ColorScheme.wrap("text", ColorScheme.EMPHASIS)).isEqualTo("<gold>text</gold>");
        assertThat(ColorScheme.wrap("text", ColorScheme.HIGHLIGHT))
                .isEqualTo("<white>text</white>");
        assertThat(ColorScheme.wrap("text", ColorScheme.SECONDARY))
                .isEqualTo("<dark_gray>text</dark_gray>");
        assertThat(ColorScheme.wrap("text", ColorScheme.COMMAND)).isEqualTo("<aqua>text</aqua>");
    }

    @Test
    @DisplayName("Should handle complex color tags")
    void shouldHandleComplexColorTags() {
        String result = ColorScheme.wrap("test", "<gradient:#FF0000:#00FF00>");
        assertThat(result).isEqualTo("<gradient:#FF0000:#00FF00>test</gradient:#FF0000:#00FF00>");
    }

    @Test
    @DisplayName("Constructor should throw UnsupportedOperationException")
    void constructorShouldThrowUnsupportedOperationException() throws NoSuchMethodException {
        Constructor<ColorScheme> constructor = ColorScheme.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class)
                .cause()
                .hasMessageContaining("ColorScheme is a utility class");
    }

    @Test
    @DisplayName("Should verify class is final")
    void shouldVerifyClassIsFinal() {
        assertThat(ColorScheme.class).isFinal();
    }
}
