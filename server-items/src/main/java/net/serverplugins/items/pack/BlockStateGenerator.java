package net.serverplugins.items.pack;

import java.io.IOException;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.serverplugins.items.models.BlockStateMapping;
import net.serverplugins.items.models.CustomBlock;

public class BlockStateGenerator {

    public void generateBlockStates(ZipOutputStream zip, Collection<CustomBlock> blocks)
            throws IOException {
        if (blocks.isEmpty()) return;

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"variants\": {\n");

        int i = 0;
        for (CustomBlock block : blocks) {
            BlockStateMapping mapping = block.getStateMapping();
            if (mapping == null) continue;

            String variant =
                    "instrument="
                            + mapping.instrument().name().toLowerCase()
                            + ",note="
                            + mapping.note()
                            + ",powered="
                            + mapping.powered();

            json.append("    \"").append(variant).append("\": { \"model\": \"serverplugins:block/");
            json.append(block.getId()).append("\" }");

            i++;
            if (i < blocks.size()) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  }\n");
        json.append("}");

        String path = "assets/minecraft/blockstates/note_block.json";
        zip.putNextEntry(new ZipEntry(path));
        zip.write(json.toString().getBytes());
        zip.closeEntry();
    }
}
