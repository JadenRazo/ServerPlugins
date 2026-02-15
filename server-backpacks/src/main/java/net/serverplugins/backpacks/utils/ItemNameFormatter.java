package net.serverplugins.backpacks.utils;

import org.bukkit.Material;

/** Utility class for formatting item names. */
public class ItemNameFormatter {

    /**
     * Formats a Material name to be human-readable. Converts underscores to spaces and capitalizes
     * the first letter of each word.
     *
     * @param material The material to format
     * @return A human-readable name
     */
    public static String format(Material material) {
        return formatMaterialName(material);
    }

    /**
     * Formats a Material name to be human-readable. Converts underscores to spaces and capitalizes
     * the first letter of each word.
     *
     * @param material The material to format
     * @return A human-readable name
     */
    public static String formatMaterialName(Material material) {
        if (material == null) {
            return "Unknown";
        }

        String name = material.name();
        String[] words = name.split("_");
        StringBuilder formatted = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                formatted.append(" ");
            }
            String word = words[i].toLowerCase();
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    formatted.append(word.substring(1));
                }
            }
        }

        return formatted.toString();
    }
}
