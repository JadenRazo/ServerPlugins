package net.serverplugins.events.events.pinata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.events.ServerEvents;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Premium Pinata event - spawns at a special location with 2x rewards and bonus keys. Features: -
 * Spawns within configurable radius of spawn center - 2x coin rewards (6000 total vs 3000 for
 * regular) - More hits required (100-150 vs 70-100) - Chance to give special keys (diversity, epic,
 * dkey medium/hard)
 */
public class PremiumPinataEvent extends BasePinataEvent {

    // Config values loaded at runtime
    private int totalCoins;
    private int breakerBonus;
    private int proportionalPool;
    private double diversityKeyChance;
    private double epicKeyChance;
    private double dkeyMediumChance;
    private double dkeyHardChance;

    public PremiumPinataEvent(ServerEvents plugin) {
        super(plugin);
    }

    @Override
    public EventType getType() {
        return EventType.PREMIUM_PINATA;
    }

    @Override
    public String getDisplayName() {
        return "Premium Pinata";
    }

    @Override
    public int getMinimumPlayers() {
        return config.getPremiumPinataMinPlayers();
    }

    @Override
    protected Location getSpawnLocation() {
        // Load config values
        loadConfigValues();

        // Find safe spawn location
        return findSafeSpawnLocation();
    }

    /** Load premium pinata config values. */
    private void loadConfigValues() {
        totalCoins = config.getPremiumPinataTotalCoins();
        breakerBonus = config.getPremiumPinataBreakerBonus();
        proportionalPool = totalCoins - breakerBonus;
        diversityKeyChance = config.getPremiumPinataDiversityChance();
        epicKeyChance = config.getPremiumPinataEpicChance();
        dkeyMediumChance = config.getPremiumPinataDkeyMediumChance();
        dkeyHardChance = config.getPremiumPinataDkeyHardChance();
    }

    /** Find a safe spawn location within the radius that is not inside blocks. */
    private Location findSafeSpawnLocation() {
        Location spawnCenter = config.getPremiumPinataSpawnCenter();
        double centerX = spawnCenter.getX();
        double centerY = spawnCenter.getY();
        double centerZ = spawnCenter.getZ();
        double spawnRadius = config.getPremiumPinataSpawnRadius();

        World world = spawnCenter.getWorld();
        if (world == null) {
            world = Bukkit.getWorld("spawn");
        }
        if (world == null) {
            world = Bukkit.getWorld("playworld");
        }
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return null;
        }

        // Reuse location object to reduce allocations
        Location testLoc = new Location(world, 0, 0, 0);

        // Try up to 50 times to find a safe location
        for (int attempt = 0; attempt < 50; attempt++) {
            double offsetX = (random.nextDouble() * 2 - 1) * spawnRadius;
            double offsetZ = (random.nextDouble() * 2 - 1) * spawnRadius;

            double x = centerX + offsetX;
            double z = centerZ + offsetZ;

            // Check at the center Y and slightly above/below
            for (double yOffset = 0; yOffset <= 3; yOffset++) {
                testLoc.setX(x);
                testLoc.setY(centerY + yOffset);
                testLoc.setZ(z);

                if (isSafeLocation(testLoc)) {
                    return testLoc.clone();
                }
            }
        }

