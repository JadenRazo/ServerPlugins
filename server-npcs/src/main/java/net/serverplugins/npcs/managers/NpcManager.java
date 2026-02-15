package net.serverplugins.npcs.managers;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.serverplugins.npcs.ServerNpcs;
import net.serverplugins.npcs.models.Npc;
import net.serverplugins.npcs.models.NpcData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public class NpcManager {

    private final ServerNpcs plugin;
    private final Map<String, Npc> npcs;
    private final Map<UUID, NpcData> npcDataMap;
    private final File npcsFile;

    public NpcManager(ServerNpcs plugin) {
        this.plugin = plugin;
        this.npcs = new HashMap<>();
        this.npcDataMap = new HashMap<>();
        this.npcsFile = new File(plugin.getDataFolder(), "npcs.yml");

        loadNpcs();
    }

    private void loadNpcs() {
        npcs.clear();

        if (!npcsFile.exists()) {
            plugin.getLogger()
                    .info("No npcs.yml file found. NPCs will be managed through FancyNpcs.");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(npcsFile);
        ConfigurationSection npcsSection = config.getConfigurationSection("npcs");

        if (npcsSection == null) {
            return;
        }

        int loaded = 0;
        for (String key : npcsSection.getKeys(false)) {
            ConfigurationSection npcSection = npcsSection.getConfigurationSection(key);
            if (npcSection != null) {
                try {
                    Npc npc = loadNpcFromSection(key, npcSection);
                    if (npc != null) {
                        npcs.put(npc.getId(), npc);
                        loaded++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load NPC " + key + ": " + e.getMessage());
                    if (plugin.getNpcsConfig().isDebugEnabled()) {
                        e.printStackTrace();
                    }
                }
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " NPC(s)");
    }

    private Npc loadNpcFromSection(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        String displayName = section.getString("display-name", name);
        String dialogId = section.getString("dialog-id", id);

        return new Npc.Builder()
                .id(id)
                .name(name)
                .displayName(displayName)
                .dialogId(dialogId)
                .location(null) // Location can be loaded if needed
                .build();
    }

    public void registerNpc(String id, String dialogId) {
        Npc npc =
                new Npc.Builder()
                        .id(id)
                        .name(id)
                        .displayName(id)
                        .dialogId(dialogId)
                        .location(null)
                        .build();

        npcs.put(id, npc);
    }

    public Npc getNpc(String id) {
        return npcs.get(id);
    }

    public NpcData getNpcData(UUID uuid) {
        return npcDataMap.computeIfAbsent(uuid, NpcData::new);
    }

    public int getNpcCount() {
        return npcs.size();
    }

    public void saveAll() {
        // Save NPC data if needed
        // This could save to database or file
    }

    public void reload() {
        loadNpcs();
    }

    public Map<String, Npc> getAllNpcs() {
        return new HashMap<>(npcs);
    }
}
