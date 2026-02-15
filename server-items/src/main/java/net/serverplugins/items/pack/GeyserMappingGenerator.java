package net.serverplugins.items.pack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.serverplugins.items.models.CustomItem;
import org.bukkit.Material;

public class GeyserMappingGenerator {

    private final Logger logger;

    public GeyserMappingGenerator(Logger logger) {
        this.logger = logger;
    }

    public void generate(Collection<CustomItem> items, File outputFile) throws IOException {
        // Group items by material (Geyser format uses minecraft: prefixed keys)
        Map<Material, List<CustomItem>> byMaterial = new LinkedHashMap<>();
        for (CustomItem item : items) {
            if (item.getCustomModelData() > 0) {
                byMaterial.computeIfAbsent(item.getMaterial(), k -> new ArrayList<>()).add(item);
            }
        }

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"format_version\": 1,\n");
        json.append("  \"items\": {\n");

        int materialIndex = 0;
        for (Map.Entry<Material, List<CustomItem>> entry : byMaterial.entrySet()) {
            String materialKey = "minecraft:" + entry.getKey().name().toLowerCase();

            json.append("    \"").append(materialKey).append("\": [\n");

            List<CustomItem> materialItems = entry.getValue();
            for (int i = 0; i < materialItems.size(); i++) {
                CustomItem item = materialItems.get(i);
                json.append("      { \"custom_model_data\": ")
                        .append(item.getCustomModelData())
                        .append(", \"name\": \"")
                        .append(item.getId())
                        .append("\", \"icon\": \"serverplugins.")
                        .append(item.getId().replace('_', '.'))
                        .append("\" }");
                if (i < materialItems.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }

            json.append("    ]");
            materialIndex++;
            if (materialIndex < byMaterial.size()) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  }\n");
        json.append("}");

        outputFile.getParentFile().mkdirs();
        Files.writeString(outputFile.toPath(), json.toString());
        logger.info("Geyser mappings generated: " + outputFile.getName());
    }
}
