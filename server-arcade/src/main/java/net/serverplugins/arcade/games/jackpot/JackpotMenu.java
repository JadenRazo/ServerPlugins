package net.serverplugins.arcade.games.jackpot;

import java.util.List;
import net.serverplugins.arcade.games.global.GlobalGameMenu;
import org.bukkit.inventory.ItemStack;

/** Menu for the Jackpot game. */
public class JackpotMenu extends GlobalGameMenu {

    private final JackpotType jackpotType;

    public JackpotMenu(JackpotType jackpotType) {
        super(jackpotType);
        this.jackpotType = jackpotType;
        this.headSlots =
                new int[] {
                    1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34
                };
    }

    @Override
    protected void buildRunningState() {
        // During running state, the spin animation handles the display
        // Show winner indicator at winning slot
        ItemStack winnerIndicator =
                createButton(1, "§6Winner", List.of("§7The winner will be shown here!"));
        inventory.setItem(jackpotType.getWinningHeadSlot(), winnerIndicator);

        // Disable bet buttons during animation
        ItemStack waitItem =
                createButton(1, "§eSpinning...", List.of("§7Winner is being selected!"));
        setItems(betButtonSlots, waitItem, null);
    }
}
