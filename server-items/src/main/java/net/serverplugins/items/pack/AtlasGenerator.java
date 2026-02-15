package net.serverplugins.items.pack;

import java.io.IOException;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.serverplugins.items.models.CustomItem;

public class AtlasGenerator {

    public void generateAtlas(ZipOutputStream zip, Collection<CustomItem> items)
            throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"sources\": [\n");
        json.append("    {\n");
        json.append("      \"type\": \"directory\",\n");
        json.append("      \"source\": \"item\",\n");
        json.append("      \"prefix\": \"item/\"\n");
        json.append("    }\n");
        json.append("  ]\n");
        json.append("}");

        String path = "assets/serverplugins/atlases/blocks.json";
        zip.putNextEntry(new ZipEntry(path));
        zip.write(json.toString().getBytes());
        zip.closeEntry();
    }
}
