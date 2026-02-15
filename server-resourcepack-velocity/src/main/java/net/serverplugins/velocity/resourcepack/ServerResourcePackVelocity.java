package net.serverplugins.velocity.resourcepack;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.nio.file.Path;
import net.serverplugins.velocity.resourcepack.listeners.ResourcePackListener;
import net.serverplugins.velocity.resourcepack.managers.PackVersionManager;
import org.slf4j.Logger;

/**
 * ServerResourcePack Velocity Plugin
 *
 * <p>Automatically delivers version-specific resource packs to players based on their Minecraft
 * protocol version. Ensures compatibility across multiple client versions (1.21.3 through 1.21.11)
 * by maintaining separate packs for each protocol.
 *
 * <p>Features: - Protocol detection and automatic pack selection - SHA-1 hash verification for pack
 * integrity - Configurable required/optional pack enforcement - Delayed pack sending to prevent
 * race conditions - Comprehensive logging and debugging
 *
 * @author ServerPlugins
 * @version 1.1.0
 */
@Plugin(
        id = "server-resourcepack-velocity",
        name = "ServerResourcePack Velocity",
        version = "1.1.0",
        description = "Version-specific resource pack delivery for ServerPlugins",
        authors = {"ServerPlugins"})
public class ServerResourcePackVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private ResourcePackConfig config;
    private PackVersionManager packManager;
    private ResourcePackListener listener;

    /**
     * Constructor injected by Velocity.
     *
     * @param server Velocity proxy server instance
     * @param logger SLF4J logger for this plugin
     * @param dataDirectory Plugin data directory for configuration
     */
    @Inject
    public ServerResourcePackVelocity(
            ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /**
     * Initializes the plugin when the proxy starts. Loads configuration, initializes managers, and
     * registers event listeners.
     */
    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("Initializing ServerResourcePack Velocity...");

        // Load configuration
        if (!loadConfiguration()) {
            logger.error("Failed to load configuration - plugin may not function correctly");
            return;
        }

        // Load manifest overrides (if pack-manifest.json exists)
        config.loadManifest();

        // Initialize pack version manager
        packManager = new PackVersionManager(server, logger);
        packManager.loadPacks(config);

        // Verify packs loaded successfully
        if (packManager.getLoadedPackCount() == 0 && !packManager.hasDefaultPack()) {
            logger.error("No resource packs loaded! Check configuration for errors");
            return;
        }

        // Register event listener
        listener = new ResourcePackListener(this, server, logger, config, packManager);
        server.getEventManager().register(this, listener);

        logger.info("ServerResourcePack Velocity initialized successfully!");
        logger.info(
                "Loaded {} protocol-specific packs, required: {}",
                packManager.getLoadedPackCount(),
                config.isPackRequired() ? "yes" : "no");

        // Log supported protocols for debugging
        logSupportedProtocols();
    }

    /** Handles proxy shutdown. */
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("ServerResourcePack Velocity shutting down...");

        // Unregister listener
        if (listener != null) {
            server.getEventManager().unregisterListener(this, listener);
        }

        logger.info("ServerResourcePack Velocity shutdown complete.");
    }

    /**
     * Loads the plugin configuration from disk.
     *
     * @return true if configuration loaded successfully
     */
    private boolean loadConfiguration() {
        try {
            config = new ResourcePackConfig(dataDirectory, logger);
            config.load();
            return true;
        } catch (IOException e) {
            logger.error("Failed to load configuration: {}", e.getMessage());
            return false;
        }
    }

    /** Logs information about supported protocols for debugging. */
    private void logSupportedProtocols() {
        logger.info("Resource Pack Protocol Mappings:");
        logger.info(
                "  Protocol 774 (1.21.11):        {}",
                config.hasPackForProtocol(774) ? "configured" : "using default");
        logger.info(
                "  Protocol 773 (1.21.9-1.21.10): {}",
                config.hasPackForProtocol(773) ? "configured" : "using default");
        logger.info(
                "  Protocol 772 (1.21.7-1.21.8):  {}",
                config.hasPackForProtocol(772) ? "configured" : "using default");
        logger.info(
                "  Protocol 771 (1.21.6):         {}",
                config.hasPackForProtocol(771) ? "configured" : "using default");
        logger.info(
                "  Protocol 770 (1.21.5):         {}",
                config.hasPackForProtocol(770) ? "configured" : "using default");
        logger.info(
                "  Protocol 769 (1.21.4):         {}",
                config.hasPackForProtocol(769) ? "configured" : "using default");
        logger.info(
                "  Protocol 768 (1.21.3):         {}",
                config.hasPackForProtocol(768) ? "configured" : "using default");
        logger.info(
                "  Default fallback:              {}",
                packManager.hasDefaultPack() ? "configured" : "MISSING!");

        if (config.isPackRequired()) {
            logger.warn("Resource pack is REQUIRED - players who decline will be disconnected");
        } else {
            logger.info("Resource pack is OPTIONAL - players can decline without consequences");
        }

        int delayTicks = config.getPackSendDelayTicks();
        logger.info("Pack send delay: {} ticks ({}ms)", delayTicks, delayTicks * 50);
    }

    /**
     * Reloads configuration and manifest, then rebuilds pack mappings.
     *
     * @return true if reload succeeded
     */
    public boolean reload() {
        if (!loadConfiguration()) {
            logger.error("Failed to reload configuration");
            return false;
        }
        config.loadManifest();
        packManager.loadPacks(config);
        logger.info(
                "Reloaded: {} protocol packs, manifest: {}",
                packManager.getLoadedPackCount(),
                config.isManifestLoaded() ? "active" : "not found");
        return true;
    }

    /**
     * Gets the plugin logger for debugging.
     *
     * @return SLF4J logger instance
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * Gets the configuration instance.
     *
     * @return Resource pack configuration
     */
    public ResourcePackConfig getConfig() {
        return config;
    }

    /**
     * Gets the pack version manager.
     *
     * @return Pack version manager instance
     */
    public PackVersionManager getPackManager() {
        return packManager;
    }
}
