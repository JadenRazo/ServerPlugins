package net.serverplugins.events.events.dragon;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.events.EventsConfig;
import net.serverplugins.events.ServerEvents;
import net.serverplugins.events.events.EventReward;
import net.serverplugins.events.events.ServerEvent;
import org.bukkit.*;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Dragon Fight event - enhanced Ender Dragon boss fight with scaling difficulty, phase-based
 * attacks, and damage-based reward distribution.
 */
public class DragonFightEvent implements ServerEvent, Listener {

    private final ServerEvents plugin;
    private final EventsConfig config;

    private boolean active;
    private EnderDragon dragon;
    private BossBar bossBar;
    private World endWorld;

    // Damage tracking
    private final Map<UUID, Double> playerDamage = new ConcurrentHashMap<>();
    private double totalDamage = 0;
    private Player killer;

    // Phase management
    private int currentPhase = 1;
    private final Map<String, Long> attackCooldowns = new HashMap<>();

    // Tasks
    private BukkitTask phaseCheckTask;
    private BukkitTask attackTask;
    private BukkitTask timeoutTask;

    // Fight stats
    private long fightStartTime;
    private double scaledMaxHealth;

    public DragonFightEvent(ServerEvents plugin) {
        this.plugin = plugin;
        this.config = plugin.getEventsConfig();
    }

    @Override
    public EventType getType() {
        return EventType.DRAGON;
    }

    @Override
    public String getDisplayName() {
        return "Dragon Fight";
    }

    @Override
    public void start() {
        if (active) return;

        // Get the end world
        endWorld = Bukkit.getWorld(config.getDragonWorld());
        if (endWorld == null) {
            // Try to find any end world
            for (World world : Bukkit.getWorlds()) {
                if (world.getEnvironment() == World.Environment.THE_END) {
                    endWorld = world;
                    break;
                }
            }
        }

        if (endWorld == null) {
            plugin.getLogger().severe("[DRAGON] No End world found! Cannot start dragon fight.");
            return;
        }

        active = true;
        fightStartTime = System.currentTimeMillis();
        playerDamage.clear();
        totalDamage = 0;
        killer = null;
        currentPhase = 1;
        attackCooldowns.clear();

        // Register listeners
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Find or spawn the dragon
        dragon = findOrSpawnDragon();
        if (dragon == null) {
            plugin.getLogger().severe("[DRAGON] Failed to spawn dragon!");
            stop();
            return;
        }

        // Scale dragon health based on players
        scaleHealth();

        // Create boss bar
        if (config.isDragonBossbarEnabled()) {
            createBossBar();
        }

        // Announce the event with teleport button
        String spawnMessage = config.getDragonMessage("spawn");
        if (spawnMessage.isEmpty()) {
            spawnMessage = "&5&lDRAGON FIGHT! &fThe Ender Dragon has awakened!";
        }
        broadcastWithTeleport(config.getPrefix() + spawnMessage);
        TextUtil.broadcastRaw(
                config.getPrefix()
                        + "&eDeal damage to earn rewards! The more damage you deal, the more coins you earn!");
        TextUtil.broadcastRaw(config.getPrefix() + "&aBonus reward for the killing blow!");
        TextUtil.broadcastRaw(config.getPrefix() + "&dHead to The End to join the fight!");

        // Play sound
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        }

        // Start phase checking
        startPhaseCheckTask();

        // Start attack task
        startAttackTask();

