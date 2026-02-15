package net.serverplugins.arcade.gui;

import net.milkbowl.vault.economy.Economy;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameType;
import net.serverplugins.arcade.machines.Machine;
import net.serverplugins.arcade.machines.OnePlayerMachine;
import org.bukkit.event.inventory.InventoryCloseEvent;

/** Base class for game GUIs (slots, blackjack, etc). */
public abstract class GameGui extends ArcadeGui {

    protected final GameType gameType;
    protected final Machine machine;
    protected int bet;
    protected boolean gameActive = false;

    public GameGui(GameType gameType, Machine machine, int bet) {
        super(gameType.getPlugin(), gameType.getGuiTitle(), gameType.getGuiSize());
        this.gameType = gameType;
        this.machine = machine;
        this.bet = bet;
    }

    public GameGui(GameType gameType, Machine machine) {
        this(gameType, machine, gameType.getDefaultBet());
    }

    /** Check if player can afford the bet. */
    protected boolean canAffordBet() {
        Economy economy = ServerArcade.getEconomy();
        if (economy == null) return false;
        return economy.getBalance(player) >= bet;
    }

    /** Withdraw the bet amount from player. */
    protected boolean withdrawBet() {
        Economy economy = ServerArcade.getEconomy();
        if (economy == null) return false;
        if (!canAffordBet()) {
            TextUtil.send(
                    player,
                    gameType.getMessage("not_enough_money", "&cYou don't have enough money!"));
            return false;
        }
        economy.withdrawPlayer(player, bet);
        return true;
    }

    /** Deposit winnings to player. */
    protected void depositWinnings(double amount) {
        Economy economy = ServerArcade.getEconomy();
        if (economy != null && amount > 0) {
            economy.depositPlayer(player, amount);
        }
    }

    /** Open the bet selection menu. */
    protected void openBetMenu() {
        new BetMenu(plugin, gameType, this).open(player);
    }

    @Override
    protected void onClose(InventoryCloseEvent event) {
        // Release the machine state
        if (machine != null) {
            // Clear the machine's current player state
            if (machine instanceof OnePlayerMachine opm) {
                opm.close();
            }

            // Note: Player stays seated until they press SHIFT to exit
            // The MachineListener handles dismount control via VehicleExitEvent
        }
    }

    public GameType getGameType() {
        return gameType;
    }

    public Machine getMachine() {
        return machine;
    }

    public int getBet() {
        return bet;
    }

    public void setBet(int bet) {
        this.bet = bet;
    }

    public boolean isGameActive() {
        return gameActive;
    }
}
