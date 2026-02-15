package net.serverplugins.arcade.games.jackpot;

import java.util.List;
import java.util.Map;
import net.milkbowl.vault.economy.Economy;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.global.GlobalGameMenu;
import net.serverplugins.arcade.games.global.GlobalGameType;
import net.serverplugins.arcade.utils.RandomList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Jackpot game - multiplayer weighted lottery. Players bet money into a pool, winner takes all
 * based on weighted chance.
 *
 * <p>Supports: - Progressive jackpots (rollover if min_pot not met) - House edge (configurable
 * winner percentage) - Timed announcements (5min, 1min, 30sec warnings)
 */
public class JackpotType extends GlobalGameType {

    // Animation settings
    private int animationDuration = 120;
    private int animationSpeed = 6;
    private List<Integer> spinHeadSlots;
    private int winningHeadSlot = 4;

    // New settings
    private boolean sendMessageToEveryone = false;
    private int minPot = 500;
    private boolean progressive = false;
    private int progressiveJackpot = 0;
    private double houseEdge = 0.10; // 10% default
    private double winnerTakes = 0.90; // 90% to winner
    private double houseTakes = 0.10; // 10% to house

    // Announcements
    private boolean announce5min = false;
    private boolean announce1min = false;
    private boolean announce30sec = false;
    private boolean announced5min = false;
    private boolean announced1min = false;
    private boolean announced30sec = false;

    private RandomList<ItemStack> weightedHeads;

    public JackpotType(ServerArcade plugin) {
        super(plugin, "Jackpot", "JACKPOT");
        this.guiSize = 54;

        // Default spin head slots (circular arrangement)
        spinHeadSlots = List.of(3, 4, 5, 15, 24, 32, 31, 30, 20, 11);
    }

    @Override
    protected void onConfigLoad(ConfigurationSection config) {
        // Load GUI titles from parent (betting_gui, waiting_gui, gui)
        super.onConfigLoad(config);

        animationDuration = config.getInt("animation_duration", animationDuration);
        sendMessageToEveryone = config.getBoolean("send_message_to_everyone", false);

        // Progressive jackpot settings
        minPot = config.getInt("min_pot", minPot);
        progressive = config.getBoolean("progressive", false);

        // House edge settings
        houseEdge = config.getDouble("house_edge", houseEdge);
        winnerTakes = config.getDouble("winner_takes", winnerTakes);
        houseTakes = config.getDouble("house_takes", houseTakes);

        // Announcement settings
        announce5min = config.getBoolean("announce_5min", false);
        announce1min = config.getBoolean("announce_1min", false);
        announce30sec = config.getBoolean("announce_30sec", false);

        ConfigurationSection gui = config.getConfigurationSection("gui");
        if (gui != null) {
            winningHeadSlot = gui.getInt("winning_head_slot", winningHeadSlot);

            List<Integer> slots = gui.getIntegerList("head_slots");
            if (!slots.isEmpty()) {
                spinHeadSlots = slots;
            }
        }
    }

    @Override
    protected void startBetting() {
        super.startBetting();
        // Reset announcement flags for new round
        announced5min = false;
        announced1min = false;
        announced30sec = false;
    }

    @Override
    protected boolean startGame() {
        // Check minimum pot requirement
        int totalPool = totalBets + progressiveJackpot;
        if (totalPool < minPot && progressive) {
            // Progressive: carry over to next round
            progressiveJackpot += totalBets;
            String message =
                    "§e§lJackpot pot too small! §7$"
                            + formatMoney(totalBets)
                            + " §7added to progressive pool. Next pot: §6$"
                            + formatMoney(progressiveJackpot);
            Bukkit.broadcastMessage(message);
            refundAll();
            gameState = State.WAITING;
            timeLeft = timeBetweenGames;
            refreshMenu();
            return false;
        }

        // Check minimum players (from parent - but allow solo if configured)
        if (players.size() < getMinPlayers()) {
            refundAll();
            gameState = State.WAITING;
            timeLeft = timeBetweenGames;
            refreshMenu();
            return false;
        }

        gameState = State.RUNNING;

        // Build weighted list of player heads
        weightedHeads = new RandomList<>();
        for (Map.Entry<Player, Integer> entry : players.entrySet()) {
            ItemStack head = createPlayerHead(entry.getKey());
            weightedHeads.addElement(head, entry.getValue());
        }

        // Refresh all players' menus
        for (Player p : players.keySet()) {
            if (gameMenu != null) {
                gameMenu.open(p);
            }
        }

        // Start spin animation
        animationSpeed = 6;
        spin();

        // Slow down animation
        for (int j = 0; j < 3; j++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> animationSpeed--, j * 4L);
        }

