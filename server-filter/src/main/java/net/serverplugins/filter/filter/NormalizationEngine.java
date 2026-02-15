package net.serverplugins.filter.filter;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class NormalizationEngine {

    private static final Map<Character, Character> LEET_MAP = new HashMap<>();
    private static final Map<Character, Character> HOMOGLYPH_MAP = new HashMap<>();
    private static final Pattern ZALGO_PATTERN = Pattern.compile("[\\u0300-\\u036f\\u0489]");
    private static final Pattern SEPARATOR_PATTERN =
            Pattern.compile("[.\\-_*#@!$%^&()+=\\[\\]{}|\\\\:;\"'<>,?/~`\\s]+");

    static {
        // L33t speak substitutions
        LEET_MAP.put('@', 'a');
        LEET_MAP.put('4', 'a');
        LEET_MAP.put('8', 'b');
        LEET_MAP.put('3', 'e');
        LEET_MAP.put('1', 'i');
        LEET_MAP.put('!', 'i');
        LEET_MAP.put('|', 'i');
        LEET_MAP.put('0', 'o');
        LEET_MAP.put('5', 's');
        LEET_MAP.put('$', 's');
        LEET_MAP.put('7', 't');
        LEET_MAP.put('+', 't');
        LEET_MAP.put('2', 'z');
        LEET_MAP.put('9', 'g');
        LEET_MAP.put('6', 'g');

        // Homoglyphs (Cyrillic, Greek, and other lookalikes)
        // Cyrillic
        HOMOGLYPH_MAP.put('\u0430', 'a'); // а
        HOMOGLYPH_MAP.put('\u0435', 'e'); // е
        HOMOGLYPH_MAP.put('\u0456', 'i'); // і
        HOMOGLYPH_MAP.put('\u043e', 'o'); // о
        HOMOGLYPH_MAP.put('\u0440', 'p'); // р
        HOMOGLYPH_MAP.put('\u0441', 'c'); // с
        HOMOGLYPH_MAP.put('\u0443', 'y'); // у
        HOMOGLYPH_MAP.put('\u0445', 'x'); // х
        HOMOGLYPH_MAP.put('\u0410', 'a'); // А
        HOMOGLYPH_MAP.put('\u0412', 'b'); // В
        HOMOGLYPH_MAP.put('\u0415', 'e'); // Е
        HOMOGLYPH_MAP.put('\u041a', 'k'); // К
        HOMOGLYPH_MAP.put('\u041c', 'm'); // М
        HOMOGLYPH_MAP.put('\u041d', 'h'); // Н
        HOMOGLYPH_MAP.put('\u041e', 'o'); // О
        HOMOGLYPH_MAP.put('\u0420', 'p'); // Р
        HOMOGLYPH_MAP.put('\u0421', 'c'); // С
        HOMOGLYPH_MAP.put('\u0422', 't'); // Т
        HOMOGLYPH_MAP.put('\u0425', 'x'); // Х

        // Greek
        HOMOGLYPH_MAP.put('\u03b1', 'a'); // α
        HOMOGLYPH_MAP.put('\u03b5', 'e'); // ε
        HOMOGLYPH_MAP.put('\u03b9', 'i'); // ι
        HOMOGLYPH_MAP.put('\u03bf', 'o'); // ο
        HOMOGLYPH_MAP.put('\u03c1', 'p'); // ρ
        HOMOGLYPH_MAP.put('\u03c5', 'u'); // υ

        // Special characters
        HOMOGLYPH_MAP.put('\u00e0', 'a'); // à
        HOMOGLYPH_MAP.put('\u00e1', 'a'); // á
        HOMOGLYPH_MAP.put('\u00e2', 'a'); // â
        HOMOGLYPH_MAP.put('\u00e3', 'a'); // ã
        HOMOGLYPH_MAP.put('\u00e4', 'a'); // ä
        HOMOGLYPH_MAP.put('\u00e8', 'e'); // è
        HOMOGLYPH_MAP.put('\u00e9', 'e'); // é
        HOMOGLYPH_MAP.put('\u00ea', 'e'); // ê
        HOMOGLYPH_MAP.put('\u00eb', 'e'); // ë
        HOMOGLYPH_MAP.put('\u00ec', 'i'); // ì
        HOMOGLYPH_MAP.put('\u00ed', 'i'); // í
        HOMOGLYPH_MAP.put('\u00ee', 'i'); // î
        HOMOGLYPH_MAP.put('\u00ef', 'i'); // ï
        HOMOGLYPH_MAP.put('\u00f2', 'o'); // ò
        HOMOGLYPH_MAP.put('\u00f3', 'o'); // ó
        HOMOGLYPH_MAP.put('\u00f4', 'o'); // ô
        HOMOGLYPH_MAP.put('\u00f5', 'o'); // õ
        HOMOGLYPH_MAP.put('\u00f6', 'o'); // ö
        HOMOGLYPH_MAP.put('\u00f9', 'u'); // ù
        HOMOGLYPH_MAP.put('\u00fa', 'u'); // ú
        HOMOGLYPH_MAP.put('\u00fb', 'u'); // û
        HOMOGLYPH_MAP.put('\u00fc', 'u'); // ü
    }

    public String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String result = input.toLowerCase();
        result = removeZalgo(result);
        result = normalizeHomoglyphs(result);
        result = normalizeLeetSpeak(result);
        result = removeRepeatedCharacters(result);
        result = removeSeparators(result);

        return result;
    }

    public String normalizeForDisplay(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String result = input.toLowerCase();
        result = removeZalgo(result);
        result = normalizeHomoglyphs(result);
        result = normalizeLeetSpeak(result);
        return result;
    }

    private String removeZalgo(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return ZALGO_PATTERN.matcher(normalized).replaceAll("");
    }

    private String normalizeHomoglyphs(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            Character replacement = HOMOGLYPH_MAP.get(c);
            sb.append(replacement != null ? replacement : c);
        }
        return sb.toString();
    }

    private String normalizeLeetSpeak(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            Character replacement = LEET_MAP.get(c);
            sb.append(replacement != null ? replacement : c);
        }
        return sb.toString();
    }

    private String removeRepeatedCharacters(String input) {
        if (input.length() < 3) {
            return input;
        }

        StringBuilder sb = new StringBuilder();
        char lastChar = 0;
        int count = 0;

        for (char c : input.toCharArray()) {
            if (c == lastChar) {
                count++;
                if (count <= 2) {
                    sb.append(c);
                }
            } else {
                lastChar = c;
                count = 1;
                sb.append(c);
            }
        }

        return sb.toString();
    }

    private String removeSeparators(String input) {
        return SEPARATOR_PATTERN.matcher(input).replaceAll("");
    }
}
