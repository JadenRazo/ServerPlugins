package net.serverplugins.items.pack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.serverplugins.items.models.CustomItem;
import org.bukkit.Material;

public class ModelGenerator {

    private final Logger logger;

    public ModelGenerator(Logger logger) {
        this.logger = logger;
    }

    public void generateModelOverrides(ZipOutputStream zip, Collection<CustomItem> items)
            throws IOException {
        // Group items by base material
        Map<Material, List<CustomItem>> byMaterial = new LinkedHashMap<>();
        for (CustomItem item : items) {
            if (item.getCustomModelData() > 0) {
                byMaterial.computeIfAbsent(item.getMaterial(), k -> new ArrayList<>()).add(item);
            }
        }

        for (Map.Entry<Material, List<CustomItem>> entry : byMaterial.entrySet()) {
            String materialKey = entry.getKey().name().toLowerCase();
            String parentModel = getParentModel(entry.getKey());

            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"parent\": \"").append(parentModel).append("\",\n");
            json.append("  \"textures\": {\n");
            json.append("    \"layer0\": \"minecraft:item/").append(materialKey).append("\"\n");
            json.append("  },\n");
            json.append("  \"overrides\": [\n");

            List<CustomItem> materialItems = entry.getValue();
            materialItems.sort(
                    (a, b) -> Integer.compare(a.getCustomModelData(), b.getCustomModelData()));

            for (int i = 0; i < materialItems.size(); i++) {
                CustomItem item = materialItems.get(i);
                json.append("    { \"predicate\": { \"custom_model_data\": ")
                        .append(item.getCustomModelData())
                        .append(" }, \"model\": \"serverplugins:item/")
                        .append(item.getId())
                        .append("\" }");
                if (i < materialItems.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }

            json.append("  ]\n");
            json.append("}");

            String path = "assets/minecraft/models/item/" + materialKey + ".json";
            zip.putNextEntry(new ZipEntry(path));
            zip.write(json.toString().getBytes());
            zip.closeEntry();
        }
    }

    public void generateAutoModels(
            ZipOutputStream zip, Collection<CustomItem> items, File userModelsDir)
            throws IOException {
        for (CustomItem item : items) {
            if (item.getCustomModelData() <= 0) continue;

            // Skip if user already provided a custom model
            File userModel = new File(userModelsDir, item.getId() + ".json");
            if (userModel.exists()) continue;

            // Generate a simple item model pointing to the item's texture
            String json =
                    "{\n"
                            + "  \"parent\": \"minecraft:item/generated\",\n"
                            + "  \"textures\": {\n"
                            + "    \"layer0\": \"serverplugins:item/"
                            + item.getId()
                            + "\"\n"
                            + "  }\n"
                            + "}";

            String path = "assets/serverplugins/models/item/" + item.getId() + ".json";
            zip.putNextEntry(new ZipEntry(path));
            zip.write(json.getBytes());
            zip.closeEntry();
        }
    }

    private String getParentModel(Material material) {
        if (material.name().contains("SWORD")
                || material.name().contains("AXE")
                || material.name().contains("PICKAXE")
                || material.name().contains("SHOVEL")
                || material.name().contains("HOE")
                || material == Material.FISHING_ROD
                || material == Material.TRIDENT) {
            return "minecraft:item/handheld";
        }
        return "minecraft:item/generated";
    }
}
