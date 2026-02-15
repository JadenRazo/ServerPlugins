package net.serverplugins.adminvelocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.List;
import net.serverplugins.adminvelocity.listeners.ChatListener;
import net.serverplugins.adminvelocity.messaging.VelocityTextUtil;
import net.serverplugins.adminvelocity.staffchat.StaffChatRouter;
import org.slf4j.Logger;

/** /sc [message] - Staff chat command. */
public class StaffChatCommand implements SimpleCommand {

    private static final String PERMISSION = "serveradmin.staffchat";

    private final Logger logger;
    private final StaffChatRouter staffChatRouter;
    private final ChatListener chatListener;

    public StaffChatCommand(
            Logger logger, StaffChatRouter staffChatRouter, ChatListener chatListener) {
        this.logger = logger;
        this.staffChatRouter = staffChatRouter;
        this.chatListener = chatListener;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!(source instanceof Player player)) {
            VelocityTextUtil.sendError(source, "Only players can use staff chat.");
            return;
        }

        if (!source.hasPermission(PERMISSION)) {
            VelocityTextUtil.sendError(source, "You don't have permission to use this command.");
            return;
        }

        if (args.length == 0) {
            // Toggle staff chat mode
            boolean newState = chatListener.toggleStaffChat(player.getUniqueId());
            if (newState) {
                VelocityTextUtil.sendSuccess(
                        player,
                        "Staff chat mode enabled. All your messages will go to staff chat.");
            } else {
                VelocityTextUtil.sendSuccess(
                        player,
                        "Staff chat mode disabled. Your messages will now be sent normally.");
            }
            return;
        }

        // Send message to staff chat
        String message = String.join(" ", args);
        String serverName =
                player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("Velocity");

        staffChatRouter.sendMessage(player, serverName, message);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }
}
