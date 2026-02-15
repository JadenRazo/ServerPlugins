package net.serverplugins.claim.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimBenefits;
import net.serverplugins.claim.models.ClaimLevel;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ClaimLevelGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;

    public ClaimLevelGui(ServerClaim plugin, Player player, Claim claim) {
        super(plugin, player, "<gold>Claim Level - " + claim.getName(), 54);
        this.plugin = plugin;
        this.claim = claim;
    }

    @Override
    protected void initializeItems() {
        ClaimLevel level = plugin.getLevelManager().getLevel(claim.getId());
        ClaimBenefits benefits = plugin.getLevelManager().getBenefits(claim.getId());

        // Fill background
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            setItem(i, new GuiItem(filler));
        }

        // Level display (slot 4)
        ItemStack levelItem =
                new ItemBuilder(getMaterialForLevel(level.getLevel()))
                        .name("<gold>Level " + level.getLevel())
                        .lore(
                                "",
                                "<gray>Current XP: <yellow>" + level.getCurrentXp(),
                                "<gray>XP to next level: <yellow>"
                                        + level.getXpNeededForNextLevel(),
                                "<gray>Total XP earned: <aqua>" + level.getTotalXpEarned(),
                                "",
                                createProgressBar(level.getProgressPercentage()))
                        .glow(level.getLevel() >= 5)
                        .build();
        setItem(4, new GuiItem(levelItem));

        // Progress bar (slots 10-16)
        createXpProgressBar(level.getProgressPercentage());

        // Current Benefits (slot 22)
        ItemStack benefitsItem =
                new ItemBuilder(Material.NETHER_STAR)
                        .name("<green>Current Benefits")
                        .lore(
                                "",
                                "<gray>Member Slots: <white>" + benefits.getMaxMemberSlots(),
                                "<gray>Warp Slots: <white>" + benefits.getMaxWarpSlots(),
                                "<gray>Upkeep Discount: <green>"
                                        + String.format("%.0f", benefits.getUpkeepDiscountPercent())
                                        + "%",
                                "<gray>Welcome Message Length: <white>"
                                        + benefits.getWelcomeMessageLength(),
                                "<gray>Particle Tier: <white>" + benefits.getParticleTier(),
                                "<gray>Bonus Chunk Slots: <white>" + benefits.getBonusChunkSlots())
                        .build();
        setItem(22, new GuiItem(benefitsItem));

        // XP Sources (slot 20)
        ItemStack xpSourcesItem =
                new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                        .name("<aqua>How to Earn XP")
                        .lore(
                                "",
                                "<gray>Playtime in claim: <yellow>1 XP/min",
                                "<gray>Blocks placed: <yellow>2 XP/100 blocks",
                                "<gray>Blocks broken: <yellow>2 XP/100 blocks",
                                "<gray>Adding members: <yellow>50 XP",
                                "<gray>Paying upkeep: <yellow>100 XP",
                                "<gray>Claiming chunks: <yellow>25 XP")
                        .build();
        setItem(20, new GuiItem(xpSourcesItem));

        // Next Level Preview (slot 24)
        if (level.getLevel() < 10) {
            ClaimBenefits nextBenefits =
                    ClaimBenefits.forLevel(claim.getId(), level.getLevel() + 1);
            ItemStack nextItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Level " + (level.getLevel() + 1) + " Preview")
                            .lore(
                                    "",
                                    "<gray>Member Slots: <white>"
                                            + nextBenefits.getMaxMemberSlots()
                                            + getChangeIndicator(
                                                    benefits.getMaxMemberSlots(),
                                                    nextBenefits.getMaxMemberSlots()),
                                    "<gray>Warp Slots: <white>"
                                            + nextBenefits.getMaxWarpSlots()
                                            + getChangeIndicator(
                                                    benefits.getMaxWarpSlots(),
                                                    nextBenefits.getMaxWarpSlots()),
                                    "<gray>Upkeep Discount: <green>"
                                            + String.format(
                                                    "%.0f", nextBenefits.getUpkeepDiscountPercent())
                                            + "%"
                                            + getChangeIndicator(
                                                    (int) benefits.getUpkeepDiscountPercent(),
                                                    (int) nextBenefits.getUpkeepDiscountPercent()),
                                    "<gray>Welcome Length: <white>"
                                            + nextBenefits.getWelcomeMessageLength()
                                            + getChangeIndicator(
                                                    benefits.getWelcomeMessageLength(),
                                                    nextBenefits.getWelcomeMessageLength()),
                                    "",
                                    "<gray>XP Required: <yellow>" + level.getXpNeededForNextLevel())
                            .build();
            setItem(24, new GuiItem(nextItem));
        } else {
            ItemStack maxItem =
                    new ItemBuilder(Material.DRAGON_EGG)
                            .name("<light_purple>Maximum Level!")
                            .lore(
                                    "",
                                    "<gray>Your claim has reached",
                                    "<gray>the maximum level!",
                                    "",
                                    "<gold>★ <yellow>Congratulations! <gold>★")
                            .glow(true)
                            .build();
            setItem(24, new GuiItem(maxItem));
        }

        // Level milestones (row 4, slots 28-34)
        for (int lvl = 1; lvl <= 7; lvl++) {
            createLevelMilestone(27 + lvl, lvl, level.getLevel());
        }
        // Continue milestones (row 5, slots 37-39)
        for (int lvl = 8; lvl <= 10; lvl++) {
            createLevelMilestone(29 + lvl, lvl, level.getLevel());
        }

        // Back button (slot 49)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<gray>Back")
                        .lore("<gray>Return to claim settings")
                        .build();
        setItem(
                49,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimSettingsGui(plugin, viewer, claim).open();
                        }));

        // Close button (slot 53)
        ItemStack closeItem = new ItemBuilder(Material.BARRIER).name("<red>Close").build();
        setItem(53, new GuiItem(closeItem, e -> viewer.closeInventory()));
    }

    private void createXpProgressBar(double percent) {
        int filledSlots = (int) Math.round(percent / 100.0 * 7);
        for (int i = 0; i < 7; i++) {
            Material mat =
                    i < filledSlots
                            ? Material.LIME_STAINED_GLASS_PANE
                            : Material.RED_STAINED_GLASS_PANE;
            String name = i < filledSlots ? "<green>■" : "<red>□";
            ItemStack item = new ItemBuilder(mat).name(name).build();
            setItem(10 + i, new GuiItem(item));
        }
    }

    private void createLevelMilestone(int slot, int lvl, int currentLevel) {
        Material mat;
        String prefix;
        boolean glow = false;

        if (lvl < currentLevel) {
            mat = Material.LIME_CONCRETE;
            prefix = "<green>✔ ";
            glow = true;
        } else if (lvl == currentLevel) {
            mat = Material.YELLOW_CONCRETE;
            prefix = "<yellow>► ";
            glow = true;
        } else {
            mat = Material.GRAY_CONCRETE;
            prefix = "<gray>";
        }

        ClaimBenefits b = ClaimBenefits.forLevel(claim.getId(), lvl);
        ItemBuilder builder =
                new ItemBuilder(mat)
                        .name(prefix + "Level " + lvl)
                        .lore(
                                "",
                                "<gray>Members: <white>" + b.getMaxMemberSlots(),
                                "<gray>Warps: <white>" + b.getMaxWarpSlots(),
                                "<gray>Discount: <white>"
                                        + String.format("%.0f", b.getUpkeepDiscountPercent())
                                        + "%");

        if (glow) builder.glow(true);

        setItem(slot, new GuiItem(builder.build()));
    }

    private Material getMaterialForLevel(int level) {
        return switch (level) {
            case 1 -> Material.WOODEN_SWORD;
            case 2 -> Material.STONE_SWORD;
            case 3 -> Material.IRON_SWORD;
            case 4 -> Material.GOLDEN_SWORD;
            case 5 -> Material.DIAMOND_SWORD;
            case 6 -> Material.NETHERITE_SWORD;
            case 7 -> Material.TRIDENT;
            case 8 -> Material.END_CRYSTAL;
            case 9 -> Material.NETHER_STAR;
            case 10 -> Material.DRAGON_EGG;
            default -> Material.STICK;
        };
    }

    private String createProgressBar(double percent) {
        int filled = (int) Math.round(percent / 10);
        StringBuilder bar = new StringBuilder("<green>");
        for (int i = 0; i < 10; i++) {
            if (i < filled) {
                bar.append("█");
            } else {
                bar.append("<gray>░");
            }
        }
        bar.append(" <white>").append(String.format("%.1f", percent)).append("%");
        return bar.toString();
    }

    private String getChangeIndicator(int current, int next) {
        if (next > current) {
            return " <green>(+" + (next - current) + ")";
        }
        return "";
    }
}
