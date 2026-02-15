package net.serverplugins.claim.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.gui.TemplateListGui;
import net.serverplugins.claim.gui.TemplateSaveGui;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimTemplate;
import net.serverplugins.claim.repository.ClaimTemplateRepository;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Command for managing claim templates. Usage: /claim template <save|list|apply|delete|rename|info>
 * [args...]
 */
public class ClaimTemplateCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;
    private final ClaimTemplateRepository templateRepository;

    public ClaimTemplateCommand(ServerClaim plugin) {
        this.plugin = plugin;
        this.templateRepository = plugin.getTemplateRepository();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (args.length < 1) {
            sendUsage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "save" -> handleSave(player, args);
            case "list" -> handleList(player);
            case "apply" -> handleApply(player, args);
            case "delete" -> handleDelete(player, args);
            case "rename" -> handleRename(player, args);
            case "info" -> handleInfo(player, args);
            default -> sendUsage(player);
        }

        return true;
    }

    private void handleSave(Player player, String[] args) {
        // Open GUI for template saving
        Claim currentClaim = plugin.getClaimManager().getClaimAt(player.getLocation().getChunk());
        if (currentClaim == null) {
            TextUtil.send(player, "<red>You must be standing in a claim to save a template.");
            return;
        }

        if (!currentClaim.isOwner(player.getUniqueId())
                && !player.hasPermission("serverclaim.admin")) {
            TextUtil.send(player, "<red>You can only save templates from your own claims.");
            return;
        }

        // Open save GUI
        new TemplateSaveGui(plugin, player, currentClaim).open();
    }

    private void handleList(Player player) {
        // Open template list GUI
        new TemplateListGui(plugin, player).open();
    }

    private void handleApply(Player player, String[] args) {
        if (args.length < 2) {
            TextUtil.send(player, "<red>Usage: /claim template apply <template_name>");
            return;
        }

        Claim currentClaim = plugin.getClaimManager().getClaimAt(player.getLocation().getChunk());
        if (currentClaim == null) {
            TextUtil.send(player, "<red>You must be standing in a claim to apply a template.");
            return;
        }

        if (!currentClaim.isOwner(player.getUniqueId())
                && !player.hasPermission("serverclaim.admin")) {
            TextUtil.send(player, "<red>You can only apply templates to your own claims.");
            return;
        }

        String templateName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        ClaimTemplate template = templateRepository.getTemplate(player.getUniqueId(), templateName);

        if (template == null) {
            TextUtil.send(player, "<red>Template '<yellow>" + templateName + "<red>' not found.");
            return;
        }

        // Apply template
        try {
            currentClaim.applyTemplate(template);
            plugin.getRepository().updateClaim(currentClaim);
            templateRepository.incrementUsageCount(template.getId());

            TextUtil.send(
                    player,
                    "<green>Template '<yellow>" + templateName + "<green>' applied successfully!");
        } catch (Exception e) {
            TextUtil.send(player, "<red>Failed to apply template: " + e.getMessage());
            plugin.getLogger().severe("Error applying template: " + e.getMessage());
        }
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            TextUtil.send(player, "<red>Usage: /claim template delete <template_name>");
            return;
        }

        String templateName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        boolean deleted = templateRepository.deleteTemplate(player.getUniqueId(), templateName);

        if (deleted) {
            TextUtil.send(
                    player,
                    "<green>Template '<yellow>" + templateName + "<green>' deleted successfully.");
        } else {
            TextUtil.send(player, "<red>Template '<yellow>" + templateName + "<red>' not found.");
        }
    }

    private void handleRename(Player player, String[] args) {
        if (args.length < 3) {
            TextUtil.send(player, "<red>Usage: /claim template rename <old_name> <new_name>");
            return;
        }

        // Find the split point - everything before the last word is old name, last word is new name
        int splitIndex = args.length - 1;
        String oldName = String.join(" ", Arrays.copyOfRange(args, 1, splitIndex));
        String newName = args[splitIndex];

        boolean renamed = templateRepository.renameTemplate(player.getUniqueId(), oldName, newName);

        if (renamed) {
            TextUtil.send(
                    player,
                    "<green>Template renamed from '<yellow>"
                            + oldName
                            + "<green>' to '<yellow>"
                            + newName
                            + "<green>'.");
        } else {
            TextUtil.send(
                    player,
                    "<red>Failed to rename template. Make sure it exists and the new name isn't already in use.");
        }
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            TextUtil.send(player, "<red>Usage: /claim template info <template_name>");
            return;
        }

        String templateName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        ClaimTemplate template = templateRepository.getTemplate(player.getUniqueId(), templateName);

        if (template == null) {
            TextUtil.send(player, "<red>Template '<yellow>" + templateName + "<red>' not found.");
            return;
        }

        TextUtil.send(player, "<gold>========== Template Info ==========");
        TextUtil.send(player, "<yellow>Name: <white>" + template.getTemplateName());
        if (template.getDescription() != null && !template.getDescription().isEmpty()) {
            TextUtil.send(player, "<yellow>Description: <white>" + template.getDescription());
        }
        TextUtil.send(player, "<yellow>Times Used: <white>" + template.getTimesUsed());
        TextUtil.send(player, "");
        TextUtil.send(player, "<yellow>Settings:");
        TextUtil.send(
                player,
                "  <gray>PvP: " + (template.isPvpEnabled() ? "<green>Enabled" : "<red>Disabled"));
        TextUtil.send(
                player,
                "  <gray>Fire Spread: "
                        + (template.isFireSpread() ? "<green>Enabled" : "<red>Disabled"));
        TextUtil.send(
                player,
                "  <gray>Mob Spawning: "
                        + (template.isMobSpawning() ? "<green>Enabled" : "<red>Disabled"));
        TextUtil.send(
                player,
                "  <gray>Explosions: "
                        + (template.isExplosionDamage() ? "<green>Enabled" : "<red>Disabled"));
        TextUtil.send(
                player,
                "  <gray>Leaf Decay: "
                        + (template.isLeafDecay() ? "<green>Enabled" : "<red>Disabled"));
    }

    private void sendUsage(Player player) {
        TextUtil.send(player, "<gold>========== Claim Templates ==========");
        TextUtil.send(
                player, "<yellow>/claim template save <gray>- Save current claim as template");
        TextUtil.send(player, "<yellow>/claim template list <gray>- View your templates");
        TextUtil.send(
                player,
                "<yellow>/claim template apply <name> <gray>- Apply template to current claim");
        TextUtil.send(player, "<yellow>/claim template delete <name> <gray>- Delete a template");
        TextUtil.send(
                player, "<yellow>/claim template rename <old> <new> <gray>- Rename a template");
        TextUtil.send(player, "<yellow>/claim template info <name> <gray>- View template details");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("save", "list", "apply", "delete", "rename", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2
                && (args[0].equalsIgnoreCase("apply")
                        || args[0].equalsIgnoreCase("delete")
                        || args[0].equalsIgnoreCase("rename")
                        || args[0].equalsIgnoreCase("info"))) {
            // Suggest player's template names
            return templateRepository.getPlayerTemplates(player.getUniqueId()).stream()
                    .map(ClaimTemplate::getTemplateName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
