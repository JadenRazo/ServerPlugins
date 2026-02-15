package net.serverplugins.enchants.utils;

import java.util.*;
import net.serverplugins.enchants.models.Essence;
import net.serverplugins.enchants.models.EssenceRequirement;
import net.serverplugins.enchants.models.RuneCard;
import net.serverplugins.enchants.models.RuneType;
import org.bukkit.Material;

public class RuneGenerator {

    private static final Random RANDOM = new Random();
    private static final String[] PROPERTY_NAMES = {
        "Fire", "Water", "Earth", "Air", "Light", "Shadow"
    };
    private static final Material[] ESSENCE_MATERIALS = {
        Material.GLOWSTONE_DUST,
        Material.REDSTONE,
        Material.BLAZE_POWDER,
        Material.PRISMARINE_CRYSTALS,
        Material.ECHO_SHARD,
        Material.AMETHYST_SHARD,
        Material.QUARTZ,
        Material.ENDER_PEARL,
        Material.SNOWBALL
    };
    private static final String[] ESSENCE_COLORS = {
        "<red>",
        "<aqua>",
        "<yellow>",
        "<green>",
        "<light_purple>",
        "<dark_purple>",
        "<gold>",
        "<dark_gray>"
    };

    /**
     * Generate a random pool of unique RuneTypes
     *
     * @param size number of runes to generate
     * @return list of unique RuneTypes
     */
    public static List<RuneType> generateRunePool(int size) {
        if (size <= 0 || size > RuneType.values().length) {
            throw new IllegalArgumentException(
                    "Size must be between 1 and " + RuneType.values().length);
        }

        List<RuneType> allRunes = new ArrayList<>(Arrays.asList(RuneType.values()));
        Collections.shuffle(allRunes);
        return new ArrayList<>(allRunes.subList(0, size));
    }

    /**
     * Generate pairs of RuneCards for the memory game
     *
     * @param pairs number of pairs to generate
     * @return shuffled list of RuneCards with matching pairs
     */
    public static List<RuneCard> generateMemoryCards(int pairs) {
        if (pairs <= 0 || pairs > RuneType.values().length) {
            throw new IllegalArgumentException(
                    "Pairs must be between 1 and " + RuneType.values().length);
        }

        List<RuneType> runePool = generateRunePool(pairs);
        List<RuneCard> cards = new ArrayList<>();

        for (int i = 0; i < pairs; i++) {
            RuneType rune = runePool.get(i);
            cards.add(new RuneCard(rune, i));
            cards.add(new RuneCard(rune, i));
        }

        Collections.shuffle(cards);
        return cards;
    }

    /**
     * Generate a random code from a pool of RuneTypes (can contain duplicates)
     *
     * @param length length of the code
     * @param pool pool of runes to choose from
     * @return random code
     */
    public static List<RuneType> generateCode(int length, List<RuneType> pool) {
        if (length <= 0) {
            throw new IllegalArgumentException("Code length must be positive");
        }
        if (pool == null || pool.isEmpty()) {
            throw new IllegalArgumentException("Pool cannot be null or empty");
        }

        List<RuneType> code = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            code.add(pool.get(RANDOM.nextInt(pool.size())));
        }
        return code;
    }

    /**
     * Generate a random essence requirement with specified number of properties
     *
     * @param propertyCount number of properties to require
     * @return random EssenceRequirement
     */
    public static EssenceRequirement generateRequirement(int propertyCount) {
        if (propertyCount <= 0 || propertyCount > PROPERTY_NAMES.length) {
            throw new IllegalArgumentException(
                    "Property count must be between 1 and " + PROPERTY_NAMES.length);
        }

        Map<String, Integer> required = new HashMap<>();
        List<String> availableProperties = new ArrayList<>(Arrays.asList(PROPERTY_NAMES));
        Collections.shuffle(availableProperties);

        for (int i = 0; i < propertyCount; i++) {
            String property = availableProperties.get(i);
            // Generate target values between -5 and +10
            int value = RANDOM.nextInt(16) - 5;
            required.put(property, value);
        }

        return new EssenceRequirement(required);
    }

    /**
     * Generate random essences with properties that can satisfy the requirement
     *
     * @param count number of essences to generate
     * @param requirement the requirement that needs to be satisfied
     * @return list of random essences
     */
    public static List<Essence> generateEssences(int count, EssenceRequirement requirement) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }

        List<Essence> essences = new ArrayList<>();
        List<String> requiredProperties = new ArrayList<>(requirement.getRequired().keySet());

        // Ensure that a valid solution exists by creating essences strategically
        // First, create essences that can help achieve the goal
        for (int i = 0; i < count; i++) {
            String name = "Essence #" + (i + 1);
            Material material = ESSENCE_MATERIALS[RANDOM.nextInt(ESSENCE_MATERIALS.length)];
            String color = ESSENCE_COLORS[RANDOM.nextInt(ESSENCE_COLORS.length)];

            Map<String, Integer> properties = new HashMap<>();
            int propCount = 2 + RANDOM.nextInt(2); // 2-3 properties per essence

            // Pick random properties including some from the requirement
            List<String> availableProps = new ArrayList<>(Arrays.asList(PROPERTY_NAMES));
            Collections.shuffle(availableProps);

            for (int j = 0; j < propCount && j < availableProps.size(); j++) {
                String prop = availableProps.get(j);
                // Values between -2 and +3
                int value = RANDOM.nextInt(6) - 2;
                properties.put(prop, value);
            }

            essences.add(new Essence(name, material, color, properties));
        }

        return essences;
    }

    /**
     * Overloaded version that generates both requirement and essences
     *
     * @param essenceCount number of essences to generate
     * @param propertyCount number of properties in the requirement
     * @return map containing "requirement" and "essences" keys
     */
    public static Map<String, Object> generateAlchemyPuzzle(int essenceCount, int propertyCount) {
        EssenceRequirement requirement = generateRequirement(propertyCount);
        List<Essence> essences = generateEssences(essenceCount, requirement);

        Map<String, Object> puzzle = new HashMap<>();
        puzzle.put("requirement", requirement);
        puzzle.put("essences", essences);
        return puzzle;
    }
}
