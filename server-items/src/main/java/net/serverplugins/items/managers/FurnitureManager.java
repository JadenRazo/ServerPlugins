package net.serverplugins.items.managers;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.items.ServerItems;
import net.serverplugins.items.models.CustomFurniture;
import net.serverplugins.items.models.CustomItem;
import net.serverplugins.items.models.FurnitureInstance;
import net.serverplugins.items.repository.ItemsRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class FurnitureManager {

    private final ServerItems plugin;
    private final ItemsRepository repository;

    private final Map<String, CustomFurniture> definitions = new HashMap<>();
    private final ConcurrentHashMap<UUID, FurnitureInstance> activeInstances =
            new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;

    public FurnitureManager(ServerItems plugin, ItemsRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void loadFurnitureDefs(File itemsFolder, ItemManager itemManager) {
        definitions.clear();
        loadFromDir(itemsFolder, itemManager);
        plugin.getLogger().info("Loaded " + definitions.size() + " furniture definitions.");
    }

    private void loadFromDir(File dir, ItemManager itemManager) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                loadFromDir(file, itemManager);
            } else if (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml")) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                for (String id : yaml.getKeys(false)) {
                    ConfigurationSection section = yaml.getConfigurationSection(id);
                    if (section == null) continue;
                    ConfigurationSection furnitureSection =
                            section.getConfigurationSection("furniture");
                    if (furnitureSection == null) continue;

                    CustomItem item = itemManager.getItem(id);
                    if (item == null) continue;

                    try {
                        CustomFurniture furniture = parseFurniture(item, furnitureSection);
                        definitions.put(id, furniture);
                    } catch (Exception e) {
                        plugin.getLogger()
                                .warning(
                                        "Failed to parse furniture '"
                                                + id
                                                + "': "
                                                + e.getMessage());
                    }
                }
            }
        }
    }

    private CustomFurniture parseFurniture(CustomItem item, ConfigurationSection section) {
        ConfigurationSection hitbox = section.getConfigurationSection("hitbox");
        float width = hitbox != null ? (float) hitbox.getDouble("width", 1.0) : 1.0f;
        float height = hitbox != null ? (float) hitbox.getDouble("height", 1.0) : 1.0f;

        ConfigurationSection transform = section.getConfigurationSection("transform");
        float[] scale = {1.0f, 1.0f, 1.0f};
        float[] translation = {0.0f, 0.0f, 0.0f};
        if (transform != null) {
            List<Double> scaleList = transform.getDoubleList("scale");
            if (scaleList.size() == 3) {
                scale[0] = scaleList.get(0).floatValue();
                scale[1] = scaleList.get(1).floatValue();
                scale[2] = scaleList.get(2).floatValue();
            }
            List<Double> transList = transform.getDoubleList("translation");
            if (transList.size() == 3) {
                translation[0] = transList.get(0).floatValue();
                translation[1] = transList.get(1).floatValue();
                translation[2] = transList.get(2).floatValue();
            }
        }

        boolean barrier = section.getBoolean("barrier", false);
        boolean sittable = section.getBoolean("sittable", false);
        float sitHeight = (float) section.getDouble("sit_height", 0.5);

        String rotStr = section.getString("rotation", "PLAYER_YAW");
        CustomFurniture.RotationType rotation;
        try {
            rotation = CustomFurniture.RotationType.valueOf(rotStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            rotation = CustomFurniture.RotationType.PLAYER_YAW;
        }

        return new CustomFurniture(
                item, width, height, scale, translation, barrier, sittable, sitHeight, rotation);
    }

    public FurnitureInstance placeFurniture(
            CustomFurniture furniture, Location location, float yaw, UUID placedBy) {
        World world = location.getWorld();

        // Spawn ItemDisplay entity
        ItemDisplay display =
                world.spawn(
                        location,
                        ItemDisplay.class,
                        entity -> {
                            ItemStack displayItem =
                                    plugin.getItemManager().buildItemStack(furniture.getItem(), 1);
                            entity.setItemStack(displayItem);

                            float[] s = furniture.getScale();
                            float[] t = furniture.getTranslation();
                            entity.setTransformation(
                                    new Transformation(
                                            new Vector3f(t[0], t[1], t[2]),
                                            new Quaternionf(),
                                            new Vector3f(s[0], s[1], s[2]),
                                            new Quaternionf()));

                            entity.setRotation(yaw, 0);
                            entity.setPersistent(true);
                        });

        // Spawn Interaction entity for hitbox
        Interaction interaction =
                world.spawn(
                        location,
                        Interaction.class,
                        entity -> {
                            entity.setInteractionWidth(furniture.getHitboxWidth());
                            entity.setInteractionHeight(furniture.getHitboxHeight());
                            entity.setPersistent(true);
                        });

        // Optional barrier block for collision
        if (furniture.isUseBarrier()) {
            location.getBlock().setType(Material.BARRIER, false);
        }

        FurnitureInstance instance =
                new FurnitureInstance(
                        display.getUniqueId(),
                        interaction.getUniqueId(),
                        furniture.getId(),
                        location,
                        yaw,
                        placedBy);

        activeInstances.put(display.getUniqueId(), instance);

        // Persist to DB
        repository.insertFurniture(
                display.getUniqueId().toString(),
                interaction.getUniqueId().toString(),
                furniture.getId(),
                world.getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                yaw,
                placedBy != null ? placedBy.toString() : null);

        return instance;
    }

    public boolean removeFurniture(UUID displayUuid) {
        FurnitureInstance instance = activeInstances.remove(displayUuid);
        if (instance == null) return false;

        // Remove entities
        for (World world : Bukkit.getWorlds()) {
            Entity display = world.getEntity(displayUuid);
            if (display != null) {
                display.remove();
                break;
            }
        }

        if (instance.getInteractionEntityUuid() != null) {
            for (World world : Bukkit.getWorlds()) {
                Entity interaction = world.getEntity(instance.getInteractionEntityUuid());
                if (interaction != null) {
                    interaction.remove();
                    break;
                }
            }
        }

        // Remove barrier if present
        Location loc = instance.getLocation();
        if (loc.getBlock().getType() == Material.BARRIER) {
            loc.getBlock().setType(Material.AIR, false);
        }

        repository.deleteFurniture(displayUuid.toString());
        return true;
    }

    public FurnitureInstance getFurnitureByDisplay(UUID displayUuid) {
        return activeInstances.get(displayUuid);
    }

    public FurnitureInstance getFurnitureByInteraction(UUID interactionUuid) {
        for (FurnitureInstance instance : activeInstances.values()) {
            if (interactionUuid.equals(instance.getInteractionEntityUuid())) {
                return instance;
            }
        }
        return null;
    }

    public CustomFurniture getDefinition(String id) {
        return definitions.get(id);
    }

    public Collection<CustomFurniture> getAllDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public int getDefinitionCount() {
        return definitions.size();
    }

    public void loadChunkFurniture(String world, int chunkX, int chunkZ) {
        List<ItemsRepository.FurnitureRow> rows =
                repository.loadFurnitureInChunk(world, chunkX, chunkZ);
        for (ItemsRepository.FurnitureRow row : rows) {
            try {
                UUID displayUuid = UUID.fromString(row.displayUuid());
                UUID interactionUuid =
                        row.interactionUuid() != null
                                ? UUID.fromString(row.interactionUuid())
                                : null;
                UUID placedBy = row.placedBy() != null ? UUID.fromString(row.placedBy()) : null;

                World bukkitWorld = Bukkit.getWorld(row.world());
                if (bukkitWorld == null) continue;

                Location loc = new Location(bukkitWorld, row.x(), row.y(), row.z());
                FurnitureInstance instance =
                        new FurnitureInstance(
                                displayUuid,
                                interactionUuid,
                                row.furnitureId(),
                                loc,
                                row.yaw(),
                                placedBy);
                activeInstances.put(displayUuid, instance);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load furniture from DB: " + e.getMessage());
            }
        }
    }

    public void unloadChunkFurniture(String world, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int maxX = minX + 16;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 16;

        activeInstances
                .entrySet()
                .removeIf(
                        entry -> {
                            Location loc = entry.getValue().getLocation();
                            return loc.getWorld().getName().equals(world)
                                    && loc.getBlockX() >= minX
                                    && loc.getBlockX() < maxX
                                    && loc.getBlockZ() >= minZ
                                    && loc.getBlockZ() < maxZ;
                        });
    }

    public void startCleanupTask() {
        int intervalTicks = plugin.getItemsConfig().getFurnitureCleanupInterval() * 20;
        cleanupTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(plugin, this::cleanupOrphans, intervalTicks, intervalTicks);
    }

    private void cleanupOrphans() {
        int removed = 0;
        for (Map.Entry<UUID, FurnitureInstance> entry : Map.copyOf(activeInstances).entrySet()) {
            FurnitureInstance instance = entry.getValue();
            World world = instance.getLocation().getWorld();
            if (world == null) continue;

            Entity display = world.getEntity(instance.getDisplayEntityUuid());
            if (display == null || display.isDead()) {
                activeInstances.remove(entry.getKey());
                repository.deleteFurniture(entry.getKey().toString());
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Cleaned up " + removed + " orphaned furniture entries.");
        }
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
    }
}
