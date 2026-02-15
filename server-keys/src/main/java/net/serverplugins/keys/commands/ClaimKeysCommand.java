package net.serverplugins.keys.commands;

import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.keys.managers.KeyManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class ClaimKeysCommand implements CommandExecutor, TabCompleter {

    private final KeyManager keyManager;

    public ClaimKeysCommand(KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        keyManager.claimKeys(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        return Collections.emptyList();
    }
}
