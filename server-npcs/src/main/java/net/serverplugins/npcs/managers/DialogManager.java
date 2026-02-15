package net.serverplugins.npcs.managers;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.utils.GeyserUtils;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.npcs.ServerNpcs;
import net.serverplugins.npcs.dialog.Dialog;
import net.serverplugins.npcs.dialog.DialogChoice;
import net.serverplugins.npcs.dialog.DialogNode;
import net.serverplugins.npcs.utils.NpcIcons;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class DialogManager {

    private final ServerNpcs plugin;
    private final Map<String, Dialog> dialogs;
    private final Map<Player, String> activeDialogs;

    public DialogManager(ServerNpcs plugin) {
        this.plugin = plugin;
        this.dialogs = new HashMap<>();
        this.activeDialogs = new HashMap<>();
        loadDialogs();
    }

    public final void loadDialogs() {
        dialogs.clear();

        File dialogsFolder =
                new File(plugin.getDataFolder(), plugin.getNpcsConfig().getDialogsFolder());
        if (!dialogsFolder.exists()) {
            dialogsFolder.mkdirs();
            plugin.getLogger().info("Created dialogs folder: " + dialogsFolder.getPath());
            return;
        }

        File[] files = dialogsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("No dialog files found in " + dialogsFolder.getPath());
            return;
        }

        int loaded = 0;
        for (File file : files) {
            try {
                Dialog dialog = loadDialogFromFile(file);
                if (dialog != null) {
                    dialogs.put(dialog.getId(), dialog);
                    loaded++;
                }
            } catch (Exception e) {
                plugin.getLogger()
                        .warning(
                                "Failed to load dialog from "
                                        + file.getName()
                                        + ": "
                                        + e.getMessage());
                if (plugin.getNpcsConfig().isDebugEnabled()) {
                    e.printStackTrace();
                }
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " dialog(s)");
    }

    private Dialog loadDialogFromFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String id = file.getName().replace(".yml", "");
        String npcName = config.getString("npc.name", id);
        String displayName = config.getString("npc.display-name", npcName);

        ConfigurationSection dialogSection = config.getConfigurationSection("dialog");
        if (dialogSection == null) {
            plugin.getLogger().warning("No dialog section found in " + file.getName());
            return null;
        }

        // Load the root node (greeting)
        DialogNode rootNode = loadDialogNode(dialogSection, "greeting");
        if (rootNode == null) {
            plugin.getLogger().warning("No greeting node found in " + file.getName());
            return null;
        }

        return new Dialog.Builder()
                .id(id)
                .npcName(npcName)
                .displayName(displayName)
                .rootNode(rootNode)
                .build();
    }

    private DialogNode loadDialogNode(ConfigurationSection section, String nodeId) {
        ConfigurationSection nodeSection = section.getConfigurationSection(nodeId);
        if (nodeSection == null) {
            return null;
        }

        List<String> messages = nodeSection.getStringList("message");

        DialogNode.Builder builder = new DialogNode.Builder().nodeId(nodeId).messages(messages);

        ConfigurationSection choicesSection = nodeSection.getConfigurationSection("choices");
        if (choicesSection != null) {
            for (String key : choicesSection.getKeys(false)) {
                ConfigurationSection choiceSection = choicesSection.getConfigurationSection(key);
                if (choiceSection != null) {
                    DialogChoice choice = loadDialogChoice(choiceSection);
                    if (choice != null) {
                        builder.addChoice(choice);
                    }
                }
            }
        }

        return builder.build();
    }

    private DialogChoice loadDialogChoice(ConfigurationSection section) {
        return new DialogChoice.Builder()
                .text(section.getString("text", ""))
                .command(section.getString("command"))
                .permission(section.getString("permission", ""))
                .delay(section.getInt("delay", 0))
                .close(section.getBoolean("close", false))
                .nextNode(section.getString("next-node"))
                .build();
    }

    public void showDialog(Player player, String dialogId) {
        Dialog dialog = dialogs.get(dialogId);
        if (dialog == null) {
            plugin.getNpcsConfig().getMessenger().send(player, "dialog-not-found");
            return;
        }

        activeDialogs.put(player, dialogId);

        // Check if player is on Bedrock
        if (GeyserUtils.isBedrockPlayer(player)) {
            showDialogForm(player, dialog, dialog.getRootNode());
        } else {
            showDialogGui(player, dialog, dialog.getRootNode());
        }
    }

    private void showDialogGui(Player player, Dialog dialog, DialogNode node) {
        // Get NPC name and portrait icon
        String npcName = dialog.getNpcName();
        String portraitIcon = NpcIcons.getIcon(npcName);
        boolean isBedrock = GeyserUtils.isBedrockPlayer(player);

        // Extract color code from display-name (format: "⻔&d&lNAME" or "⻔<color>NAME")
        String displayName = dialog.getDisplayName();
        String colorCode = ColorScheme.HIGHLIGHT; // Default white

        // Try to extract MiniMessage color
        if (displayName.contains("<") && displayName.contains(">")) {
            int start = displayName.indexOf('<');
            int end = displayName.indexOf('>', start);
            if (end > start) {
                colorCode = "<" + displayName.substring(start + 1, end) + ">";
            }
        }
        // Fallback to legacy color codes (& or §)
        else if ((displayName.contains("&") || displayName.contains("§"))
                && displayName.length() > 3) {
            int colorIndex = displayName.indexOf('&');
            if (colorIndex < 0) colorIndex = displayName.indexOf('§');
            if (colorIndex >= 0 && colorIndex + 1 < displayName.length()) {
                char code = displayName.charAt(colorIndex + 1);
                // Convert legacy codes to MiniMessage
                colorCode = convertLegacyToMiniMessage(code);
            }
        }

        // Send all dialog messages to chat (portraits only work in chat, not title/action bar)
        List<String> messages = node.getMessages();
        for (String message : messages) {
            // Replace the spacing character (⻔) with portrait icon where it appears
            // The YAML has ⻔ as placeholder, we replace it with actual portrait
            String processedMessage = message.replace(NpcIcons.SPACE_CUSTOM, portraitIcon);
            processedMessage = processedMessage.replace("{player}", player.getName());

            // For Bedrock players, strip custom font characters that don't render
            if (isBedrock) {
                processedMessage = stripBedrockUnsupportedChars(processedMessage);
            }

            TextUtil.send(player, processedMessage);
        }

        // Show clickable choices in chat
        if (node.hasChoices()) {
            List<DialogChoice> choices = node.getChoices();
            for (int i = 0; i < choices.size(); i++) {
                DialogChoice choice = choices.get(i);
                if (choice.hasPermission() && !player.hasPermission(choice.getPermission())) {
                    continue;
                }

                String choiceText = choice.getText();
                choiceText = choiceText.replace(NpcIcons.SPACE_CUSTOM, portraitIcon);
                choiceText = choiceText.replace("{player}", player.getName());
                if (isBedrock) {
                    choiceText = stripBedrockUnsupportedChars(choiceText);
                }

                String clickCmd = "/dialog choose " + dialog.getId() + " " + i;
                String line =
                        "<click:run_command:'"
                                + clickCmd
                                + "'><hover:show_text:'<gray>Click to select'>"
                                + choiceText
                                + "</hover></click>";
                TextUtil.send(player, line);
            }
        }
    }

    /**
     * Strips custom font characters that don't render on Bedrock Edition. These include negative
     * space characters (⻔, 퀁) and other custom unicode chars.
     */
    private String stripBedrockUnsupportedChars(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // Remove common negative space characters used in Java resource packs
        // ⻔ (U+2ED4) - custom negative space
        // 퀁 (U+D001) - custom negative space
        // お (U+304A) - bossbar character
        // Characters in Private Use Area (U+E000-U+F8FF)
        return text.replaceAll("[⻔퀁お\\uE000-\\uF8FF]", "")
                .replaceAll("\\s{2,}", " ") // Collapse multiple spaces
                .trim();
    }

    /** Converts legacy color codes to MiniMessage format. */
    private String convertLegacyToMiniMessage(char legacyCode) {
        return switch (legacyCode) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            default -> ColorScheme.HIGHLIGHT;
        };
    }

    private void showDialogForm(Player player, Dialog dialog, DialogNode node) {
        // Bedrock form implementation
        // This would integrate with Floodgate/Cumulus forms
        // For now, fall back to GUI
        showDialogGui(player, dialog, node);
    }

    public void executeChoice(Player player, DialogChoice choice) {
        if (choice.hasCommand()) {
            int delay = choice.getDelay();
            if (delay > 0) {
                Bukkit.getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    player.performCommand(choice.getCommand());
                                },
                                delay);
            } else {
                player.performCommand(choice.getCommand());
            }
        }

        if (choice.shouldClose()) {
            activeDialogs.remove(player);
        }
    }

    public Dialog getDialog(String id) {
        return dialogs.get(id);
    }

    public int getDialogCount() {
        return dialogs.size();
    }

    public void reload() {
        loadDialogs();
    }

    public boolean isInDialog(Player player) {
        return activeDialogs.containsKey(player);
    }

    public void closeDialog(Player player) {
        activeDialogs.remove(player);
    }
}
