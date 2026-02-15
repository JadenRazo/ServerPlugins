package net.serverplugins.backpacks.managers;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.serverplugins.backpacks.BackpacksConfig;
import net.serverplugins.backpacks.ServerBackpacks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

/**
 * Manages crafting recipes for backpacks. Registers shaped recipes based on configuration and
 * handles recipe lifecycle.
 */
public class RecipeManager {

    private final ServerBackpacks plugin;
    private final Set<NamespacedKey> registeredRecipes = new HashSet<>();

    public RecipeManager(ServerBackpacks plugin) {
        this.plugin = plugin;
    }

    /**
     * Register all backpack recipes from config. This should be called during plugin enable after
     * config is loaded.
     */
    public void registerRecipes() {
        BackpacksConfig config = plugin.getBackpacksConfig();
        if (config == null) {
            plugin.getLogger().warning("Cannot register recipes: config not loaded");
            return;
        }

        int registeredCount = 0;
        int skippedCount = 0;

        for (BackpacksConfig.BackpackType type : config.getBackpackTypes().values()) {
            if (type.craftingEnabled()) {
                if (registerRecipe(type)) {
                    registeredCount++;
                } else {
                    skippedCount++;
                }
            }
        }

        if (registeredCount > 0) {
            plugin.getLogger()
                    .info("Registered " + registeredCount + " backpack crafting recipe(s)");
        }
        if (skippedCount > 0) {
            plugin.getLogger().warning("Skipped " + skippedCount + " invalid recipe(s)");
        }
    }

    /**
     * Register a single backpack recipe.
     *
     * @param type The backpack type to create a recipe for
     * @return true if registration succeeded, false otherwise
     */
    private boolean registerRecipe(BackpacksConfig.BackpackType type) {
        // Validate recipe configuration
        if (!validateRecipe(type)) {
            plugin.getLogger().warning("Skipping invalid recipe for backpack '" + type.id() + "'");
            return false;
        }

        try {
            // Create namespaced key: serverbackpacks:tier1_backpack
            NamespacedKey key = new NamespacedKey(plugin, type.id().toLowerCase() + "_backpack");

            // Create the backpack result item
            ItemStack result = plugin.getBackpackManager().createBackpack(type);

            // Create shaped recipe
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(type.craftingShape());

            // Set ingredients
            for (Map.Entry<Character, Material> entry : type.craftingIngredients().entrySet()) {
                recipe.setIngredient(entry.getKey(), entry.getValue());
            }

            // Add recipe to discovery book group
            recipe.setGroup("server_backpacks");

            // Register with server
            Bukkit.addRecipe(recipe);
            registeredRecipes.add(key);

            plugin.getLogger().info("Registered crafting recipe: " + type.id());
            return true;

        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            "Failed to register recipe for '" + type.id() + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Validates a recipe configuration before registration.
     *
     * @param type The backpack type to validate
     * @return true if valid, false otherwise
     */
    private boolean validateRecipe(BackpacksConfig.BackpackType type) {
        String[] shape = type.craftingShape();
        Map<Character, Material> ingredients = type.craftingIngredients();

        // Check if shape exists
        if (shape == null || shape.length == 0) {
            plugin.getLogger().warning("Recipe for '" + type.id() + "' has no shape defined");
            return false;
        }

        // Check shape dimensions (1-3 rows)
        if (shape.length < 1 || shape.length > 3) {
            plugin.getLogger()
                    .warning(
                            "Recipe for '"
                                    + type.id()
                                    + "' has invalid shape: must be 1-3 rows (got "
                                    + shape.length
                                    + ")");
            return false;
        }

        // Check each row (1-3 characters)
        for (int i = 0; i < shape.length; i++) {
            String row = shape[i];
            if (row == null || row.length() < 1 || row.length() > 3) {
                plugin.getLogger()
                        .warning(
                                "Recipe for '"
                                        + type.id()
                                        + "' has invalid row "
                                        + (i + 1)
                                        + ": must be 1-3 characters");
                return false;
            }
        }

        // Check if all symbols in shape have ingredients defined
        Set<Character> usedSymbols = new HashSet<>();
        for (String row : shape) {
            for (char c : row.toCharArray()) {
                if (c != ' ') { // Space is valid (empty slot)
                    usedSymbols.add(c);
                }
            }
        }

        for (Character symbol : usedSymbols) {
            if (!ingredients.containsKey(symbol)) {
                plugin.getLogger()
                        .warning(
                                "Recipe for '"
                                        + type.id()
                                        + "' uses symbol '"
                                        + symbol
                                        + "' but no ingredient is defined for it");
                return false;
            }
        }

        // Warn about unused ingredients (not an error, just helpful)
        for (Character symbol : ingredients.keySet()) {
            if (!usedSymbols.contains(symbol)) {
                plugin.getLogger()
                        .warning(
                                "Recipe for '"
                                        + type.id()
                                        + "' defines ingredient '"
                                        + symbol
                                        + "' but it's not used in the shape");
            }
        }

        return true;
    }

    /** Unregister all recipes. This should be called during plugin disable. */
    public void unregisterRecipes() {
        for (NamespacedKey key : registeredRecipes) {
            try {
                Bukkit.removeRecipe(key);
            } catch (Exception e) {
                plugin.getLogger()
                        .warning("Failed to unregister recipe " + key + ": " + e.getMessage());
            }
        }
        registeredRecipes.clear();

        if (!registeredRecipes.isEmpty()) {
            plugin.getLogger().info("Unregistered all backpack recipes");
        }
    }

    /**
     * Gets the count of registered recipes.
     *
     * @return Number of currently registered recipes
     */
    public int getRecipeCount() {
        return registeredRecipes.size();
    }

    /**
     * Checks if recipes are currently registered.
     *
     * @return true if at least one recipe is registered
     */
    public boolean hasRecipes() {
        return !registeredRecipes.isEmpty();
    }
}