        // Fallback to center location
        return new Location(world, centerX, centerY, centerZ);
    }

    /** Check if a location is safe (not inside solid blocks). */
    private boolean isSafeLocation(Location loc) {
        Block block = loc.getBlock();
        Block blockAbove = block.getRelative(0, 1, 0);
        Block blockBelow = block.getRelative(0, -1, 0);

        // Entity needs 2 blocks of air space and solid ground below (or air for floating)
        return !block.getType().isSolid()
                && !blockAbove.getType().isSolid()
                && (blockBelow.getType().isSolid() || blockBelow.getType() == Material.AIR);
    }

    @Override
    protected int calculateClicksRequired() {
        int min = config.getPremiumPinataClicksMin();
        int max = config.getPremiumPinataClicksMax();
        return min + random.nextInt(max - min + 1);
    }

    @Override
    protected EntityType getPinataEntityType() {
        return EntityType.LLAMA; // Premium pinata uses llama
    }

    @Override
    protected org.bukkit.ChatColor getGlowColor() {
        return org.bukkit.ChatColor.LIGHT_PURPLE; // Magenta/pink for premium
    }

    @Override
    protected String getGlowTeamName() {
        return "premium_pinata_glow";
    }

    @Override
    protected String formatPinataName(int clicks, int required) {
        String baseName =
                "<light_purple><bold>PREMIUM PINATA</bold></light_purple> <white>["
                        + clicks
                        + "/"
                        + required
                        + "]</white>";
        if (targetPlayerName != null && !targetPlayerName.isEmpty()) {
            return baseName + " <gray>(" + targetPlayerName + ")</gray>";
        }
        return baseName;
    }

    @Override
    protected Material getArmorMaterial() {
        return Material.DIAMOND_CHESTPLATE; // Diamond armor for premium look
    }

    @Override
    protected int getTimeoutSeconds() {
        return config.getPremiumPinataTimeout();
    }

    @Override
    protected String getTeleportCommand() {
        return "/warp premiumpinata";
    }

    @Override
    protected NamedTextColor getTeleportColor() {
        return NamedTextColor.LIGHT_PURPLE;
    }

    @Override
    protected BossBar.Color getBarColor() {
        return BossBar.Color.PINK;
    }

    @Override
    protected Particle getMovementParticle() {
        return Particle.FIREWORK;
    }

    @Override
    protected Particle getSecondaryParticle() {
        return Particle.END_ROD; // Extra sparkle for premium
    }

    @Override
    protected int getMovementParticleCount() {
        return 3;
    }

    @Override
    protected int getBurstParticleCount() {
        return 12; // More particles for premium
    }

    @Override
    protected void broadcastStartMessages() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            MessageBuilder.create()
                    .colored("\u2605 PREMIUM PINATA EVENT! \u2605", "<light_purple><bold>")
                    .send(player);
            MessageBuilder.create()
                    .colored(
                            "A PREMIUM pinata has spawned! Break it by punching it ",
                            "<light_purple>")
                    .highlight(clicksRequired + " times")
                    .colored("!", "<light_purple>")
                    .send(player);
            MessageBuilder.create()
                    .success("Reward: ")
                    .emphasis("$" + String.format("%,d", totalCoins) + " coins ")
                    .success("+ ")
                    .command("BONUS KEYS!")
                    .success(" distributed based on your hits!")
                    .send(player);
            MessageBuilder.create()
                    .warning("The final hit gets a ")
                    .emphasis("bonus")
                    .warning(" and ")
                    .command("better key chances")
                    .warning("!")
                    .send(player);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        }

        broadcastWithTeleport(config.getPrefix() + "&dThe premium pinata awaits!");
    }

    @Override
    protected void broadcastBreakMessages() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            MessageBuilder.create()
                    .colored("\u2605 PREMIUM PINATA BROKEN! \u2605", "<light_purple><bold>")
                    .send(player);
            if (breaker != null) {
                MessageBuilder.create()
                        .highlight(breaker.getName())
                        .colored(" dealt the final blow!", "<light_purple>")
                        .send(player);
            }
        }
    }

    @Override
    protected void breakPinata() {
        if (pinataEntity == null) return;

        Location loc = pinataEntity.getLocation().clone();

        // Extra big explosion for premium
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 5);
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 200, 1.5, 1.5, 1.5, 0.8);
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc, 100, 1.5, 1.5, 1.5, 0.5);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc, 50, 1, 1, 1, 0.3);

        // Play special sounds
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);
        }

        broadcastBreakMessages();
        distributeRewards();
        distributeItems();
        stop();
    }

    @Override
    protected void distributeRewards() {
        int totalHits = getTotalPlayerClicks();

        if (totalHits == 0) {
            plugin.getLogger().warning("[PREMIUM_PINATA] No hits recorded, skipping rewards");
            return;
        }

        for (Map.Entry<UUID, Integer> entry : getPlayerClicks().entrySet()) {
            UUID uuid = entry.getKey();
            int hits = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);

            if (player != null && player.isOnline()) {
                double hitPercentage = (double) hits / totalHits;
                int proportionalCoins = (int) (proportionalPool * hitPercentage);

                int playerCoins = proportionalCoins;
                boolean isBreaker = player.equals(breaker);
                if (isBreaker) {
                    playerCoins += breakerBonus;
                }

                // Add combo bonus from hit streaks
                int comboBonus = getComboBonus(uuid);
                playerCoins += comboBonus;

                // Give coins via Vault
                if (plugin.hasEconomy()) {
                    plugin.getEconomy().depositPlayer(player, playerCoins);
                }

                // Roll for special keys
                List<String> keysWon = new ArrayList<>();
                double multiplier = isBreaker ? 1.0 : 0.5; // Half chance for non-breakers

                // Diversity key (/givekey)
                if (random.nextDouble() < diversityKeyChance * multiplier) {
                    giveKey(player, "diversity", "givekey");
                    keysWon.add("<green>Diversity Key</green>");
                }

                // Epic key (/givekey) - rare
                if (random.nextDouble() < epicKeyChance * multiplier) {
                    giveKey(player, "epic", "givekey");
                    keysWon.add("<dark_purple>Epic Key</dark_purple>");
                }

                // Medium dkey (/dkey)
                if (random.nextDouble() < dkeyMediumChance * multiplier) {
                    giveKey(player, "medium", "dkey");
                    keysWon.add("<yellow>Medium Dungeon Key</yellow>");
                }

                // Hard dkey (/dkey) - rare
                if (random.nextDouble() < dkeyHardChance * multiplier) {
                    giveKey(player, "hard", "dkey");
                    keysWon.add("<red>Hard Dungeon Key</red>");
                }

                // Send reward message (include combo bonus if earned)
                String bonusText = comboBonus > 0 ? " + " + comboBonus + " combo bonus" : "";
                if (isBreaker) {
                    MessageBuilder.create()
                            .colored("You broke the PREMIUM pinata! ", "<light_purple>")
                            .emphasis("+" + playerCoins + " coins ")
                            .info("(" + hits + " hits + final hit bonus" + bonusText + ")")
                            .send(player);
                } else {
                    MessageBuilder.create()
                            .colored("You hit the PREMIUM pinata! ", "<light_purple>")
                            .emphasis("+" + playerCoins + " coins ")
                            .info("(" + hits + " hits" + bonusText + ")")
                            .send(player);
                }

                // Announce keys won
                if (!keysWon.isEmpty()) {
                    TextUtil.send(
                            player,
                            "<aqua><bold>BONUS KEYS:</bold></aqua> <white>"
                                    + String.join(", ", keysWon)
                                    + "</white>");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                }
            }
        }
    }

    @Override
    protected List<org.bukkit.inventory.ItemStack> getDroppableItems() {
        List<org.bukkit.inventory.ItemStack> items = super.getDroppableItems();
        if (config.isPremiumItemDropsEnabled()) {
            for (org.bukkit.inventory.ItemStack bonusItem : config.getPremiumBonusItems()) {
                if (random.nextDouble() < config.getPremiumDropChance()) {
                    items.add(bonusItem.clone());
                }
            }
        }
        return items;
    }

    @Override
    protected void distributeItems() {
        List<org.bukkit.inventory.ItemStack> items = getDroppableItems();
        if (items.isEmpty()) return;

        boolean breakerOnly = config.isItemDropsBreakerOnly();
        boolean proportional = config.isItemDropsProportional();
        float dropChance = (float) config.getPremiumDropChance();

        if (breakerOnly && breaker != null) {
            for (org.bukkit.inventory.ItemStack item : items) {
                if (random.nextFloat() < dropChance) {
                    giveItemToPlayer(breaker, item);
                }
            }
        } else if (proportional) {
            int totalClicks = currentClicks.get();
            java.util.HashMap<UUID, Integer> eligiblePlayers = new java.util.HashMap<>();
            for (Map.Entry<UUID, java.util.concurrent.atomic.AtomicInteger> entry :
                    playerClicks.entrySet()) {
                eligiblePlayers.put(entry.getKey(), entry.getValue().get());
            }

            for (org.bukkit.inventory.ItemStack item : items) {
                if (random.nextFloat() < dropChance) {
                    Player recipient = selectWeightedRandomPlayer(eligiblePlayers, totalClicks);
                    if (recipient != null) {
                        giveItemToPlayer(recipient, item);
                    }
                }
            }
        } else {
            List<UUID> participants = new ArrayList<>(playerClicks.keySet());
            for (org.bukkit.inventory.ItemStack item : items) {
                if (random.nextFloat() < dropChance) {
                    UUID randomUuid = participants.get(random.nextInt(participants.size()));
                    Player recipient = Bukkit.getPlayer(randomUuid);
                    if (recipient != null) {
                        giveItemToPlayer(recipient, item);
                    }
                }
            }
        }
    }

    /** Give a key to a player using the appropriate command. */
    private void giveKey(Player player, String keyType, String command) {
        try {
            String cmd = command + " " + player.getName() + " " + keyType;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            plugin.getLogger()
                    .info(
                            "[PREMIUM_PINATA] Gave "
                                    + keyType
                                    + " key to "
                                    + player.getName()
                                    + " via /"
                                    + command);
        } catch (Exception e) {
            plugin.getLogger().warning("[PREMIUM_PINATA] Failed to give key: " + e.getMessage());
        }
    }
}
