package net.serverplugins.arcade.games.slots;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.serverplugins.arcade.ArcadeConfig;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameResult;

public class SlotsManager {

    private final ServerArcade plugin;
    // SECURITY: Use SecureRandom instead of Random for unpredictable outcomes
    // This is a temporary fix until provably fair system is implemented
    private final SecureRandom random = new SecureRandom();
    // Use Caffeine cache with automatic expiration to prevent memory leak
    private final Cache<UUID, Long> cooldowns;

    public SlotsManager(ServerArcade plugin) {
        this.plugin = plugin;
        // Initialize cache with automatic expiration based on cooldown setting + buffer
        int cooldownSeconds = plugin.getArcadeConfig().getCooldown();
        this.cooldowns =
                Caffeine.newBuilder()
                        .expireAfterWrite(cooldownSeconds + 5, TimeUnit.SECONDS)
                        .maximumSize(10_000)
                        .build();
    }

    public SpinResult spin(double bet) {
        List<ArcadeConfig.SlotSymbol> symbols = plugin.getArcadeConfig().getSlotSymbols();
        if (symbols.isEmpty()) {
            return new SpinResult(
                    new ArcadeConfig.SlotSymbol[3], GameResult.lose(bet, "No symbols configured"));
        }

        int totalWeight = symbols.stream().mapToInt(ArcadeConfig.SlotSymbol::weight).sum();

        ArcadeConfig.SlotSymbol[] results = new ArcadeConfig.SlotSymbol[3];
        for (int i = 0; i < 3; i++) {
            results[i] = pickSymbol(symbols, totalWeight);
        }

        GameResult result = calculateResult(bet, results);
        return new SpinResult(results, result);
    }

    private ArcadeConfig.SlotSymbol pickSymbol(
            List<ArcadeConfig.SlotSymbol> symbols, int totalWeight) {
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;

        for (ArcadeConfig.SlotSymbol symbol : symbols) {
            cumulative += symbol.weight();
            if (roll < cumulative) {
                return symbol;
            }
        }

        return symbols.get(symbols.size() - 1);
    }

    private GameResult calculateResult(double bet, ArcadeConfig.SlotSymbol[] symbols) {
        if (symbols[0].equals(symbols[1]) && symbols[1].equals(symbols[2])) {
            double multiplier = symbols[0].multiplier();
            return GameResult.win(bet, multiplier, "Three " + symbols[0].name() + "s!");
        }

        if (symbols[0].equals(symbols[1])
                || symbols[1].equals(symbols[2])
                || symbols[0].equals(symbols[2])) {
            ArcadeConfig.SlotSymbol match =
                    symbols[0].equals(symbols[1])
                            ? symbols[0]
                            : (symbols[1].equals(symbols[2]) ? symbols[1] : symbols[0]);
            double multiplier = match.multiplier() * 0.3;
            return GameResult.win(bet, multiplier, "Two " + match.name() + "s!");
        }

        return GameResult.lose(bet);
    }

    public boolean isOnCooldown(UUID uuid) {
        Long lastPlay = cooldowns.getIfPresent(uuid);
        if (lastPlay == null) return false;

        int cooldownSeconds = plugin.getArcadeConfig().getCooldown();
        return System.currentTimeMillis() - lastPlay < cooldownSeconds * 1000L;
    }

    public int getRemainingCooldown(UUID uuid) {
        Long lastPlay = cooldowns.getIfPresent(uuid);
        if (lastPlay == null) return 0;

        int cooldownSeconds = plugin.getArcadeConfig().getCooldown();
        long elapsed = System.currentTimeMillis() - lastPlay;
        return Math.max(0, cooldownSeconds - (int) (elapsed / 1000));
    }

    public void setCooldown(UUID uuid) {
        cooldowns.put(uuid, System.currentTimeMillis());
    }

    public record SpinResult(ArcadeConfig.SlotSymbol[] symbols, GameResult result) {}
}
