package net.serverplugins.filter.filter;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import net.serverplugins.filter.ServerFilter;
import net.serverplugins.filter.data.WordCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class WordListManager {

    private final ServerFilter plugin;
    private final Map<WordCategory, Set<String>> wordLists = new EnumMap<>(WordCategory.class);
    private final Map<WordCategory, Set<Pattern>> patternLists = new EnumMap<>(WordCategory.class);
    private final Set<String> whitelistedWords = new HashSet<>();

    public WordListManager(ServerFilter plugin) {
        this.plugin = plugin;
        for (WordCategory category : WordCategory.values()) {
            wordLists.put(category, new HashSet<>());
            patternLists.put(category, new HashSet<>());
        }
    }

    public void loadWordLists() {
        wordLists.values().forEach(Set::clear);
        patternLists.values().forEach(Set::clear);
        whitelistedWords.clear();

        loadCategoryFile("slurs.yml", WordCategory.SLURS);
        loadCategoryFile("extreme.yml", WordCategory.EXTREME);
        loadCategoryFile("moderate.yml", WordCategory.MODERATE);
        loadCategoryFile("mild.yml", WordCategory.MILD);

        loadWhitelist();

        plugin.getLogger()
                .info(
                        "Loaded word lists: "
                                + "SLURS="
                                + wordLists.get(WordCategory.SLURS).size()
                                + ", "
                                + "EXTREME="
                                + wordLists.get(WordCategory.EXTREME).size()
                                + ", "
                                + "MODERATE="
                                + wordLists.get(WordCategory.MODERATE).size()
                                + ", "
                                + "MILD="
                                + wordLists.get(WordCategory.MILD).size()
                                + " | Whitelisted="
                                + whitelistedWords.size());
    }

    private void loadCategoryFile(String fileName, WordCategory category) {
        File file = new File(plugin.getDataFolder(), "wordlists/" + fileName);

        if (!file.exists()) {
            plugin.saveResource("wordlists/" + fileName, false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Load default from jar if exists
        InputStream defaultStream = plugin.getResource("wordlists/" + fileName);
        if (defaultStream != null) {
            FileConfiguration defaultConfig =
                    YamlConfiguration.loadConfiguration(
                            new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }

        List<String> words = config.getStringList("words");
        for (String word : words) {
            if (word != null && !word.isEmpty()) {
                wordLists.get(category).add(word.toLowerCase());
            }
        }

        List<String> patterns = config.getStringList("patterns");
        for (String patternStr : patterns) {
            if (patternStr != null && !patternStr.isEmpty()) {
                try {
                    Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                    patternLists.get(category).add(pattern);
                } catch (Exception e) {
                    plugin.getLogger()
                            .warning("Invalid regex pattern in " + fileName + ": " + patternStr);
                }
            }
        }
    }

    private void loadWhitelist() {
        FileConfiguration config = plugin.getConfig();
        List<String> whitelist = config.getStringList("whitelist");
        for (String word : whitelist) {
            if (word != null && !word.isEmpty()) {
                whitelistedWords.add(word.toLowerCase());
            }
        }
    }

    public Set<String> getWords(WordCategory category) {
        return Collections.unmodifiableSet(wordLists.get(category));
    }

    public Set<Pattern> getPatterns(WordCategory category) {
        return Collections.unmodifiableSet(patternLists.get(category));
    }

    public Set<String> getWhitelistedWords() {
        return Collections.unmodifiableSet(whitelistedWords);
    }

    public boolean isWhitelisted(String word) {
        return whitelistedWords.contains(word.toLowerCase());
    }

    public int getTotalWordCount() {
        return wordLists.values().stream().mapToInt(Set::size).sum();
    }

    public int getTotalPatternCount() {
        return patternLists.values().stream().mapToInt(Set::size).sum();
    }
}
