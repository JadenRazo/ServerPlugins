package net.serverplugins.enchants.games.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.enchantments.EnchantTier;
import net.serverplugins.enchants.games.EnchantGame;
import net.serverplugins.enchants.games.GameType;
import net.serverplugins.enchants.models.RuneType;
import net.serverplugins.enchants.utils.RuneGenerator;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Forge game where players must replicate a pattern of runes by selecting from available runes. */
public class ForgeGame extends EnchantGame {

    private final List<RuneType> targetPattern;
    private final List<RuneType> workspace;
    private final List<RuneType> availableRunes;
    private final int patternLength;

    // Slot ranges
    private static final int TARGET_START = 1;
    private static final int WORKSPACE_START = 10;
    private static final int AVAILABLE_START = 19;

    public ForgeGame(ServerEnchants plugin, Player player, EnchantTier tier) {
        super(plugin, player, GameType.FORGE, tier, 27);

        this.patternLength = config.getForgeRunes(tier);
        this.workspace = new ArrayList<>();
        this.availableRunes = new ArrayList<>();

        // Generate target pattern
        List<RuneType> runePool = RuneGenerator.generateRunePool(Math.min(8, patternLength + 3));
        this.targetPattern = RuneGenerator.generateCode(patternLength, runePool);

        // Create available runes (target runes + extras shuffled)
        availableRunes.addAll(targetPattern);
        // Add some extra random runes from the pool
        for (int i = 0; i < 2 && availableRunes.size() < 7; i++) {
            availableRunes.add(runePool.get((int) (Math.random() * runePool.size())));
        }
        Collections.shuffle(availableRunes);
    }

    @Override
    protected void setupGame() {
        // Display target pattern (row 0, slots 1-7)
        for (int i = 0; i < patternLength && i < 7; i++) {
            RuneType rune = targetPattern.get(i);
            ItemStack item =
                    ItemBuilder.of(rune.getMaterial())
                            .name(rune.getColoredName())
                            .lore("<gray>Target #" + (i + 1), "", "<yellow>Replicate this pattern!")
                            .glow()
                            .build();
            setItem(TARGET_START + i, GuiItem.of(item));
        }

        // Initialize workspace (row 1, slots 10-16)
        updateWorkspace();

        // Display available runes (row 2, slots 19-25)
        updateAvailableRunes();

        // Submit button
        ItemStack submitButton =
                ItemBuilder.of(Material.EMERALD_BLOCK)
                        .name("<green><bold>CHECK PATTERN")
                        .lore(
                                "<gray>Click to verify your pattern",
                                "",
                                "<white>Workspace must match target exactly!")
                        .build();
        setItem(26, GuiItem.of(submitButton));

        // Info display
        updateInfoDisplay();

        // Border decoration
        ItemStack border = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        setItem(0, GuiItem.of(border));
        setItem(8, GuiItem.of(border));
        setItem(9, GuiItem.of(border));
        setItem(17, GuiItem.of(border));
        setItem(18, GuiItem.of(border));

        // Start timer
        startTimer(config.getForgeTimeLimit());
    }

    @Override
    public void handleGameClick(Player player, int slot, ClickType clickType) {
        if (gameOver) return;

        // Handle workspace clicks (remove rune)
        if (slot >= WORKSPACE_START && slot < WORKSPACE_START + 7) {
            int index = slot - WORKSPACE_START;
            if (index < workspace.size()) {
                RuneType removed = workspace.remove(index);
                availableRunes.add(removed);
                updateWorkspace();
                updateAvailableRunes();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            }
            return;
        }

        // Handle available rune clicks (add to workspace)
        if (slot >= AVAILABLE_START && slot < AVAILABLE_START + 7) {
            int index = slot - AVAILABLE_START;
            if (index < availableRunes.size() && workspace.size() < patternLength) {
                RuneType selected = availableRunes.remove(index);
                workspace.add(selected);
                updateWorkspace();
                updateAvailableRunes();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            return;
        }

        // Handle submit button
        if (slot == 26) {
            checkPattern(player);
        }
    }

    /** Check if the workspace pattern matches the target */
    private void checkPattern(Player player) {
        if (workspace.size() != targetPattern.size()) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        boolean matches = true;
        for (int i = 0; i < targetPattern.size(); i++) {
            if (workspace.get(i) != targetPattern.get(i)) {
                matches = false;
                break;
            }
        }

        if (matches) {
            score = 1;
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            endGame(true);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            endGame(false);
        }
    }

    /** Update the workspace display */
    private void updateWorkspace() {
        for (int i = 0; i < 7; i++) {
            int slot = WORKSPACE_START + i;
            if (i < workspace.size()) {
                RuneType rune = workspace.get(i);
                ItemStack item =
                        ItemBuilder.of(rune.getMaterial())
                                .name(rune.getColoredName())
                                .lore("<gray>Position: " + (i + 1), "", "<yellow>Click to remove")
                                .build();
                setItem(slot, GuiItem.of(item));
            } else if (i < patternLength) {
                ItemStack empty =
                        ItemBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                                .name("<gray>Empty Slot")
                                .lore("<gray>Add a rune from below")
                                .build();
                setItem(slot, GuiItem.of(empty));
            } else {
                removeItem(slot);
            }
        }
    }

    /** Update the available runes display */
    private void updateAvailableRunes() {
        for (int i = 0; i < 7; i++) {
            int slot = AVAILABLE_START + i;
            if (i < availableRunes.size()) {
                RuneType rune = availableRunes.get(i);
                ItemStack item =
                        ItemBuilder.of(rune.getMaterial())
                                .name(rune.getColoredName())
                                .lore("<gray>Click to add to workspace")
                                .build();
                setItem(slot, GuiItem.of(item));
            } else {
                removeItem(slot);
            }
        }
    }

    /** Update the info display */
    private void updateInfoDisplay() {
        ItemStack infoItem =
                ItemBuilder.of(Material.ANVIL)
                        .name("<gold><bold>Enchantment Forge")
                        .lore(
                                "<white>Pattern Length: <yellow>" + patternLength,
                                "<white>Workspace: <aqua>"
                                        + workspace.size()
                                        + "<gray>/"
                                        + patternLength,
                                "<white>Time: <aqua>" + timeRemaining + "s",
                                "",
                                "<gray>Replicate the pattern above!")
                        .build();

        setItem(4, GuiItem.of(infoItem));
    }
}
