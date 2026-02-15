package net.serverplugins.events.events.pinata;

import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.events.ServerEvents;
import net.serverplugins.events.events.EventReward;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Standard pinata event - spawn a glowing mob at spawn that players punch to break. Rewards are
 * distributed based on participation with a bonus for the final hit.
 */
public class PinataEvent extends BasePinataEvent {

    public PinataEvent(ServerEvents plugin) {
        super(plugin);
    }

    @Override
    public int getMinimumPlayers() {
        return config.getPinataMinPlayers();
    }

    @Override
    public EventType getType() {
        return EventType.PINATA;
    }

    @Override
    public String getDisplayName() {
        return "Pinata";
    }

    @Override
    protected Location getSpawnLocation() {
        return config.getSpawnLocation();
    }

    @Override
    protected int calculateClicksRequired() {
        int min = config.getPinataClicksMin();
        int max = config.getPinataClicksMax();
        return min + random.nextInt(max - min + 1);
    }

    @Override
    protected EntityType getPinataEntityType() {
        return config.getPinataEntity();
    }

    @Override
    protected org.bukkit.ChatColor getGlowColor() {
        return org.bukkit.ChatColor.YELLOW;
    }

    @Override
    protected String getGlowTeamName() {
        return "pinata_glow";
    }

    @Override
    protected String formatPinataName(int clicks, int required) {
        String baseName =
                "<gold><bold>PINATA</bold></gold> <white>[" + clicks + "/" + required + "]</white>";
        // Add player name when using player skin mode
        if (targetPlayerName != null && !targetPlayerName.isEmpty()) {
            return baseName + " <gray>(" + targetPlayerName + ")</gray>";
        }
        return baseName;
    }

    @Override
    protected Material getArmorMaterial() {
        return Material.LEATHER_CHESTPLATE;
    }

    @Override
    protected int getTimeoutSeconds() {
        return 90;
    }

    @Override
    protected String getTeleportCommand() {
        return "/spawn";
    }

    @Override
    protected NamedTextColor getTeleportColor() {
        return NamedTextColor.GREEN;
    }

    @Override
    protected BossBar.Color getBarColor() {
        return BossBar.Color.YELLOW;
    }

    @Override
    protected void broadcastStartMessages() {
        TextUtil.broadcastRaw(
                MessageBuilder.create()
                        .prefix(config.getMessenger().getPrefix())
                        .star()
                        .emphasis("PINATA EVENT!")
                        .build());

        TextUtil.broadcastRaw(
                MessageBuilder.create()
                        .prefix(config.getMessenger().getPrefix())
                        .warning("A pinata has spawned at spawn! Break it by punching it ")
                        .emphasis(String.valueOf(clicksRequired))
                        .warning(" times!")
                        .build());

        TextUtil.broadcastRaw(
                MessageBuilder.create()
                        .prefix(config.getMessenger().getPrefix())
                        .success("Reward: ")
                        .emphasis("$" + String.format("%,d", config.getPinataTotalCoins()))
                        .success(" coins distributed based on your hits!")
                        .build());

        TextUtil.broadcastRaw(
                MessageBuilder.create()
                        .prefix(config.getMessenger().getPrefix())
                        .warning("The final hit gets a ")
                        .emphasis("bonus")
                        .warning("!")
                        .build());

        String message = config.getPinataMessage("spawn");
        if (targetPlayerName != null) {
            message = message.replace("%target%", targetPlayerName);
            broadcastWithTeleport(config.getPrefix() + message);
            TextUtil.broadcastRaw(
                    MessageBuilder.create()
                            .prefix(config.getMessenger().getPrefix())
                            .warning("It looks like ")
                            .emphasis(targetPlayerName)
                            .warning("! Punch them!")
                            .build());
        } else {
            broadcastWithTeleport(config.getPrefix() + message);
        }
    }

    @Override
    protected void broadcastBreakMessages() {
        if (breaker != null) {
            String message =
                    config.getPinataMessage("broken").replace("%player%", breaker.getName());
            TextUtil.broadcastRaw(config.getPrefix() + message);
        }
    }

    @Override
    protected void distributeRewards() {
        int totalHits = getTotalPlayerClicks();

        if (totalHits == 0) {
            plugin.getLogger().warning("[PINATA] No hits recorded, skipping rewards");
            return;
        }

        // Cache key chance to avoid redundant config lookups
        double baseKeyChance = config.getPinataKeyChance();
        String keyType = config.getPinataKeyType();

        for (Map.Entry<UUID, Integer> entry : getPlayerClicks().entrySet()) {
            UUID uuid = entry.getKey();
            int hits = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);

            if (player != null && player.isOnline()) {
                // Calculate proportional share based on hits
                double hitPercentage = (double) hits / totalHits;
                int proportionalCoins = (int) (config.getPinataProportionalPool() * hitPercentage);

                // Add breaker bonus if this is the player who broke it
                int totalCoins = proportionalCoins;
                boolean isBreaker = player.equals(breaker);
                if (isBreaker) {
                    totalCoins += config.getPinataBreakerBonus();
                }

                // Add combo bonus from hit streaks
                int comboBonus = getComboBonus(uuid);
                totalCoins += comboBonus;

                // Create reward with coins and crate keys
                EventReward reward =
                        EventReward.of(
                                totalCoins, isBreaker ? baseKeyChance : baseKeyChance / 2, keyType);

                // Give reward (but don't send default message - we send custom)
                if (plugin.hasEconomy()) {
                    plugin.getEconomy().depositPlayer(player, totalCoins);
                }

                // Roll for key manually since we want custom message
                if (baseKeyChance > 0) {
                    double chance = isBreaker ? baseKeyChance : baseKeyChance / 2;
                    if (random.nextDouble() < chance) {
                        Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                "crates key give " + player.getName() + " " + keyType + " 1");
                        TextUtil.send(
                                player,
                                MessageBuilder.create()
                                        .prefix(config.getMessenger().getPrefix())
                                        .emphasis("LUCKY!")
                                        .warning(" You received a ")
                                        .emphasis(keyType)
                                        .warning(" key!")
                                        .build());
                    }
                }

                // Send detailed message to player (include combo bonus if earned)
                String bonusText = comboBonus > 0 ? " + " + comboBonus + " combo bonus" : "";
                if (isBreaker) {
                    TextUtil.send(
                            player,
                            MessageBuilder.create()
                                    .prefix(config.getMessenger().getPrefix())
                                    .success("You broke the pinata! ")
                                    .emphasis("+" + totalCoins + " coins")
                                    .append(
                                            " <gray>("
                                                    + hits
                                                    + " hits + final hit bonus"
                                                    + bonusText
                                                    + ")</gray>")
                                    .build());
                } else {
                    TextUtil.send(
                            player,
                            MessageBuilder.create()
                                    .prefix(config.getMessenger().getPrefix())
                                    .success("You hit the pinata! ")
                                    .emphasis("+" + totalCoins + " coins")
                                    .append(" <gray>(" + hits + " hits" + bonusText + ")</gray>")
                                    .build());
                }
            }
        }
    }
}
