package net.serverplugins.items.pack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the generated pack manifest JSON that maps protocol versions to pack filenames and
 * hashes. Written by server-items and consumed by server-resourcepack-velocity.
 */
public class PackManifest {

    private String generated;
    private final Map<Integer, PackEntry> packs = new LinkedHashMap<>();
    private PackEntry defaultPack;

    public static class PackEntry {
        private final String file;
        private final String hash;

        public PackEntry(String file, String hash) {
            this.file = file;
            this.hash = hash;
        }

        public String getFile() {
            return file;
        }

        public String getHash() {
            return hash;
        }
    }

    public void setGenerated(String timestamp) {
        this.generated = timestamp;
    }

    public String getGenerated() {
        return generated;
    }

    public void addPack(int protocol, String file, String hash) {
        packs.put(protocol, new PackEntry(file, hash));
    }

    public void setDefault(String file, String hash) {
        this.defaultPack = new PackEntry(file, hash);
    }

    public Map<Integer, PackEntry> getPacks() {
        return packs;
    }

    public PackEntry getDefault() {
        return defaultPack;
    }

    public int getPackCount() {
        return packs.size();
    }

    /** Returns the number of unique zip files (deduplicates shared packs). */
    public long getUniqueFileCount() {
        return packs.values().stream().map(PackEntry::getFile).distinct().count();
    }

    /** Writes the manifest to a JSON file. */
    public void writeToFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"generated\": \"").append(generated).append("\",\n");

        sb.append("  \"packs\": {\n");
        int i = 0;
        for (Map.Entry<Integer, PackEntry> entry : packs.entrySet()) {
            sb.append("    \"").append(entry.getKey()).append("\": { ");
            sb.append("\"file\": \"").append(entry.getValue().file).append("\", ");
            sb.append("\"hash\": \"").append(entry.getValue().hash).append("\" }");
            if (++i < packs.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("  }");

        if (defaultPack != null) {
            sb.append(",\n  \"default\": { ");
            sb.append("\"file\": \"").append(defaultPack.file).append("\", ");
            sb.append("\"hash\": \"").append(defaultPack.hash).append("\" }");
        }

        sb.append("\n}");

        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
    }
}
