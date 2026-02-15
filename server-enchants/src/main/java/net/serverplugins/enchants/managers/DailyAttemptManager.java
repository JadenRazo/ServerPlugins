package net.serverplugins.enchants.managers;

import net.serverplugins.enchants.repository.EnchanterRepository;
import org.bukkit.entity.Player;

/** Simple wrapper around repository methods for managing daily free attempts. */
public class DailyAttemptManager {

    private final EnchanterRepository repository;

    public DailyAttemptManager(EnchanterRepository repository) {
        this.repository = repository;
    }

    /** Checks if player has a free attempt available today. */
    public boolean hasFreeAttempt(Player player) {
        return repository.hasFreeAttempt(player.getUniqueId());
    }

    /** Uses the player's free attempt for today. */
    public void useFreeAttempt(Player player) {
        repository.useFreeAttempt(player.getUniqueId());
    }
}
