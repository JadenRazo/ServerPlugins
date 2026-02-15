package net.serverplugins.admin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.serverplugins.admin.vanish.VanishMode;
import net.serverplugins.api.messages.PluginMessenger;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public class AdminConfig {

    private final ServerAdmin plugin;
    private PluginMessenger messenger;

    // Vanish settings
    private VanishMode defaultVanishMode;
    private boolean allowInteraction;
    private boolean allowAttack;
    private boolean persistOnRelog;
    private boolean enableFlight;
    private boolean vanishBlockPressurePlates;
    private boolean vanishBlockTripwire;
    private boolean vanishBlockSculkSensors;
    private boolean vanishBlockTurtleEggs;
    private boolean vanishBlockDripleaf;
    private boolean vanishSilentChest;

    // Spectate settings
    private boolean autoVanish;
    private boolean exitOnTargetQuit;
    private boolean restoreLocation;

    // Xray settings
    private boolean xrayEnabled;
    private int alertThreshold;
    private int timeWindow;
    private Set<Material> trackedBlocks;
    private Set<Material> stoneBlocks;

    // Spawner settings
    private boolean spawnerEnabled;
    private int spawnerAlertThreshold;
    private int spawnerTimeWindow;
    private boolean spawnerAlertOnFirst;

    // Freeze settings
    private boolean freezeEnabled;
    private int freezeTimeout;
    private List<String> freezeAllowedCommands;
    private boolean freezeBlindness;
    private boolean freezeSlowness;

    // Staff chat settings
    private boolean staffChatEnabled;
    private String staffChatFormat;

    // Alt detection settings
    private boolean altsEnabled;

    // Xray vision settings (admin ability to see ores)
    private boolean xrayVisionEnabled;
    private int xrayVisionRadius;
    private int xrayVisionUpdateInterval;
    private Set<Material> xrayVisionOres;

    // Freecam settings
    private boolean freecamShowArmor;
    private boolean freecamShowHeldItem;
    private boolean freecamShowNameTag;

    // Messages
    private String prefix;
    private String vanishEnabledMsg;
    private String vanishDisabledMsg;
    private String spectateStartMsg;
    private String spectateEndMsg;
    private String xrayAlertMsg;
    private String spawnerAlertMsg;
    private String freezeMsg;
    private String unfreezeMsg;

    public AdminConfig(ServerAdmin plugin) {
        this.plugin = plugin;
        reload();
    }

    public final void reload() {
        FileConfiguration config = plugin.getConfig();

        // Initialize messenger with admin-themed prefix
        this.messenger = new PluginMessenger(config, "messages", "<red>[Admin]</red> ");

        // Vanish
        defaultVanishMode = VanishMode.fromString(config.getString("vanish.default-mode", "STAFF"));
        allowInteraction = config.getBoolean("vanish.allow-interaction", true);
        allowAttack = config.getBoolean("vanish.allow-attack", false);
        persistOnRelog = config.getBoolean("vanish.persist-on-relog", true);
        enableFlight = config.getBoolean("vanish.enable-flight", true);
        vanishBlockPressurePlates = config.getBoolean("vanish.block-pressure-plates", true);
        vanishBlockTripwire = config.getBoolean("vanish.block-tripwire", true);
        vanishBlockSculkSensors = config.getBoolean("vanish.block-sculk-sensors", true);
        vanishBlockTurtleEggs = config.getBoolean("vanish.block-turtle-eggs", true);
        vanishBlockDripleaf = config.getBoolean("vanish.block-dripleaf", true);
        vanishSilentChest = config.getBoolean("vanish.silent-chest", true);

        // Spectate
        autoVanish = config.getBoolean("spectate.auto-vanish", true);
        exitOnTargetQuit = config.getBoolean("spectate.exit-on-target-quit", true);
        restoreLocation = config.getBoolean("spectate.restore-location", true);

        // Xray
        xrayEnabled = config.getBoolean("xray.enabled", true);
        alertThreshold = config.getInt("xray.alert-threshold", 50);
        timeWindow = config.getInt("xray.time-window", 300);

        trackedBlocks = new HashSet<>();
        for (String block : config.getStringList("xray.tracked-blocks")) {
            try {
                trackedBlocks.add(Material.valueOf(block.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        stoneBlocks = new HashSet<>();
        for (String block : config.getStringList("xray.stone-blocks")) {
            try {
                stoneBlocks.add(Material.valueOf(block.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Spawner
        spawnerEnabled = config.getBoolean("spawner.enabled", true);
        spawnerAlertThreshold = config.getInt("spawner.alert-threshold", 2);
        spawnerTimeWindow = config.getInt("spawner.time-window", 1800);
        spawnerAlertOnFirst = config.getBoolean("spawner.alert-on-first", false);

        // Freeze
        freezeEnabled = config.getBoolean("freeze.enabled", true);
        freezeTimeout = config.getInt("freeze.timeout-minutes", 5);
        freezeAllowedCommands = config.getStringList("freeze.allowed-commands");
        if (freezeAllowedCommands == null) {
            freezeAllowedCommands = new ArrayList<>();
        }
        freezeBlindness = config.getBoolean("freeze.effects.blindness", true);
        freezeSlowness = config.getBoolean("freeze.effects.slowness", true);

        // Staff chat
        staffChatEnabled = config.getBoolean("staffchat.enabled", true);
        staffChatFormat =
                config.getString("staffchat.format", "&8[&6SC&8] &f%player%&7: &f%message%");

        // Alts
        altsEnabled = config.getBoolean("alts.enabled", true);

        // Xray vision (admin ability to see ores)
        xrayVisionEnabled = config.getBoolean("xrayvision.enabled", true);
        xrayVisionRadius = config.getInt("xrayvision.radius", 32);
        xrayVisionUpdateInterval = config.getInt("xrayvision.update-interval", 20);

        xrayVisionOres = new HashSet<>();
        for (String ore : config.getStringList("xrayvision.ores")) {
            try {
                xrayVisionOres.add(Material.valueOf(ore.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Freecam
        freecamShowArmor = config.getBoolean("freecam.show-armor", true);
        freecamShowHeldItem = config.getBoolean("freecam.show-held-item", true);
        freecamShowNameTag = config.getBoolean("freecam.show-name-tag", true);

        // Messages
        prefix = config.getString("messages.prefix", "&8[&6Admin&8] ");
        vanishEnabledMsg =
                config.getString(
                        "messages.vanish-enabled", "&aYou are now vanished &7(%mode% mode)");
        vanishDisabledMsg =
                config.getString("messages.vanish-disabled", "&cYou are no longer vanished");
        spectateStartMsg = config.getString("messages.spectate-start", "&aSpectating &f%player%");
        spectateEndMsg = config.getString("messages.spectate-end", "&cStopped spectating");
        xrayAlertMsg =
                config.getString(
                        "messages.xray-alert",
                        "&c[XRay] &f%player% &7mined &f%count% %block% &7in &f%time% &7(suspicion: &c%level%&7)");
        spawnerAlertMsg =
                config.getString(
                        "messages.spawner-alert",
                        "&d[Spawner] &f%player% &7found a &f%type% spawner &7at &f%location% &7(%count% in %time%)");
        freezeMsg =
                config.getString(
                        "messages.freeze-msg",
                        "&c&lYOU HAVE BEEN FROZEN\\n&7You were frozen by &f%staff%\\n&7Reason: &f%reason%");
        unfreezeMsg = config.getString("messages.unfreeze-msg", "&a&lYou have been unfrozen");
    }

    // Vanish getters
    public VanishMode getDefaultVanishMode() {
        return defaultVanishMode;
    }

    public boolean allowInteraction() {
        return allowInteraction;
    }

    public boolean allowAttack() {
        return allowAttack;
    }

    public boolean persistOnRelog() {
        return persistOnRelog;
    }

    public boolean enableFlight() {
        return enableFlight;
    }

    public boolean vanishBlockPressurePlates() {
        return vanishBlockPressurePlates;
    }

    public boolean vanishBlockTripwire() {
        return vanishBlockTripwire;
    }

    public boolean vanishBlockSculkSensors() {
        return vanishBlockSculkSensors;
    }

    public boolean vanishBlockTurtleEggs() {
        return vanishBlockTurtleEggs;
    }

    public boolean vanishBlockDripleaf() {
        return vanishBlockDripleaf;
    }

    public boolean vanishSilentChest() {
        return vanishSilentChest;
    }

    // Spectate getters
    public boolean autoVanish() {
        return autoVanish;
    }

    public boolean exitOnTargetQuit() {
        return exitOnTargetQuit;
    }

    public boolean restoreLocation() {
        return restoreLocation;
    }

    // Xray getters
    public boolean isXrayEnabled() {
        return xrayEnabled;
    }

    public int getAlertThreshold() {
        return alertThreshold;
    }

    public int getTimeWindow() {
        return timeWindow;
    }

    public Set<Material> getTrackedBlocks() {
        return trackedBlocks;
    }

    public Set<Material> getStoneBlocks() {
        return stoneBlocks;
    }

    // Spawner getters
    public boolean isSpawnerEnabled() {
        return spawnerEnabled;
    }

    public int getSpawnerAlertThreshold() {
        return spawnerAlertThreshold;
    }

    public int getSpawnerTimeWindow() {
        return spawnerTimeWindow;
    }

    public boolean spawnerAlertOnFirst() {
        return spawnerAlertOnFirst;
    }

    // Freeze getters
    public boolean isFreezeEnabled() {
        return freezeEnabled;
    }

    public int getFreezeTimeout() {
        return freezeTimeout;
    }

    public List<String> getFreezeAllowedCommands() {
        return freezeAllowedCommands;
    }

    public boolean freezeBlindness() {
        return freezeBlindness;
    }

    public boolean freezeSlowness() {
        return freezeSlowness;
    }

    // Staff chat getters
    public boolean isStaffChatEnabled() {
        return staffChatEnabled;
    }

    public String getStaffChatFormat() {
        return staffChatFormat;
    }

    // Alts getters
    public boolean isAltsEnabled() {
        return altsEnabled;
    }

    // Xray vision getters
    public boolean isXrayVisionEnabled() {
        return xrayVisionEnabled;
    }

    public int getXrayVisionRadius() {
        return xrayVisionRadius;
    }

    public int getXrayVisionUpdateInterval() {
        return xrayVisionUpdateInterval;
    }

    public Set<Material> getXrayVisionOres() {
        return xrayVisionOres;
    }

    // Freecam getters
    public boolean freecamShowArmor() {
        return freecamShowArmor;
    }

    public boolean freecamShowHeldItem() {
        return freecamShowHeldItem;
    }

    public boolean freecamShowNameTag() {
        return freecamShowNameTag;
    }

    // Message getters
    public String getPrefix() {
        return prefix;
    }

    public String getVanishEnabledMsg() {
        return vanishEnabledMsg;
    }

    public String getVanishDisabledMsg() {
        return vanishDisabledMsg;
    }

    public String getSpectateStartMsg() {
        return spectateStartMsg;
    }

    public String getSpectateEndMsg() {
        return spectateEndMsg;
    }

    public String getXrayAlertMsg() {
        return xrayAlertMsg;
    }

    public String getSpawnerAlertMsg() {
        return spawnerAlertMsg;
    }

    public String getFreezeMsg() {
        return freezeMsg;
    }

    public String getUnfreezeMsg() {
        return unfreezeMsg;
    }

    // Messenger getter
    public PluginMessenger getMessenger() {
        return messenger;
    }
}
