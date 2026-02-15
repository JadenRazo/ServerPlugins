package net.serverplugins.enchants.games.impl;

import java.util.*;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.enchantments.EnchantTier;
import net.serverplugins.enchants.games.EnchantGame;
import net.serverplugins.enchants.games.GameType;
import net.serverplugins.enchants.models.Essence;
import net.serverplugins.enchants.models.EssenceRequirement;
import net.serverplugins.enchants.utils.RuneGenerator;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Alchemy game where players must combine essences to match required property values. */
public class AlchemyGame extends EnchantGame {

    private final EssenceRequirement requirement;
    private final List<Essence> availableEssences;
    private final List<Essence> cauldron;
    private int attemptsRemaining;
    private static final int MAX_ATTEMPTS = 3;

    // Slot ranges
    private static final int REQUIREMENT_START = 1;
    private static final int CAULDRON_START = 19;
    private static final int ESSENCES_START = 28;
    private static final int SUBMIT_SLOT = 40;

    public AlchemyGame(ServerEnchants plugin, Player player, EnchantTier tier) {
        super(plugin, player, GameType.ALCHEMY, tier, 45);

        int propertyCount = config.getAlchemyProperties(tier);
        int essenceCount = config.getAlchemyEssences(tier);

        this.cauldron = new ArrayList<>();
        this.attemptsRemaining = MAX_ATTEMPTS;

        // Generate puzzle
        Map<String, Object> puzzle =
                RuneGenerator.generateAlchemyPuzzle(essenceCount, propertyCount);
        this.requirement = (EssenceRequirement) puzzle.get("requirement");
        this.availableEssences = (List<Essence>) puzzle.get("essences");
    }

    @Override
    protected void setupGame() {
        // Display requirements (top rows)
        displayRequirements();

        // Display cauldron (middle row)
        updateCauldron();

        // Display available essences (bottom rows)
        updateAvailableEssences();

        // Submit button
        updateSubmitButton();

        // Info display
        updateInfoDisplay();

        // Border decoration
        ItemStack border = ItemBuilder.of(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build();
        fillBorder(GuiItem.of(border));

        // Start timer
        startTimer(config.getAlchemyTimeLimit());
    }

    @Override
    public void handleGameClick(Player player, int slot, ClickType clickType) {
        if (gameOver) return;

        // Handle cauldron clicks (remove essence)
        if (slot >= CAULDRON_START && slot < CAULDRON_START + 7) {
            int index = slot - CAULDRON_START;
            if (index < cauldron.size()) {
                Essence removed = cauldron.remove(index);
                availableEssences.add(removed);
                updateCauldron();
                updateAvailableEssences();
                displayRequirements(); // Update current totals
                player.playSound(player.getLocation(), Sound.ITEM_BOTTLE_EMPTY, 1.0f, 1.0f);
            }
            return;
        }

        // Handle essence clicks (add to cauldron)
        if (slot >= ESSENCES_START && slot < ESSENCES_START + 14) {
            int index = slot - ESSENCES_START;
            if (index < availableEssences.size()) {
                Essence selected = availableEssences.remove(index);
                cauldron.add(selected);
                updateCauldron();
                updateAvailableEssences();
                displayRequirements(); // Update current totals
                player.playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 1.0f, 1.2f);
            }
            return;
        }

        // Handle submit button
        if (slot == SUBMIT_SLOT) {
            checkSolution(player);
        }
    }

    /** Display the required property values and current totals */
    private void displayRequirements() {
        Map<String, Integer> currentTotals = calculateCurrentTotals();
        List<String> properties = new ArrayList<>(requirement.getRequired().keySet());

        for (int i = 0; i < properties.size() && i < 5; i++) {
            String property = properties.get(i);
            int required = requirement.getRequiredValue(property);
            int current = currentTotals.getOrDefault(property, 0);

            String statusColor = (current == required) ? "<green>" : "<yellow>";
            ItemStack item =
                    ItemBuilder.of(Material.POTION)
                            .name("<aqua>" + property)
                            .lore(
                                    "<white>Required: <gold>" + required,
                                    "<white>Current: " + statusColor + current,
                                    "",
                                    current == required
                                            ? "<green>✓ Matched!"
                                            : "<gray>Add essences to adjust")
                            .customModelData(i + 1)
                            .build();

            setItem(REQUIREMENT_START + i * 2, GuiItem.of(item));
        }
    }

