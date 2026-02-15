package net.serverplugins.claim.gui;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.ClaimTemplate;
import net.serverplugins.claim.repository.ClaimTemplateRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** GUI for browsing and managing claim templates. */
public class TemplateListGui extends Gui {

    private final ServerClaim plugin;
    private final ClaimTemplateRepository templateRepository;
    private final int page;
    private static final int ITEMS_PER_PAGE = 28;

    public TemplateListGui(ServerClaim plugin, Player player) {
        this(plugin, player, 0);
    }

    public TemplateListGui(ServerClaim plugin, Player player, int page) {
        super(plugin, player, "Your Claim Templates", 54);
        this.plugin = plugin;
        this.templateRepository = plugin.getClaimTemplateRepository();
        this.page = page;
    }

    @Override
    protected void initializeItems() {
        // Fill border
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) setItem(i, new GuiItem(filler));
        for (int i = 45; i < 54; i++) setItem(i, new GuiItem(filler));
        for (int i = 9; i < 45; i += 9) {
            setItem(i, new GuiItem(filler));
            setItem(i + 8, new GuiItem(filler));
        }

        // Load templates
        List<ClaimTemplate> templates = templateRepository.getPlayerTemplates(viewer.getUniqueId());

        // Empty message
        if (templates.isEmpty()) {
            setItem(
                    22,
                    new GuiItem(
                            new ItemBuilder(Material.BARRIER)
                                    .name("<red>No Templates")
                                    .lore(
                                            "",
                                            "<gray>You don't have any saved templates.",
                                            "<gray>Use <gold>/claim template save <name>",
                                            "<gray>to create one!",
                                            "")
                                    .build()));
            return;
        }

        // Pagination
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, templates.size());
        List<ClaimTemplate> pageTemplates = templates.subList(startIndex, endIndex);

        // Display templates
        int slot = 10;
        for (ClaimTemplate template : pageTemplates) {
            // Skip border slots
            if (slot == 17 || slot == 26 || slot == 35) slot++;
            if (slot == 18 || slot == 27 || slot == 36) slot++;

            setItem(
                    slot++,
                    GuiItem.withContext(
                            createTemplateItem(template),
                            context -> {
                                Player clicker = context.getPlayer();
                                if (context.getClickType().isShiftClick()) {
                                    // Delete template
                                    TextUtil.send(
                                            clicker,
                                            "<yellow>Use <gold>/claim template delete "
                                                    + template.getTemplateName()
                                                    + "</gold> to delete this template.");
                                    clicker.closeInventory();
                                } else {
                                    // Apply template
                                    clicker.closeInventory();
                                    clicker.performCommand(
                                            "claim template apply " + template.getTemplateName());
                                }
                            }));
        }

        // Navigation
        if (page > 0) {
            setItem(
                    48,
                    new GuiItem(
                            new ItemBuilder(Material.ARROW).name("<yellow>Previous Page").build(),
                            context -> new TemplateListGui(plugin, viewer, page - 1).open()));
        }

        if (endIndex < templates.size()) {
            setItem(
                    50,
                    new GuiItem(
                            new ItemBuilder(Material.ARROW).name("<yellow>Next Page").build(),
                            context -> new TemplateListGui(plugin, viewer, page + 1).open()));
        }

        // Close button
        setItem(
                49,
                new GuiItem(
                        new ItemBuilder(Material.BARRIER).name("<red>Close").build(),
                        context -> context.getPlayer().closeInventory()));
    }

    private ItemStack createTemplateItem(ClaimTemplate template) {
        ItemBuilder builder =
                new ItemBuilder(Material.WRITABLE_BOOK)
                        .name(
                                "<gradient:#00c6ff:#0072ff>"
                                        + template.getTemplateName()
                                        + "</gradient>");

        // Build lore as varargs list
        List<String> loreLines = new ArrayList<>();
        loreLines.add("");
        if (template.getDescription() != null && !template.getDescription().isEmpty()) {
            loreLines.add("<gray>" + template.getDescription());
            loreLines.add("");
        }
        loreLines.add("<gold>Settings:");
        loreLines.add(
                "<gray>  PVP: " + (template.isPvpEnabled() ? "<green>Enabled" : "<red>Disabled"));
        loreLines.add(
                "<gray>  Fire Spread: "
                        + (template.isFireSpreadEnabled() ? "<green>Enabled" : "<red>Disabled"));
        loreLines.add(
                "<gray>  Mob Spawning: "
                        + (template.isMobSpawningEnabled() ? "<green>Enabled" : "<red>Disabled"));
        loreLines.add(
                "<gray>  Explosions: "
                        + (template.isExplosionDamageEnabled()
                                ? "<green>Enabled"
                                : "<red>Disabled"));
        loreLines.add("");
        loreLines.add("<gold>Times Used: <white>" + template.getTimesUsed());
        loreLines.add("");
        loreLines.add("<yellow>Click to apply to current claim");
        loreLines.add("<red>Shift-Click to delete");
        loreLines.add("");

        return builder.lore(loreLines.toArray(new String[0])).build();
    }
}