        // Speed up then stop
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            for (int j = 0; j < 3; j++) {
                                Bukkit.getScheduler()
                                        .runTaskLater(plugin, () -> animationSpeed++, j * 20L);
                            }
                        },
                        animationDuration);

        Bukkit.getScheduler()
                .runTaskLater(plugin, () -> animationSpeed = 0, animationDuration + 60L);

        // Determine winner after animation
        Bukkit.getScheduler().runTaskLater(plugin, this::onGameEnd, animationDuration + 80L);

        return true;
    }

    private void spin() {
        if (gameMenu == null || weightedHeads == null) return;

        ItemStack nextHead = weightedHeads.getRandomElement();

        // Shift heads through the slots
        for (int i = spinHeadSlots.size() - 1; i > 0; i--) {
            ItemStack prev = gameMenu.getInventory().getItem(spinHeadSlots.get(i - 1));
            gameMenu.getInventory().setItem(spinHeadSlots.get(i), prev);
        }
        gameMenu.getInventory().setItem(spinHeadSlots.get(0), nextHead);

        // Play tick sound for all players
        for (Player p : players.keySet()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.1f, 0.5f);
        }

        // Continue if still animating
        if (animationSpeed > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, this::spin, animationSpeed);
        }
    }

    private void onGameEnd() {
        if (gameMenu == null) return;

        // Get winning head from the winning slot
        ItemStack winningHead = gameMenu.getInventory().getItem(winningHeadSlot);
        if (winningHead == null || !(winningHead.getItemMeta() instanceof SkullMeta meta)) {
            // Fallback: refund all
            refundAll();
            gameState = State.WAITING;
            timeLeft = timeBetweenGames;
            return;
        }

        Player winner = meta.getOwningPlayer() != null ? meta.getOwningPlayer().getPlayer() : null;

        if (winner == null) {
            refundAll();
            gameState = State.WAITING;
            timeLeft = timeBetweenGames;
            return;
        }

        // Calculate payouts with house edge
        int totalPool = totalBets + progressiveJackpot;
        int houseCut = (int) (totalPool * houseTakes);
        int winnerPayout = totalPool - houseCut;

        // Pay winner
        Economy economy = ServerArcade.getEconomy();
        if (economy != null) {
            economy.depositPlayer(winner, winnerPayout);
        }

        // Track statistics for all players
        int winnerBet = players.getOrDefault(winner, 0);
        if (plugin.getStatisticsTracker() != null) {
            plugin.getStatisticsTracker()
                    .recordLotteryWin(
                            winner.getUniqueId(),
                            winner.getName(),
                            winnerBet,
                            winnerPayout,
                            this.getName());

            // Track losses for other players
            for (Map.Entry<Player, Integer> entry : players.entrySet()) {
                if (!entry.getKey().equals(winner)) {
                    plugin.getStatisticsTracker()
                            .recordLotteryLoss(
                                    entry.getKey().getUniqueId(),
                                    entry.getKey().getName(),
                                    entry.getValue(),
                                    this.getName());
                }
            }
        }

        // Discord webhook for big wins
        if (progressive || totalPool > 5000) {
            plugin.getDiscordWebhook()
                    .sendLotteryWin(
                            winner.getName(),
                            this.getName(),
                            winnerBet,
                            winnerPayout,
                            players.size());
        }

        // Announce winner
        String message =
                "§6§l"
                        + this.getName().toUpperCase()
                        + "! §e"
                        + winner.getName()
                        + " §7won §6$"
                        + formatMoney(winnerPayout)
                        + "§7!";
        if (progressiveJackpot > 0) {
            message += " §7(including §e$" + formatMoney(progressiveJackpot) + " §7progressive)";
        }

        if (sendMessageToEveryone) {
            Bukkit.broadcastMessage(message);
        } else {
            for (Player p : players.keySet()) {
                p.sendMessage(message);
            }
        }

        // Special message for winner
        winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        winner.sendMessage(
                "§a§lCongratulations! §7You won §6$"
                        + formatMoney(winnerPayout)
                        + " §7from "
                        + players.size()
                        + " players!");

        // Reset for next round
        players.clear();
        totalBets = 0;
        progressiveJackpot = 0; // Reset progressive after win
        gameState = State.WAITING;

        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            timeLeft = timeBetweenGames;
                        },
                        100L);
    }

    /** Override timer to add announcement support. */
    @Override
    public void start() {
        if (timerTask != null) {
            timerTask.cancel();
        }

        gameState = State.WAITING;
        timeLeft = timeBetweenGames;

        timerTask =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    // Handle announcements for betting phase
                                    if (gameState == State.BETTING) {
                                        if (announce5min && !announced5min && timeLeft == 300) {
                                            announced5min = true;
                                            Bukkit.broadcastMessage(
                                                    "§c§l"
                                                            + this.getName().toUpperCase()
                                                            + " IN 5 MINUTES! §7Current pot: §6$"
                                                            + formatMoney(
                                                                    totalBets
                                                                            + progressiveJackpot));
                                        }
                                        if (announce1min && !announced1min && timeLeft == 60) {
                                            announced1min = true;
                                            Bukkit.broadcastMessage(
                                                    "§c§l"
                                                            + this.getName().toUpperCase()
                                                            + " IN 1 MINUTE! §7Current pot: §6$"
                                                            + formatMoney(
                                                                    totalBets
                                                                            + progressiveJackpot));
                                        }
                                        if (announce30sec && !announced30sec && timeLeft == 30) {
                                            announced30sec = true;
                                            Bukkit.broadcastMessage(
                                                    "§c§l30 SECONDS TO "
                                                            + this.getName().toUpperCase()
                                                            + "! §7Current pot: §6$"
                                                            + formatMoney(
                                                                    totalBets
                                                                            + progressiveJackpot));
                                        }
                                    }

                                    timeLeft--;

                                    if (timeLeft <= 0) {
                                        switch (gameState) {
                                            case WAITING -> startBetting();
                                            case BETTING -> startGame();
                                            case RUNNING -> {} // Game handles its own end
                                        }
                                    }

                                    // Update menu if open
                                    if (gameMenu != null) {
                                        gameMenu.refreshDisplay();
                                    }
                                },
                                20L,
                                20L); // Every second
    }

    /** Get minimum players required (from config or default to 1 for solo lottery). */
    private int getMinPlayers() {
        return plugin.getConfig().getInt(configKey + ".min_players", 1);
    }

    private ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName("§b" + player.getName());
            int bet = players.getOrDefault(player, 0);
            double chance = getWinChance(player);
            meta.setLore(
                    List.of(
                            "§7Chance to win: §b" + String.format("%.1f%%", chance),
                            "§7Bet value: §e" + formatMoney(bet)));
            head.setItemMeta(meta);
        }
        return head;
    }

    @Override
    protected GlobalGameMenu createMenu() {
        return new JackpotMenu(this);
    }

    private String formatMoney(int amount) {
        if (amount >= 1000000) {
            return String.format("%.1fM", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        }
        return String.valueOf(amount);
    }

    // Getters
    public List<Integer> getSpinHeadSlots() {
        return spinHeadSlots;
    }

    public int getWinningHeadSlot() {
        return winningHeadSlot;
    }
}