        // Start timeout if configured
        int timeLimit = config.getDragonTimeLimit();
        if (timeLimit > 0) {
            timeoutTask =
                    Bukkit.getScheduler()
                            .runTaskLater(
                                    plugin,
                                    () -> {
                                        if (active) {
                                            TextUtil.broadcastRaw(
                                                    config.getPrefix()
                                                            + "&cTime's up! The dragon has escaped!");
                                            stop();
                                        }
                                    },
                                    timeLimit * 20L);
        }
    }

    private EnderDragon findOrSpawnDragon() {
        // First, check if there's already a dragon
        for (Entity entity : endWorld.getEntities()) {
            if (entity instanceof EnderDragon existingDragon) {
                return existingDragon;
            }
        }

        // No dragon found, spawn one at the end fountain
        Location spawnLoc = new Location(endWorld, 0, 70, 0);

        // Use the DragonBattle to spawn a new dragon properly
        if (endWorld.getEnderDragonBattle() != null) {
            endWorld.getEnderDragonBattle().initiateRespawn();
            // Wait a bit and find the dragon
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                for (Entity entity : endWorld.getEntities()) {
                                    if (entity instanceof EnderDragon) {
                                        this.dragon = (EnderDragon) entity;
                                        scaleHealth();
                                        break;
                                    }
                                }
                            },
                            20L);
        }

        // Spawn directly as fallback
        return endWorld.spawn(spawnLoc, EnderDragon.class);
    }

    private void scaleHealth() {
        if (dragon == null) return;

        // Count players in the end
        int playerCount = 0;
        for (Player player : endWorld.getPlayers()) {
            if (player.getLocation().distance(dragon.getLocation())
                    <= config.getDragonScaleRadius()) {
                playerCount++;
            }
        }
        playerCount = Math.max(1, playerCount);

        // Calculate scaled health
        double baseHealth = config.getDragonBaseHealth();
        double perPlayerHealth = config.getDragonPerPlayerHealth();
        double maxHealth = config.getDragonMaxHealth();

        scaledMaxHealth = Math.min(baseHealth + (perPlayerHealth * playerCount), maxHealth);

        // First, set the max health attribute, then set current health
        var maxHealthAttr = dragon.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(scaledMaxHealth);
            dragon.setHealth(scaledMaxHealth);
        }

        plugin.getLogger()
                .info(
                        "[DRAGON] Scaled health to "
                                + scaledMaxHealth
                                + " for "
                                + playerCount
                                + " players");
    }

    private void createBossBar() {
        String title = config.getDragonBossbarTitle().replace("{health}", "100");
        bossBar =
                Bukkit.createBossBar(
                        title, config.getDragonBossbarColor(), config.getDragonBossbarStyle());
        bossBar.setProgress(1.0);

        // Add all players in the end
        for (Player player : endWorld.getPlayers()) {
            bossBar.addPlayer(player);
        }
    }

    private void updateBossBar() {
        if (bossBar == null || dragon == null) return;

        double healthPercent = (dragon.getHealth() / scaledMaxHealth) * 100;
        String title =
                config.getDragonBossbarTitle()
                        .replace("{health}", String.format("%.0f", healthPercent))
                        .replace("&", "\u00A7");
        bossBar.setTitle(title);
        bossBar.setProgress(Math.max(0, Math.min(1, dragon.getHealth() / scaledMaxHealth)));

        // Update player list
        for (Player player : endWorld.getPlayers()) {
            if (!bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
    }

    private void startPhaseCheckTask() {
        phaseCheckTask =
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!active || dragon == null || dragon.isDead()) {
                            cancel();
                            return;
                        }

                        updateBossBar();
                        checkPhaseTransition();
                    }
                }.runTaskTimer(plugin, 20L, 10L); // Check every 0.5 seconds
    }

    private void checkPhaseTransition() {
        if (dragon == null) return;

        double healthPercent = (dragon.getHealth() / scaledMaxHealth) * 100;
        List<Integer> thresholds = config.getDragonPhaseThresholds();

        int newPhase = 1;
        for (int i = 0; i < thresholds.size(); i++) {
            if (healthPercent <= thresholds.get(i)) {
                newPhase = i + 2;
            }
        }

        if (newPhase > currentPhase) {
            currentPhase = newPhase;
            announcePhaseChange();
        }
    }

    private void announcePhaseChange() {
        String message = config.getDragonMessage("phase_change");
        if (message.isEmpty()) {
            message = "&5&lPHASE {phase}! &eThe dragon grows more powerful!";
        }
        message = message.replace("{phase}", String.valueOf(currentPhase));
        TextUtil.broadcastRaw(config.getPrefix() + message);

        // Effects
        for (Player player : endWorld.getPlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
        }

        if (dragon != null) {
            dragon.getWorld()
                    .spawnParticle(Particle.DRAGON_BREATH, dragon.getLocation(), 50, 3, 3, 3, 0.1);
        }
    }

    private void startAttackTask() {
        attackTask =
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!active || dragon == null || dragon.isDead()) {
                            cancel();
                            return;
                        }

                        // Try to execute attacks based on current phase
                        executeRandomAttack();
                    }
                }.runTaskTimer(plugin, 100L, 60L); // Check every 3 seconds
    }

    private void executeRandomAttack() {
        List<String> availableAttacks = new ArrayList<>();

        // Check which attacks are available
        if (canExecuteAttack("lightning", config.getDragonLightningAttack())) {
            availableAttacks.add("lightning");
        }
        if (canExecuteAttack("breath", config.getDragonBreathAttack())) {
            availableAttacks.add("breath");
        }
        if (canExecuteAttack("knockback", config.getDragonKnockbackAttack())) {
            availableAttacks.add("knockback");
        }
        if (canExecuteAttack("fireball", config.getDragonFireballAttack())) {
            availableAttacks.add("fireball");
        }

        if (availableAttacks.isEmpty()) return;

        // Pick a random attack
        String attack = availableAttacks.get(new Random().nextInt(availableAttacks.size()));

        switch (attack) {
            case "lightning" -> executeLightningAttack();
            case "breath" -> executeBreathAttack();
            case "knockback" -> executeKnockbackAttack();
            case "fireball" -> executeFireballAttack();
        }
    }

    private boolean canExecuteAttack(String name, EventsConfig.DragonAttackConfig attackConfig) {
        if (!attackConfig.enabled()) return false;
        if (currentPhase < attackConfig.minPhase()) return false;

        Long lastUse = attackCooldowns.get(name);
        if (lastUse != null) {
            long cooldownMs = attackConfig.cooldownSeconds() * 1000L;
            if (System.currentTimeMillis() - lastUse < cooldownMs) {
                return false;
            }
        }
        return true;
    }

    private void executeLightningAttack() {
        EventsConfig.DragonAttackConfig attackConfig = config.getDragonLightningAttack();
        attackCooldowns.put("lightning", System.currentTimeMillis());

        // Strike lightning at random players
        List<Player> targets = new ArrayList<>(endWorld.getPlayers());
        if (targets.isEmpty()) return;

        Player target = targets.get(new Random().nextInt(targets.size()));
        Location loc = target.getLocation();

        // Strike lightning
        endWorld.strikeLightningEffect(loc);

        // Damage nearby players
        for (Player player : endWorld.getPlayers()) {
            if (player.getLocation().distance(loc) <= attackConfig.secondaryValue()) {
                player.damage(attackConfig.damage());
            }
        }
    }

    private void executeBreathAttack() {
        EventsConfig.DragonAttackConfig attackConfig = config.getDragonBreathAttack();
        attackCooldowns.put("breath", System.currentTimeMillis());

        if (dragon == null) return;

        // Create area effect cloud at dragon location
        Location loc = dragon.getLocation().add(0, -5, 0);
        AreaEffectCloud cloud =
                endWorld.spawn(
                        loc,
                        AreaEffectCloud.class,
                        aec -> {
                            aec.setParticle(Particle.DRAGON_BREATH);
                            aec.setRadius(5.0f);
                            aec.setDuration((int) attackConfig.secondaryValue());
                            aec.setRadiusPerTick(-0.01f);
                            aec.setSource(dragon);
                        });
    }

    private void executeKnockbackAttack() {
        EventsConfig.DragonAttackConfig attackConfig = config.getDragonKnockbackAttack();
        attackCooldowns.put("knockback", System.currentTimeMillis());

        if (dragon == null) return;

        // Knockback all nearby players
        Location dragonLoc = dragon.getLocation();
        for (Player player : endWorld.getPlayers()) {
            double distance = player.getLocation().distance(dragonLoc);
            if (distance <= 15) {
                Vector direction =
                        player.getLocation()
                                .toVector()
                                .subtract(dragonLoc.toVector())
                                .normalize()
                                .multiply(attackConfig.secondaryValue())
                                .setY(0.8);
                player.setVelocity(direction);
                player.damage(attackConfig.damage());
            }
        }

        // Effects
        endWorld.playSound(dragonLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.5f);
    }

    private void executeFireballAttack() {
        EventsConfig.DragonAttackConfig attackConfig = config.getDragonFireballAttack();
        attackCooldowns.put("fireball", System.currentTimeMillis());

        if (dragon == null) return;

        // Shoot fireballs at players
        int count = (int) attackConfig.secondaryValue();
        List<Player> targets = new ArrayList<>(endWorld.getPlayers());
        if (targets.isEmpty()) return;

        for (int i = 0; i < count && !targets.isEmpty(); i++) {
            Player target = targets.get(new Random().nextInt(targets.size()));
            Location dragonLoc = dragon.getLocation();
            Vector direction =
                    target.getLocation().toVector().subtract(dragonLoc.toVector()).normalize();

            DragonFireball fireball = dragon.launchProjectile(DragonFireball.class, direction);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragonDamage(EntityDamageByEntityEvent event) {
        if (!active) return;
        if (!(event.getEntity() instanceof EnderDragon)) return;

        // Check if this is our dragon (by world, since reference might change)
        if (endWorld == null || !event.getEntity().getWorld().equals(endWorld)) return;

        // Get the damager (handle projectiles)
        Player damager = null;
        if (event.getDamager() instanceof Player player) {
            damager = player;
        } else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player player) {
                damager = player;
            }
        } else if (event.getDamager() instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player player) {
                damager = player;
            }
        }

        if (damager == null) return;

        // Track damage
        double damage = event.getFinalDamage();
        playerDamage.merge(damager.getUniqueId(), damage, Double::sum);
        totalDamage += damage;

        // Update killer reference (last person to hit)
        killer = damager;

        // Update dragon reference if needed
        if (dragon == null || dragon.isDead()) {
            dragon = (EnderDragon) event.getEntity();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!active) return;
        if (!(event.getEntity() instanceof EnderDragon)) return;

        // Check if this is our dragon (by world)
        if (endWorld == null || !event.getEntity().getWorld().equals(endWorld)) return;

        // Try to get killer from the event first
        Player eventKiller = event.getEntity().getKiller();
        if (eventKiller != null) {
            killer = eventKiller;
        }

        plugin.getLogger()
                .info(
                        "[DRAGON] Dragon died! Killer: "
                                + (killer != null ? killer.getName() : "null")
                                + ", Total damage tracked: "
                                + totalDamage
                                + ", Participants: "
                                + playerDamage.size());

        // Dragon died - process rewards
        processDragonDeath();
    }

    private void processDragonDeath() {
        // Find top damage dealer as fallback for killer
        UUID topDamagerUUID = null;
        double topDamageAmount = 0;
        for (Map.Entry<UUID, Double> entry : playerDamage.entrySet()) {
            if (entry.getValue() > topDamageAmount) {
                topDamageAmount = entry.getValue();
                topDamagerUUID = entry.getKey();
            }
        }

        // If killer is null, use top damage dealer
        if (killer == null && topDamagerUUID != null) {
            killer = Bukkit.getPlayer(topDamagerUUID);
        }

        // Announce victory
        String killerName = killer != null ? killer.getName() : "the brave warriors";
        String deathMessage = config.getDragonMessage("death");
        if (deathMessage.isEmpty()) {
            deathMessage = "&a&lVICTORY! &fThe dragon has been slain by &6{killer}&f!";
        }
        deathMessage = deathMessage.replace("{killer}", killerName);
        TextUtil.broadcastRaw(config.getPrefix() + deathMessage);

        // Effects
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        // Distribute rewards
        distributeRewards();

        // Stop the event
        stop();
    }

    private void distributeRewards() {
        if (totalDamage <= 0) {
            plugin.getLogger().warning("[DRAGON] No damage recorded, skipping rewards");
            TextUtil.broadcastRaw(
                    config.getPrefix() + "&cNo damage was recorded - no rewards distributed.");
            return;
        }

        plugin.getLogger()
                .info("[DRAGON] Distributing rewards to " + playerDamage.size() + " participants");

        // Find the top damage dealer
        UUID topDamager = null;
        double topDamageAmount = 0;
        for (Map.Entry<UUID, Double> entry : playerDamage.entrySet()) {
            if (entry.getValue() > topDamageAmount) {
                topDamageAmount = entry.getValue();
                topDamager = entry.getKey();
            }
        }

        // Build participant summary for broadcast
        List<String> rewardSummary = new ArrayList<>();
        int rewardedCount = 0;

        for (Map.Entry<UUID, Double> entry : playerDamage.entrySet()) {
            UUID uuid = entry.getKey();
            double damage = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);

            // Calculate damage percentage
            double damagePercent = (damage / totalDamage) * 100;

            if (player != null && player.isOnline()) {
                String playerName = player.getName();

                // Base coins: 2000 + 100 per percent damage
                int coins = 2000 + (int) (damagePercent * 100);

                // Determine reward tier and give keys based on contribution
                String keyRewardMsg = "";
                String tierName = "";
                boolean isKiller = killer != null && killer.getUniqueId().equals(uuid);
                boolean isTopDamager = uuid.equals(topDamager);

                if (isKiller || isTopDamager) {
                    // Top tier: Killer or highest damage dealer
                    coins += 5000; // Big bonus
                    Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(), "givekey " + playerName + " epic");
                    Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(), "dkey " + playerName + " hard");
                    keyRewardMsg = " &d+Epic Key &c+Hard Dungeon Key";
                    tierName = isKiller ? "&6[SLAYER]" : "&e[TOP DMG]";
                } else if (damagePercent >= 15) {
                    // High tier: 15%+ damage
                    coins += 2000;
                    Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(), "givekey " + playerName + " diversity");
                    Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(), "dkey " + playerName + " medium");
                    keyRewardMsg = " &b+Diversity Key &6+Medium Dungeon Key";
                    tierName = "&b[HIGH]";
                } else if (damagePercent >= 5) {
                    // Medium tier: 5%+ damage
                    coins += 1000;
                    Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(), "givekey " + playerName + " balanced");
                    Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(), "dkey " + playerName + " easy");
                    keyRewardMsg = " &a+Balanced Key &e+Easy Dungeon Key";
                    tierName = "&a[MED]";
                } else {
                    // Low tier: participated but < 5% damage
                    Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(), "dkey " + playerName + " easy");
                    keyRewardMsg = " &e+Easy Dungeon Key";
                    tierName = "&7[PART]";
                }

                // Give coins via economy
                EventReward coinReward = EventReward.of(coins, 0, "none");
                coinReward.give(plugin, player);

                // Send personal reward message
                String tierLabel =
                        isKiller ? "&6&lDRAGON SLAYER! " : (isTopDamager ? "&e&lTOP DAMAGE! " : "");
                TextUtil.send(
                        player,
                        config.getPrefix()
                                + tierLabel
                                + "&7You dealt &e"
                                + String.format("%.1f", damagePercent)
                                + "% &7damage and earned &6"
                                + coins
                                + " coins&7!"
                                + keyRewardMsg);

                // Add to summary
                rewardSummary.add(
                        tierName
                                + " &f"
                                + playerName
                                + " &7("
                                + String.format("%.1f", damagePercent)
                                + "%)");
                rewardedCount++;

                plugin.getLogger()
                        .info(
                                "[DRAGON] Rewarded "
                                        + playerName
                                        + ": "
                                        + coins
                                        + " coins, "
                                        + String.format("%.1f", damagePercent)
                                        + "% damage");
            } else {
                // Player offline - log it
                plugin.getLogger()
                        .info(
                                "[DRAGON] Player "
                                        + uuid
                                        + " dealt "
                                        + String.format("%.1f", damagePercent)
                                        + "% damage but is offline - no reward given");
            }
        }

        // Broadcast participant summary
        if (rewardedCount > 0) {
            TextUtil.broadcastRaw(
                    config.getPrefix() + "&a" + rewardedCount + " dragon slayers rewarded!");
            // Show top participants (max 5)
            int shown = 0;
            for (String entry : rewardSummary) {
                if (shown >= 5) {
                    int remaining = rewardSummary.size() - 5;
                    if (remaining > 0) {
                        TextUtil.broadcastRaw(
                                config.getPrefix() + "&7...and " + remaining + " more!");
                    }
                    break;
                }
                TextUtil.broadcastRaw(config.getPrefix() + entry);
                shown++;
            }
        }
    }

    @Override
    public void stop() {
        if (!active) return;

        active = false;

        // Cancel tasks
        if (phaseCheckTask != null) {
            phaseCheckTask.cancel();
            phaseCheckTask = null;
        }
        if (attackTask != null) {
            attackTask.cancel();
            attackTask = null;
        }
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        // Remove boss bar
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        // Remove the dragon entity if it still exists
        if (dragon != null && !dragon.isDead()) {
            // Reset max health to default before removing
            var maxHealthAttr = dragon.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(200.0); // Reset to vanilla default
            }
            dragon.remove();
        }

        // Unregister listeners
        HandlerList.unregisterAll(this);

        // Clear references
        dragon = null;
        endWorld = null;

        // Clear event reference
        plugin.getEventManager().clearActiveEvent();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public EnderDragon getDragon() {
        return dragon;
    }

    public int getCurrentPhase() {
        return currentPhase;
    }

    public Map<UUID, Double> getPlayerDamage() {
        return new HashMap<>(playerDamage);
    }

    public double getTotalDamage() {
        return totalDamage;
    }

    /** Broadcast a message with a clickable [JOIN FIGHT] button */
    private void broadcastWithTeleport(String message) {
        // Teleport to the obsidian platform (100, 49, 0) - standard End spawn point
        String tpCommand = "/tppos 100 49 0 " + config.getDragonWorld();

        Component textComponent =
                TextUtil.parse(message)
                        .append(Component.text(" "))
                        .append(
                                Component.text("[JOIN DRAGON FIGHT]")
                                        .color(NamedTextColor.LIGHT_PURPLE)
                                        .decorate(TextDecoration.BOLD)
                                        .clickEvent(
                                                net.kyori.adventure.text.event.ClickEvent
                                                        .runCommand(tpCommand))
                                        .hoverEvent(
                                                net.kyori.adventure.text.event.HoverEvent.showText(
                                                        Component.text(
                                                                        "Click to teleport to The End and fight the dragon!")
                                                                .color(NamedTextColor.YELLOW))));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(textComponent);
        }
    }
}
