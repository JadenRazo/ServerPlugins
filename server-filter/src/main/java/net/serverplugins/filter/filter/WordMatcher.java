package net.serverplugins.filter.filter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.serverplugins.filter.data.WordCategory;

public class WordMatcher {

    private final NormalizationEngine normalizer;
    private final WordListManager wordListManager;
    private final Map<WordCategory, Set<Pattern>> compiledPatterns =
            new EnumMap<>(WordCategory.class);

    public WordMatcher(NormalizationEngine normalizer, WordListManager wordListManager) {
        this.normalizer = normalizer;
        this.wordListManager = wordListManager;
        for (WordCategory category : WordCategory.values()) {
            compiledPatterns.put(category, new HashSet<>());
        }
    }

    public void compilePatterns() {
        compiledPatterns.values().forEach(Set::clear);

        for (WordCategory category : WordCategory.values()) {
            Set<Pattern> patterns = compiledPatterns.get(category);

            // Compile word patterns with word boundaries
            for (String word : wordListManager.getWords(category)) {
                String escaped = Pattern.quote(word);
                // Match as whole word or as part of a larger word
                Pattern pattern =
                        Pattern.compile(
                                "(?:^|\\s|[^a-z])" + escaped + "(?:$|\\s|[^a-z])|" + escaped,
                                Pattern.CASE_INSENSITIVE);
                patterns.add(pattern);
            }

            // Add custom regex patterns from config
            patterns.addAll(wordListManager.getPatterns(category));
        }
    }

    public FilterResult match(String message, Set<WordCategory> categoriesToCheck) {
        if (message == null || message.isEmpty()) {
            return new FilterResult(Collections.emptyList(), message);
        }

        String normalized = normalizer.normalize(message);
        String normalizedForTracking = normalizer.normalizeForDisplay(message);
        List<FilterResult.MatchedWord> matches = new ArrayList<>();

        for (WordCategory category : categoriesToCheck) {
            // First check direct word matches in normalized text
            for (String word : wordListManager.getWords(category)) {
                // Always require word boundaries to prevent false positives
                // This ensures "ass" doesn't match "class", "hell" doesn't match "hello", etc.
                if (matchesAsWholeWord(normalized, word)) {
                    // Check if whitelisted
                    if (!isPartOfWhitelistedWord(message.toLowerCase(), word)) {
                        // Find position in the normalized-for-tracking version
                        int index = normalizedForTracking.indexOf(word);
                        matches.add(
                                new FilterResult.MatchedWord(
                                        word,
                                        word,
                                        category,
                                        Math.max(0, index),
                                        Math.max(0, index) + word.length()));
                    }
                }
            }

            // Then check regex patterns
            for (Pattern pattern : wordListManager.getPatterns(category)) {
                Matcher matcher = pattern.matcher(normalized);
                while (matcher.find()) {
                    String matched = matcher.group();
                    if (!isPartOfWhitelistedWord(message.toLowerCase(), matched)) {
                        matches.add(
                                new FilterResult.MatchedWord(
                                        matched,
                                        matched,
                                        category,
                                        matcher.start(),
                                        matcher.end()));
                    }
                }
            }
        }

        return new FilterResult(matches, message);
    }

    private boolean isPartOfWhitelistedWord(String message, String matchedWord) {
        for (String whitelisted : wordListManager.getWhitelistedWords()) {
            if (message.contains(whitelisted) && whitelisted.contains(matchedWord)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAsWholeWord(String text, String word) {
        int index = 0;
        while ((index = text.indexOf(word, index)) >= 0) {
            boolean startBoundary = index == 0 || !Character.isLetter(text.charAt(index - 1));
            boolean endBoundary =
                    index + word.length() >= text.length()
                            || !Character.isLetter(text.charAt(index + word.length()));

            if (startBoundary && endBoundary) {
                return true;
            }
            index++;
        }
        return false;
    }

    public boolean containsBlocked(String message, Set<WordCategory> categories) {
        return match(message, categories).hasMatches();
    }
}
