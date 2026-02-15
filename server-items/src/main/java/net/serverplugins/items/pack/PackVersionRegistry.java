package net.serverplugins.items.pack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps Minecraft protocol versions to resource pack formats. Each entry holds the pack_format int,
 * a label used in filenames, and which protocol versions use that format.
 */
public class PackVersionRegistry {

    /** A single pack version entry. */
    public record PackVersion(int packFormat, String label, int[] protocols) {}

    private static final PackVersion[] VERSIONS = {
        new PackVersion(63, "1.21.11", new int[] {774}),
        new PackVersion(61, "1.21.9", new int[] {773}),
        new PackVersion(57, "1.21.7", new int[] {772}),
        new PackVersion(55, "1.21.6", new int[] {771}),
        new PackVersion(46, "1.21.4", new int[] {769, 770}),
        new PackVersion(34, "1.21.3", new int[] {768}),
    };

    /** Returns all registered pack versions ordered from newest to oldest. */
    public static PackVersion[] getVersions() {
        return VERSIONS;
    }

    /** Returns the latest (highest pack_format) version. */
    public static PackVersion getLatest() {
        return VERSIONS[0];
    }

    /**
     * Builds a protocol-to-label map for all protocols across all versions. Used by the manifest
     * generator.
     */
    public static Map<Integer, String> getProtocolToLabelMap() {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (PackVersion version : VERSIONS) {
            for (int proto : version.protocols()) {
                map.put(proto, version.label());
            }
        }
        return map;
    }
}
