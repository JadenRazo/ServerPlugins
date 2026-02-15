package net.serverplugins.arcade.games.blackjack;

import java.util.*;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.duo.DuoGame;
import net.serverplugins.arcade.games.duo.DuoGameType;
import net.serverplugins.arcade.machines.Machine;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Blackjack game type - 2 player competitive blackjack. */
public class BlackjackType extends DuoGameType {

    // Card configuration
    private final Map<String, Card> cards = new HashMap<>();
    private ItemStack cardBackItem;

    public BlackjackType(ServerArcade plugin) {
        super(plugin, "Blackjack", "BLACKJACK");
        this.guiSize = 45;
        this.challengeTime = 120;
        this.gameTime = 60;

        // Load default cards
        loadDefaultCards();
    }

    @Override
    protected void onConfigLoad(ConfigurationSection config) {
        super.onConfigLoad(config);

        // GUI title loaded from config by parent class
        // Don't override - let config YAML handle it

        // Load cards from config
        ConfigurationSection cardsSection = config.getConfigurationSection("cards");
        if (cardsSection != null) {
            cards.clear();
            for (String cardId : cardsSection.getKeys(false)) {
                ConfigurationSection cardConfig = cardsSection.getConfigurationSection(cardId);
                if (cardConfig != null) {
                    int value = cardConfig.getInt("value", 10);
                    int chance = cardConfig.getInt("chance", 10);
                    String name = cardConfig.getString("name", cardId);

                    List<Integer> modelDataList =
                            cardConfig.getIntegerList("custom_model_data_list");
                    int[] modelDatas =
                            modelDataList.isEmpty()
                                    ? new int[] {cardConfig.getInt("custom_model_data", 515)}
                                    : modelDataList.stream().mapToInt(Integer::intValue).toArray();

                    cards.put(cardId, new Card(cardId, value, chance, name, modelDatas));
                }
            }
        }

        // Load card back
        ConfigurationSection cardBack = config.getConfigurationSection("card_back");
        if (cardBack != null) {
            cardBackItem =
                    createItem(
                            Material.getMaterial(cardBack.getString("material", "STICK")),
                            cardBack.getInt("custom_model_data", 541),
                            cardBack.getString("name", "§fCard"));
        }

        if (cards.isEmpty()) {
            loadDefaultCards();
        }
    }

    private void loadDefaultCards() {
        cards.put("A", new Card("A", 11, 12, "§cAce", new int[] {515, 528}));
        cards.put("2", new Card("2", 2, 12, "§cTwo", new int[] {516, 529}));
        cards.put("3", new Card("3", 3, 12, "§cThree", new int[] {517, 530}));
        cards.put("4", new Card("4", 4, 12, "§cFour", new int[] {518, 531}));
        cards.put("5", new Card("5", 5, 12, "§cFive", new int[] {519, 532}));
        cards.put("6", new Card("6", 6, 12, "§cSix", new int[] {520, 533}));
        cards.put("7", new Card("7", 7, 12, "§cSeven", new int[] {521, 534}));
        cards.put("8", new Card("8", 8, 12, "§cEight", new int[] {522, 535}));
        cards.put("9", new Card("9", 9, 12, "§cNine", new int[] {523, 536}));
        cards.put("10", new Card("10", 10, 12, "§cTen", new int[] {524, 537}));
        cards.put("J", new Card("J", 10, 10, "§cJack", new int[] {525, 538}));
        cards.put("Q", new Card("Q", 10, 10, "§cQueen", new int[] {527, 540}));
        cards.put("K", new Card("K", 10, 10, "§cKing", new int[] {526, 539}));

        cardBackItem = createItem(Material.STICK, 541, "§fCard");
    }

    private ItemStack createItem(Material material, int customModelData, String name) {
        ItemStack item = new ItemStack(material != null ? material : Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            meta.setDisplayName(name.replace("&", "§"));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    protected DuoGame createGame(Machine machine, Player player1, int bet) {
        return new BlackjackGameInstance(this, machine, player1, bet);
    }

    /** Draw a random card. */
    public Card drawCard(Random random) {
        int totalWeight = cards.values().stream().mapToInt(c -> c.chance).sum();
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;

        for (Card card : cards.values()) {
            cumulative += card.chance;
            if (roll < cumulative) {
                return card;
            }
        }

        return cards.values().iterator().next();
    }

    public ItemStack getCardBackItem() {
        return cardBackItem != null ? cardBackItem.clone() : new ItemStack(Material.STICK);
    }

    public Map<String, Card> getCards() {
        return cards;
    }

    /** Card data class. */
    public static class Card {
        public final String id;
        public final int value;
        public final int chance;
        public final String name;
        public final int[] customModelDatas;

        public Card(String id, int value, int chance, String name, int[] customModelDatas) {
            this.id = id;
            this.value = value;
            this.chance = chance;
            this.name = name;
            this.customModelDatas = customModelDatas;
        }

        public ItemStack createItem(int variant) {
            ItemStack item = new ItemStack(Material.STICK);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                int modelData =
                        variant < customModelDatas.length
                                ? customModelDatas[variant]
                                : customModelDatas[0];
                meta.setCustomModelData(modelData);
                meta.setDisplayName(name);
                item.setItemMeta(meta);
            }
            return item;
        }
    }
}
