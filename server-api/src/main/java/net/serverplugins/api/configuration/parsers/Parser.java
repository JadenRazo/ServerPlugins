package net.serverplugins.api.configuration.parsers;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import net.serverplugins.api.effects.CustomSound;
import net.serverplugins.api.messages.Message;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * Abstract base class for configuration parsers. Provides a registry system for type-safe config
 * loading.
 */
public abstract class Parser<T> {

    private static final Map<Class<?>, Parser<?>> PARSERS = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger("ServerAPI");
    private static boolean initialized = false;

    /** Register a parser for a specific type. */
    public static <T> void register(Class<T> type, Parser<T> parser) {
        PARSERS.put(type, parser);
    }

    /** Get a parser for a specific type. */
    @SuppressWarnings("unchecked")
    public static <T> Parser<T> getParser(Class<T> type) {
        return (Parser<T>) PARSERS.get(type);
    }

    /** Check if a parser exists for a type. */
    public static boolean exists(Class<?> type) {
        return PARSERS.containsKey(type);
    }

    /** Initialize all built-in parsers. */
    public static void initializeDefaults() {
        if (initialized) return;
        initialized = true;

        register(Location.class, LocationParser.getInstance());
        register(CustomSound.class, CustomSoundParser.getInstance());
        register(ItemStack.class, ItemStackParser.getInstance());
        register(Message.class, MessageParser.getInstance());

        LOGGER.info("Configuration parsers initialized (" + PARSERS.size() + " parsers)");
    }

    /** Check if parsers have been initialized. */
    public static boolean isInitialized() {
        return initialized;
    }

    /** Log a warning for parser issues. */
    protected void warning(String message) {
        LOGGER.warning("[Parser] " + message);
    }

    /**
     * Load an object from a configuration section.
     *
     * @param config The configuration section
     * @param path The path within the section (can be null for root)
     * @return The parsed object, or null if parsing fails
     */
    public abstract T loadFromConfig(ConfigurationSection config, String path);

    /** Load an object from a configuration section at root level. */
    public T loadFromConfig(ConfigurationSection config) {
        return loadFromConfig(config, null);
    }

    /**
     * Save an object to a configuration section.
     *
     * @param config The configuration section
     * @param path The path within the section
     * @param object The object to save
     */
    public abstract void saveToConfig(ConfigurationSection config, String path, T object);

    /** Static helper to save any registered type. */
    @SuppressWarnings("unchecked")
    public static <T> void saveObjectToConfig(ConfigurationSection config, String path, T object) {
        if (object == null) return;

        Parser<T> parser = (Parser<T>) getParser(object.getClass());
        if (parser != null) {
            parser.saveToConfig(config, path, object);
        }
    }
}
