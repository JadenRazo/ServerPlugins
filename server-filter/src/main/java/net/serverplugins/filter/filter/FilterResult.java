package net.serverplugins.filter.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.serverplugins.filter.data.WordCategory;

public class FilterResult {

    private final List<MatchedWord> matches;
    private final String originalMessage;

    public FilterResult(List<MatchedWord> matches, String originalMessage) {
        this.matches = matches != null ? new ArrayList<>(matches) : new ArrayList<>();
        this.originalMessage = originalMessage;
    }

    public boolean hasMatches() {
        return !matches.isEmpty();
    }

    public List<MatchedWord> getMatches() {
        return Collections.unmodifiableList(matches);
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public boolean containsCategory(WordCategory category) {
        return matches.stream().anyMatch(m -> m.category() == category);
    }

    public List<MatchedWord> getMatchesByCategory(WordCategory category) {
        return matches.stream().filter(m -> m.category() == category).toList();
    }

    public record MatchedWord(
            String matchedText,
            String originalWord,
            WordCategory category,
            int startIndex,
            int endIndex) {}
}
