package net.serverplugins.arcade.games.global;

import java.util.List;
import java.util.Map;
import net.serverplugins.arcade.gui.ArcadeGui;
import net.serverplugins.arcade.gui.BetMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** Base menu for global multiplayer games. */
public abstract class GlobalGameMenu extends ArcadeGui {

    protected final GlobalGameType gameType;
    protected int[] headSlots = {
        1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31,
        32, 33, 34
    };
    protected int[] betButtonSlots = {49, 50, 51};

    public GlobalGameMenu(GlobalGameType gameType) {
        super(gameType.getPlugin(), gameType.getGuiTitle(), gameType.getGuiSize());
        this.gameType = gameType;
    }

    @Override
    protected void build() {
        // No glass pane fillers - clean GUI with inventory hiding via packets

        // Display player heads
        displayPlayerHeads();

        // Build state-specific elements
        switch (gameType.getGameState()) {
            case WAITING -> buildWaitingState();
            case BETTING -> buildBettingState();
            case RUNNING -> buildRunningState();
        }
    }

    @Override
    protected boolean shouldRebuildHandlers() {
        // Only rebuild handlers when state changes, not during timer updates
        return false;
    }

    protected void displayPlayerHeads() {
        Map<Player, Integer> players = gameType.getPlayers();
        int index = 0;

        for (Map.Entry<Player, Integer> entry : players.entrySet()) {
            if (index >= headSlots.length) break;

            Player p = entry.getKey();
            int bet = entry.getValue();
            double chance = gameType.getWinChance(p);

            ItemStack head = createPlayerHead(p, bet, chance);
            inventory.setItem(headSlots[index], head);
            index++;
        }
    }

    protected ItemStack createPlayerHead(Player player, int bet, double chance) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName("§b" + player.getName());
            meta.setLore(
                    List.of(
                            "§7Chance to win: §b" + String.format("%.1f%%", chance),
                            "§7Bet value: §e" + formatMoney(bet)));
            head.setItemMeta(meta);
        }
        return head;
    }

    protected void buildWaitingState() {
        ItemStack waitItem =
                createButton(
                        1,
                        "§eWaiting for next round...",
                        List.of(
                                "§7Next round starts in:",
                                "§f" + formatTime(gameType.getTimeLeft())));
        setItems(betButtonSlots, waitItem, null);
    }

    protected void buildBettingState() {
        boolean hasBet = gameType.hasPlayer(player);

        if (hasBet) {
            int bet = gameType.getPlayerBet(player);
            ItemStack removeBetItem =
                    createButton(
                            1,
                            "§f",
                            List.of(
                                    "§7Leave the game and",
                                    "§7get your money back.",
                                    "§f",
                                    "§f ▪ Your bet: §6" + formatMoney(bet),
                                    "§f ▪ Win chance: §b"
                                            + String.format(
                                                    "%.1f%%", gameType.getWinChance(player)),
                                    "§f",
                                    "§f §eClick to remove your bet!"));
            setItems(
                    betButtonSlots,
                    removeBetItem,
                    e -> {
                        gameType.removeBet(player);
                        update();
                    });
        } else {
            ItemStack betItem =
                    createButton(
                            1,
                            "§f",
                            List.of(
                                    "§7Join the game that is",
                                    "§7about to start and bet",
                                    "§7an amount of money.",
                                    "§f",
                                    "§f ▪ Total pool: §6" + formatMoney(gameType.getTotalBets()),
                                    "§f ▪ Players: §e" + gameType.getPlayers().size(),
                                    "§f ▪ Time left: §f" + formatTime(gameType.getTimeLeft()),
                                    "§f",
                                    "§f §eClick to make a bet!"));
            setItems(betButtonSlots, betItem, e -> openBetMenu());
        }
    }

    protected void buildRunningState() {
        // Override in subclass for spinning animation
    }

    protected void openBetMenu() {
        new BetMenu(
                        plugin,
                        gameType,
                        (p, amount) -> {
                            gameType.addBet(p, amount);
                            open(p);
                        })
                .open(player);
    }

    protected ItemStack createButton(int customModelData, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    protected String formatMoney(int amount) {
        if (amount >= 1000000) {
            return String.format("%.1fM", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        }
        return String.valueOf(amount);
    }

    protected String formatTime(int seconds) {
        if (seconds >= 60) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        return seconds + "s";
    }
}