    /** Calculate current property totals from cauldron */
    private Map<String, Integer> calculateCurrentTotals() {
        Map<String, Integer> totals = new HashMap<>();
        for (Essence essence : cauldron) {
            for (Map.Entry<String, Integer> entry : essence.getProperties().entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return totals;
    }

    /** Update the cauldron display */
    private void updateCauldron() {
        for (int i = 0; i < 7; i++) {
            int slot = CAULDRON_START + i;
            if (i < cauldron.size()) {
                Essence essence = cauldron.get(i);
                List<String> lore = new ArrayList<>();
                lore.add("<gray>Properties:");
                for (Map.Entry<String, Integer> entry : essence.getProperties().entrySet()) {
                    String sign = entry.getValue() >= 0 ? "+" : "";
                    lore.add("  <white>" + entry.getKey() + ": <gold>" + sign + entry.getValue());
                }
                lore.add("");
                lore.add("<yellow>Click to remove");

                ItemStack item =
                        ItemBuilder.of(essence.getMaterial())
                                .name(essence.getColor() + essence.getName())
                                .lore(lore.toArray(new String[0]))
                                .build();
                setItem(slot, GuiItem.of(item));
            } else {
                ItemStack empty =
                        ItemBuilder.of(Material.GLASS_BOTTLE)
                                .name("<gray>Empty")
                                .lore("<gray>Add essence from below")
                                .build();
                setItem(slot, GuiItem.of(empty));
            }
        }
    }

    /** Update the available essences display */
    private void updateAvailableEssences() {
        for (int i = 0; i < 14; i++) {
            int slot = ESSENCES_START + i;
            if (i < availableEssences.size()) {
                Essence essence = availableEssences.get(i);
                List<String> lore = new ArrayList<>();
                lore.add("<gray>Properties:");
                for (Map.Entry<String, Integer> entry : essence.getProperties().entrySet()) {
                    String sign = entry.getValue() >= 0 ? "+" : "";
                    lore.add("  <white>" + entry.getKey() + ": <gold>" + sign + entry.getValue());
                }
                lore.add("");
                lore.add("<yellow>Click to add to cauldron");

                ItemStack item =
                        ItemBuilder.of(essence.getMaterial())
                                .name(essence.getColor() + essence.getName())
                                .lore(lore.toArray(new String[0]))
                                .build();
                setItem(slot, GuiItem.of(item));
            } else {
                removeItem(slot);
            }
        }
    }

    /** Update the submit button */
    private void updateSubmitButton() {
        ItemStack submitButton =
                ItemBuilder.of(Material.EMERALD_BLOCK)
                        .name("<green><bold>SUBMIT SOLUTION")
                        .lore(
                                "<gray>Click to check your mixture",
                                "",
                                "<white>Attempts: <yellow>"
                                        + attemptsRemaining
                                        + "<gray>/"
                                        + MAX_ATTEMPTS)
                        .build();
        setItem(SUBMIT_SLOT, GuiItem.of(submitButton));
    }

    /** Update the info display */
    private void updateInfoDisplay() {
        ItemStack infoItem =
                ItemBuilder.of(Material.BREWING_STAND)
                        .name("<gold><bold>Alchemical Synthesis")
                        .lore(
                                "<white>Essences in Cauldron: <aqua>" + cauldron.size(),
                                "<white>Attempts: <yellow>"
                                        + attemptsRemaining
                                        + "<gray>/"
                                        + MAX_ATTEMPTS,
                                "<white>Time: <aqua>" + timeRemaining + "s",
                                "",
                                "<gray>Match all property values!")
                        .build();

        setItem(13, GuiItem.of(infoItem));
    }

    /** Check if the current cauldron mixture matches the requirement */
    private void checkSolution(Player player) {
        Map<String, Integer> currentTotals = calculateCurrentTotals();
        boolean correct = requirement.isSatisfiedBy(currentTotals);

        if (correct) {
            // Calculate score based on essences used (fewer is better)
            score = Math.max(1, requirement.getRequired().size() - cauldron.size() + 1);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            endGame(true);
        } else {
            attemptsRemaining--;
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

            if (attemptsRemaining <= 0) {
                endGame(false);
            } else {
                updateSubmitButton();
                updateInfoDisplay();
            }
        }
    }
}
