package net.serverplugins.velocity.resourcepack.managers;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.serverplugins.velocity.resourcepack.ResourcePackConfig;
import org.slf4j.Logger;

/**
 * Manages protocol-to-resource-pack mappings.
 *
 * <p>Converts configuration values into Velocity ResourcePackInfo objects and provides efficient
 * lookups by protocol version.
 */
public class PackVersionManager {

    private final ProxyServer server;
    private final Logger logger;
    private final Map<Integer, ResourcePackInfo> packMap;
    private ResourcePackInfo defaultPack;

    // Supported Minecraft protocol versions
    private static final int[] SUPPORTED_PROTOCOLS = {
        774, // 1.21.11
        773, // 1.21.9-1.21.10
        772, // 1.21.7-1.21.8
        771, // 1.21.6
        770, // 1.21.5
        769, // 1.21.4
        768 // 1.21.3
    };

    public PackVersionManager(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.packMap = new HashMap<>();
    }

    /**
     * Loads all resource pack configurations from the config file.
     *
     * @param config Configuration containing protocol mappings
     */
    public void loadPacks(ResourcePackConfig config) {
        packMap.clear();

        // Load packs for all supported protocols
        int loadedCount = 0;
        for (int protocol : SUPPORTED_PROTOCOLS) {
            if (config.hasPackForProtocol(protocol)) {
                try {
                    ResourcePackInfo packInfo =
                            buildPackInfo(
                                    config.getPackUrl(protocol),
                                    config.getPackHash(protocol),
                                    config.isPackRequired(),
                                    config.getPackPrompt());
                    packMap.put(protocol, packInfo);
                    loadedCount++;
                    logger.debug("Loaded resource pack for protocol {}", protocol);
                } catch (Exception e) {
                    logger.error(
                            "Failed to load pack for protocol {}: {}", protocol, e.getMessage());
                }
            }
        }

        // Load default fallback pack
        try {
            defaultPack =
                    buildPackInfo(
                            config.getDefaultPackUrl(),
                            config.getDefaultPackHash(),
                            config.isPackRequired(),
                            config.getPackPrompt());
            logger.info("Loaded {} protocol-specific packs + 1 default fallback", loadedCount);
        } catch (Exception e) {
            logger.error("Failed to load default pack: {}", e.getMessage());
        }
    }

    /**
     * Gets the appropriate resource pack for a given protocol version.
     *
     * @param protocol Minecraft protocol version number
     * @return ResourcePackInfo if available, empty if default pack also failed to load
     */
    public Optional<ResourcePackInfo> getPackForProtocol(int protocol) {
        ResourcePackInfo pack = packMap.get(protocol);
        if (pack != null) {
            logger.debug("Found specific pack for protocol {}", protocol);
            return Optional.of(pack);
        }

        if (defaultPack != null) {
            logger.debug("Using default pack for unknown protocol {}", protocol);
            return Optional.of(defaultPack);
        }

        logger.warn("No resource pack available for protocol {}", protocol);
        return Optional.empty();
    }

    /**
     * Builds a ResourcePackInfo object from configuration values.
     *
     * @param url URL of the resource pack
     * @param hashHex SHA-1 hash in hexadecimal format (40 characters)
     * @param required Whether the pack is required
     * @param prompt Prompt message shown to player
     * @return Constructed ResourcePackInfo
     * @throws IllegalArgumentException if hash is invalid
     */
    private ResourcePackInfo buildPackInfo(
            String url, String hashHex, boolean required, String prompt)
            throws IllegalArgumentException {

        // Convert hex hash to byte array
        byte[] hashBytes = hexToBytes(hashHex);

        // Convert prompt (replace \\n with actual newlines)
        Component promptComponent = Component.text(prompt.replace("\\n", "\n"));

        // Generate unique ID for this pack based on URL
        UUID packId = UUID.nameUUIDFromBytes(url.getBytes());

        // Build ResourcePackInfo using Velocity's ProxyServer.createResourcePackBuilder
        return server.createResourcePackBuilder(url)
                .setId(packId)
                .setHash(hashBytes)
                .setShouldForce(required)
                .setPrompt(promptComponent)
                .build();
    }

    /**
     * Converts a SHA-1 hex string to a byte array.
     *
     * @param hexString SHA-1 hash in hexadecimal format (40 characters)
     * @return Byte array representation (20 bytes)
     * @throws IllegalArgumentException if hex string is invalid
     */
    private byte[] hexToBytes(String hexString) {
        if (hexString == null || hexString.isEmpty()) {
            throw new IllegalArgumentException("Hash cannot be null or empty");
        }

        // Remove any whitespace or common prefixes
        hexString = hexString.trim().toLowerCase();
        if (hexString.startsWith("0x")) {
            hexString = hexString.substring(2);
        }

        // SHA-1 hash should be exactly 40 hex characters (20 bytes)
        if (hexString.length() != 40) {
            throw new IllegalArgumentException(
                    "Invalid SHA-1 hash length: "
                            + hexString.length()
                            + " (expected 40 hex characters)");
        }

        // Convert hex string to bytes
        byte[] bytes = new byte[20];
        try {
            for (int i = 0; i < 20; i++) {
                int index = i * 2;
                int value = Integer.parseInt(hexString.substring(index, index + 2), 16);
                bytes[i] = (byte) value;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex characters in hash: " + hexString, e);
        }

        return bytes;
    }

    /**
     * Gets the number of loaded protocol-specific packs.
     *
     * @return Count of loaded packs (excluding default)
     */
    public int getLoadedPackCount() {
        return packMap.size();
    }

    /**
     * Checks if a default fallback pack is loaded.
     *
     * @return true if default pack is available
     */
    public boolean hasDefaultPack() {
        return defaultPack != null;
    }
}
