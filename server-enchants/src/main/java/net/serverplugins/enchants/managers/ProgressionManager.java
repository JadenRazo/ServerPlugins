package net.serverplugins.enchants.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.enchantments.CustomEnchantment;
import net.serverplugins.enchants.enchantments.EnchantTier;
import net.serverplugins.enchants.models.GameResult;
import net.serverplugins.enchants.models.PlayerProgression;
import net.serverplugins.enchants.repository.EnchanterRepository;
import org.bukkit.entity.Player;

/**
 * Manages player progression, experience, fragments, and tier access. Caches progression data and
 * handles leveling up.
 */
public class ProgressionManager {

    private final ServerEnchants plugin;
    private final EnchanterRepository repository;
    private final EnchantmentRegistry registry;
    private final Map<UUID, PlayerProgression> progressionCache = new HashMap<>();

    public ProgressionManager(
            ServerEnchants plugin, EnchanterRepository repository, EnchantmentRegistry registry) {
        this.plugin = plugin;
        this.repository = repository;
        this.registry = registry;
    }

    /** Loads progression from database and caches it. */
    public PlayerProgression loadProgression(UUID playerUuid) {
        PlayerProgression progression = repository.getProgression(playerUuid);
        progressionCache.put(playerUuid, progression);
        return progression;
    }

    /** Gets cached progression or loads from database. */
    public PlayerProgression getProgression(UUID playerUuid) {
        return progressionCache.computeIfAbsent(playerUuid, this::loadProgression);
    }

    /** Saves cached progression to database asynchronously. */
    public void saveProgression(UUID playerUuid) {
        PlayerProgression progression = progressionCache.get(playerUuid);
        if (progression != null) {
            repository.saveProgression(progression);
        }
    }

    /** Saves all cached progressions to database. */
    public void saveAll() {
        progressionCache.values().forEach(repository::saveProgression);
    }

    /** Adds game rewards (XP and fragments) and handles level-ups. */
    public void addGameReward(Player player, GameResult result) {
        PlayerProgression progression = getProgression(player.getUniqueId());

        // Add XP and fragments
        progression.addExperience(result.getXpEarned());
        progression.addFragments(result.getFragmentsEarned());

        // Increment game stats
        progression.setLifetimeGamesPlayed(progression.getLifetimeGamesPlayed() + 1);
        if (result.isWon()) {
            progression.setLifetimeGamesWon(progression.getLifetimeGamesWon() + 1);
        }

        // Check for level up
        boolean leveledUp = false;
        while (progression.tryLevelUp()) {
            leveledUp = true;
        }

        if (leveledUp) {
            TextUtil.sendSuccess(player, "Level up! You are now level " + progression.getLevel());
            player.playSound(player.getLocation(), "entity.player.levelup", 1.0f, 1.0f);
        }

        // Save asynchronously
        saveProgression(player.getUniqueId());
    }

    /** Checks if player can access a tier based on level and unlock requirements. */
    public boolean canAccessTier(Player player, EnchantTier tier) {
        PlayerProgression progression = getProgression(player.getUniqueId());
        int requiredLevel = plugin.getEnchantsConfig().getTierLevelRequirement(tier);

        if (progression.getLevel() < requiredLevel) {
            return false;
        }

        // Check unlock requirements for higher tiers
        int requiredUnlocks = plugin.getEnchantsConfig().getTierUnlockRequirement(tier);
        if (requiredUnlocks > 0) {
            EnchantTier previousTier =
                    switch (tier) {
                        case UNCOMMON -> EnchantTier.COMMON;
                        case RARE -> EnchantTier.UNCOMMON;
                        case LEGENDARY -> EnchantTier.RARE;
                        default -> null;
                    };

            if (previousTier != null) {
                int unlockedCount = countUnlocksByTier(player.getUniqueId(), previousTier);
                return unlockedCount >= requiredUnlocks;
            }
        }

        return true;
    }

    /** Counts how many enchantments of a specific tier the player has unlocked. */
    public int countUnlocksByTier(UUID playerUuid, EnchantTier tier) {
        Map<String, Integer> unlocks = repository.getUnlocks(playerUuid);
        int count = 0;

        for (String enchantId : unlocks.keySet()) {
            CustomEnchantment enchant = registry.getById(enchantId);
            if (enchant != null && enchant.getTier() == tier) {
                count++;
            }
        }

        return count;
    }

    /** Gets the unlock level for a specific enchantment (0 if not unlocked). */
    public int getUnlockLevel(UUID playerUuid, String enchantId) {
        Map<String, Integer> unlocks = repository.getUnlocks(playerUuid);
        return unlocks.getOrDefault(enchantId, 0);
    }

    /** Checks if player has unlocked an enchantment. */
    public boolean hasUnlocked(UUID playerUuid, String enchantId) {
        return getUnlockLevel(playerUuid, enchantId) > 0;
    }

    /** Unlocks an enchantment at a specific level. */
    public void unlockEnchantment(UUID playerUuid, String enchantId, int level) {
        repository.saveUnlock(playerUuid, enchantId, level);
    }

    /** Unloads player data from cache and saves to database. */
    public void unloadPlayer(UUID playerUuid) {
        saveProgression(playerUuid);
        progressionCache.remove(playerUuid);
    }
}
