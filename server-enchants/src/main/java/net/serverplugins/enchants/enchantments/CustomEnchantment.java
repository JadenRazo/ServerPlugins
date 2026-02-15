package net.serverplugins.enchants.enchantments;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Abstract base class for all custom enchantments. Defines the structure and behavior for custom
 * enchantments in the ServerPlugins system.
 */
public abstract class CustomEnchantment {

    private final String id;
    private final String displayName;
    private final EnchantTier tier;
    private final int maxLevel;
    private final Material icon;
    private final String[] description;

    /**
     * Constructor for creating a custom enchantment.
     *
     * @param id Unique identifier (e.g., "auto_smelt")
     * @param displayName Display name shown to players (e.g., "Auto-Smelt")
     * @param tier The rarity tier of this enchantment
     * @param maxLevel Maximum enchantment level (1-5)
     * @param icon Material icon for GUI representation
     * @param description Lore lines describing the enchantment
     */
    protected CustomEnchantment(
            String id,
            String displayName,
            EnchantTier tier,
            int maxLevel,
            Material icon,
            String... description) {
        this.id = id;
        this.displayName = displayName;
        this.tier = tier;
        this.maxLevel = maxLevel;
        this.icon = icon;
        this.description = description;
    }

    /**
     * Check if this enchantment can be applied to the given item.
     *
     * @param item The item to check
     * @return true if the enchantment can be applied
     */
    public abstract boolean canApplyTo(ItemStack item);

    /**
     * Called when a block is broken by a player with this enchantment. Default implementation does
     * nothing - override to add behavior.
     *
     * @param player The player who broke the block
     * @param event The block break event
     * @param level The level of this enchantment on the tool
     */
    public void onBlockBreak(Player player, BlockBreakEvent event, int level) {
        // Default: no-op
    }

    /**
     * Called when a player dies with this enchantment on an item. Default implementation does
     * nothing - override to add behavior.
     *
     * @param player The player who died
     * @param event The player death event
     * @param level The level of this enchantment
     */
    public void onPlayerDeath(Player player, PlayerDeathEvent event, int level) {
        // Default: no-op
    }

    /**
     * Called periodically (tick) for enchantments with passive effects. Default implementation does
     * nothing - override to add behavior.
     *
     * @param player The player with the enchanted item
     * @param level The level of this enchantment
     */
    public void onTick(Player player, int level) {
        // Default: no-op
    }

    /**
     * Get the unique identifier of this enchantment.
     *
     * @return The enchantment ID
     */
    public String getId() {
        return id;
    }

    /**
     * Get the display name of this enchantment.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the tier of this enchantment.
     *
     * @return The enchantment tier
     */
    public EnchantTier getTier() {
        return tier;
    }

    /**
     * Get the maximum level of this enchantment.
     *
     * @return The maximum level
     */
    public int getMaxLevel() {
        return maxLevel;
    }

    /**
     * Get the GUI icon material for this enchantment.
     *
     * @return The icon material
     */
    public Material getIcon() {
        return icon;
    }

    /**
     * Get the description lines for this enchantment.
     *
     * @return Array of description strings
     */
    public String[] getDescription() {
        return description;
    }

    /**
     * Get the level as a roman numeral display string.
     *
     * @param level The numeric level (1-5)
     * @return Roman numeral string (I, II, III, IV, V)
     */
    public String getLevelDisplay(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    /**
     * Get the full lore for this enchantment at a specific level. Includes tier color, display
     * name, level, and description.
     *
     * @param level The enchantment level
     * @return List of formatted lore strings
     */
    public List<String> getFullLore(int level) {
        List<String> lore = new ArrayList<>();

        // Add title line with tier color
        String levelDisplay = getLevelDisplay(level);
        lore.add(tier.getColor() + displayName + " " + levelDisplay);

        // Add description lines
        for (String line : description) {
            lore.add("<gray>" + line);
        }

        // Add level info if max level > 1
        if (maxLevel > 1) {
            lore.add("");
            lore.add("<dark_gray>Level: <white>" + level + "<gray>/<white>" + maxLevel);
        }

        return lore;
    }

    /**
     * Get a colored display string for this enchantment at a specific level.
     *
     * @param level The enchantment level
     * @return Formatted string with tier color, name, and level
     */
    public String getColoredDisplay(int level) {
        return tier.getColor() + displayName + " " + getLevelDisplay(level);
    }
}
