package net.serverplugins.filter.filter;

import java.util.EnumSet;
import java.util.Set;
import net.serverplugins.filter.ServerFilter;
import net.serverplugins.filter.data.FilterLevel;
import net.serverplugins.filter.data.WordCategory;

public class MessageFilterService {

    private final ServerFilter plugin;
    private final NormalizationEngine normalizer;
    private final WordListManager wordListManager;
    private final WordMatcher wordMatcher;
    private String censorCharacter = "*";

    public MessageFilterService(ServerFilter plugin) {
        this.plugin = plugin;
        this.normalizer = new NormalizationEngine();
        this.wordListManager = new WordListManager(plugin);
        this.wordMatcher = new WordMatcher(normalizer, wordListManager);
    }

    public void initialize() {
        wordListManager.loadWordLists();
        wordMatcher.compilePatterns();
        censorCharacter = plugin.getConfig().getString("censor-character", "*");
    }

    public void reload() {
        initialize();
    }

    public String filterMessage(String originalMessage, FilterLevel level) {
        if (originalMessage == null || originalMessage.isEmpty()) {
            return originalMessage;
        }

        Set<WordCategory> toBlock = level.getBlockedCategories();
        FilterResult result = wordMatcher.match(originalMessage, toBlock);

        if (result.hasMatches()) {
            return censorMessage(originalMessage, result);
        }

        return originalMessage;
    }

    public boolean containsSlurs(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        FilterResult result = wordMatcher.match(message, EnumSet.of(WordCategory.SLURS));
        return result.hasMatches();
    }

    public FilterResult analyzeMessage(String message, Set<WordCategory> categories) {
        return wordMatcher.match(message, categories);
    }

    public FilterResult analyzeMessage(String message, FilterLevel level) {
        return wordMatcher.match(message, level.getBlockedCategories());
    }

    private String censorMessage(String original, FilterResult result) {
        String normalized = normalizer.normalizeForDisplay(original);
        StringBuilder censored = new StringBuilder(original);

        // Track which positions have been censored to avoid double-censoring
        boolean[] censoredPositions = new boolean[original.length()];

        for (FilterResult.MatchedWord match : result.getMatches()) {
            String wordToFind = match.matchedText().toLowerCase();

            // Find the word in the original message (case-insensitive)
            String lowerOriginal = original.toLowerCase();
            int searchStart = 0;

            while (searchStart < lowerOriginal.length()) {
                int index = findWordInMessage(lowerOriginal, normalized, wordToFind, searchStart);

                if (index < 0) break;

                int endIndex = Math.min(index + wordToFind.length(), original.length());

                // Check if already censored
                boolean alreadyCensored = false;
                for (int i = index; i < endIndex; i++) {
                    if (censoredPositions[i]) {
                        alreadyCensored = true;
                        break;
                    }
                }

                if (!alreadyCensored) {
                    // Censor this occurrence
                    String replacement = generateCensor(endIndex - index);
                    censored.replace(index, endIndex, replacement);

                    for (int i = index; i < endIndex; i++) {
                        censoredPositions[i] = true;
                    }
                }

                searchStart = endIndex;
            }
        }

        return censored.toString();
    }

    private int findWordInMessage(
            String lowerOriginal, String normalized, String word, int startIndex) {
        // First try to find in the original lowercase
        int index = lowerOriginal.indexOf(word, startIndex);
        if (index >= 0) {
            return index;
        }

        // If not found, try finding via normalized version
        // This handles cases where the word has bypasses like f.u.c.k
        // For now, return -1 and let the normalized matching handle it
        return -1;
    }

    private String generateCensor(int length) {
        return censorCharacter.repeat(Math.max(1, length));
    }

    public NormalizationEngine getNormalizer() {
        return normalizer;
    }

    public WordListManager getWordListManager() {
        return wordListManager;
    }

    public WordMatcher getWordMatcher() {
        return wordMatcher;
    }
}
